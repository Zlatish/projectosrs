package org.rsmod.content.other.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.writeText
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.rsmod.api.parsers.json.Json
import org.rsmod.api.parsers.toml.Toml

/**
 * Creates GitHub issues through the REST API. Requests run on OkHttp's own threads, so [onResult]
 * is invoked off the game thread and must not touch game state directly.
 *
 * The token and repository are read from [CONFIG_FILE], which lives in the gitignored `.data`
 * folder so the token can never be committed.
 *
 * In plain terms: this class is the part of `::bug` that talks to GitHub over the internet. It
 * reads the settings file, builds the web request, sends it in the background so the game never
 * waits on the internet, and turns GitHub's reply into a simple "created issue #N" or "failed
 * because ...".
 * - `@Singleton` means the server only ever creates one of these and shares it.
 * - `@Inject constructor(...)` means the server builds it and passes in two `ObjectMapper`s from
 *   the Jackson library: `json` reads and writes JSON (the format GitHub's API uses), and `toml`
 *   reads TOML (the format of the settings file). `@Json` / `@Toml` say which one is wanted.
 */
@Singleton
class GitHubIssueClient
@Inject
constructor(@Json private val json: ObjectMapper, @Toml private val toml: ObjectMapper) {
    // Writes messages to the server console and log files.
    private val logger = InlineLogger()

    // The web client used to talk to GitHub, from the OkHttp library. It's set up with:
    // - its own pool of background threads named "github-issues", created as needed;
    // - a 15-second limit, after which a request counts as failed.
    //
    // Daemon threads so a recent request never keeps the JVM alive after server shutdown.
    // `.apply { ... }` runs the block on the new thread to change a setting, then returns that
    // thread.
    private val http =
        OkHttpClient.Builder()
            .dispatcher(
                Dispatcher(
                    Executors.newCachedThreadPool { runnable ->
                        Thread(runnable, "github-issues").apply { isDaemon = true }
                    }
                )
            )
            .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    /**
     * Loads the config, creating a template file on first use. Returns `null` if the token or
     * repository has not been filled in yet.
     *
     * The `?` in `GitHubConfig?` means this function may return `null` instead of a config.
     */
    fun loadConfig(): GitHubConfig? {
        // First time only: if .data/github.toml doesn't exist, create it from the template below
        // so the owner just has to paste in a token.
        if (!CONFIG_FILE.exists()) {
            CONFIG_FILE.writeText(CONFIG_TEMPLATE)
            logger.info { "Created GitHub issue config template: ${CONFIG_FILE.toAbsolutePath()}" }
        }
        // Read the file into a `GitHubConfig`. The file is read every time ::bug is used, so
        // changes to it work without restarting the server.
        val config = toml.readValue(CONFIG_FILE.toFile(), GitHubConfig::class.java)
        // `takeIf { ... }` returns the config if the check passes, or `null` if either value is
        // still blank.
        return config.takeIf { it.token.isNotBlank() && it.repo.isNotBlank() }
    }

    /**
     * Sends a request to GitHub to create an issue, and returns straight away without waiting for
     * the reply. When GitHub replies (or the request fails), [onResult] is called on a background
     * thread with the outcome.
     *
     * `onResult: (IssueResult) -> Unit` is a function passed in as a parameter: it takes an
     * `IssueResult` and returns nothing (`Unit` is Kotlin's "nothing").
     */
    fun createIssue(
        config: GitHubConfig,
        title: String,
        body: String,
        labels: List<String>,
        onResult: (IssueResult) -> Unit,
    ) {
        // Build the issue data as JSON, e.g. {"title": "...", "body": "...", "labels": [...]}.
        // `mapOf("a" to b)` creates a map where "a" points to b.
        val payload =
            json.writeValueAsString(mapOf("title" to title, "body" to body, "labels" to labels))
        // Build the web request to GitHub's "create an issue" endpoint for our repo:
        // - `Authorization` carries the token, which proves we're allowed to create issues.
        // - `Accept` and `X-GitHub-Api-Version` tell GitHub which API format and version we
        //   expect back, as GitHub's documentation recommends.
        // - `.post(...)` makes it a POST request (sending data) with the JSON above as its body.
        val request =
            Request.Builder()
                .url("https://api.github.com/repos/${config.repo}/issues")
                .header("Authorization", "Bearer ${config.token}")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

        // Send the request in the background. `enqueue` doesn't wait; OkHttp calls one of the two
        // functions below when it's done.
        // `object : Callback { ... }` creates a one-off object that fills in OkHttp's `Callback`
        // interface, i.e. it supplies the two functions OkHttp expects.
        http
            .newCall(request)
            .enqueue(
                object : Callback {
                    // GitHub couldn't be reached at all, e.g. no internet connection or the
                    // 15-second limit was hit.
                    override fun onFailure(call: Call, e: IOException) {
                        logger.warn(e) { "Failed to reach GitHub while creating an issue." }
                        onResult(IssueResult.Failure("couldn't reach GitHub"))
                    }

                    // GitHub replied. That might still be an error (e.g. a bad token), so the
                    // reply is checked in `parseResponse`. `use { ... }` closes the response
                    // afterwards so the connection is released.
                    override fun onResponse(call: Call, response: Response) {
                        response.use { onResult(parseResponse(it)) }
                    }
                }
            )
    }

    // Turns GitHub's reply into a simple success (with the new issue number) or a failure (with
    // a reason the player can understand).
    private fun parseResponse(response: Response): IssueResult {
        val text = response.body.string()
        // Anything other than a success status code means GitHub refused the request.
        if (!response.isSuccessful) {
            // GitHub error bodies never echo the token, so they are safe to log.
            logger.warn { "GitHub rejected issue creation (status=${response.code}): $text" }
            // Translate common error codes into plain reasons. `when` picks the first matching
            // branch, like a switch statement. `403, 404 ->` matches either code, and `else`
            // catches everything else.
            val reason =
                when (response.code) {
                    401 -> "the token is invalid or expired"
                    403,
                    404 -> "the token can't create issues in this repo"
                    else -> "GitHub returned error ${response.code}"
                }
            return IssueResult.Failure(reason)
        }
        // Success: read the new issue's number out of GitHub's JSON reply.
        val number = json.readTree(text).path("number").asInt()
        return IssueResult.Created(number)
    }

    // The two settings read from .data/github.toml. The `= ""` defaults mean a missing value is
    // read as blank instead of crashing. A `data class` is a class that just holds values.
    data class GitHubConfig(val token: String = "", val repo: String = "")

    // The outcome of trying to create an issue: either `Created` or `Failure`, nothing else.
    // A `sealed class` lists every possible kind up front, so a `when` that checks the result
    // (as in BugReportCommands) can be sure it has covered all of them.
    sealed class IssueResult {
        data class Created(val number: Int) : IssueResult()

        data class Failure(val reason: String) : IssueResult()
    }

    // Fixed values for this class. A `companion object` holds values that belong to the class
    // itself rather than to one instance, much like "static" in Java.
    private companion object {
        // Where the settings live: .data/github.toml, relative to the project folder.
        private val CONFIG_FILE: Path = Paths.get(".data", "github.toml")
        // Tells GitHub the request body is JSON.
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        // How long to wait for GitHub before giving up.
        private const val REQUEST_TIMEOUT_SECONDS = 15L

        // What's written to .data/github.toml the first time ::bug is used. The `#` lines are
        // comments in the file explaining what to fill in.
        private val CONFIG_TEMPLATE =
            """
            # Settings for the in-game ::bug command. This file is gitignored; never commit it.
            # token: a fine-grained personal access token limited to the repo below,
            #        with only the "Issues: Read and write" repository permission.
            token = ''
            repo = 'Zlatish/projectosrs'
            """
                .trimIndent() + "\n"
    }
}
