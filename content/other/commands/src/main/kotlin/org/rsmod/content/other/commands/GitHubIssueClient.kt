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
 */
@Singleton
class GitHubIssueClient
@Inject
constructor(@Json private val json: ObjectMapper, @Toml private val toml: ObjectMapper) {
    private val logger = InlineLogger()

    // Daemon threads so a recent request never keeps the JVM alive after server shutdown.
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
     */
    fun loadConfig(): GitHubConfig? {
        if (!CONFIG_FILE.exists()) {
            CONFIG_FILE.writeText(CONFIG_TEMPLATE)
            logger.info { "Created GitHub issue config template: ${CONFIG_FILE.toAbsolutePath()}" }
        }
        val config = toml.readValue(CONFIG_FILE.toFile(), GitHubConfig::class.java)
        return config.takeIf { it.token.isNotBlank() && it.repo.isNotBlank() }
    }

    fun createIssue(
        config: GitHubConfig,
        title: String,
        body: String,
        labels: List<String>,
        onResult: (IssueResult) -> Unit,
    ) {
        val payload =
            json.writeValueAsString(mapOf("title" to title, "body" to body, "labels" to labels))
        val request =
            Request.Builder()
                .url("https://api.github.com/repos/${config.repo}/issues")
                .header("Authorization", "Bearer ${config.token}")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

        http
            .newCall(request)
            .enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        logger.warn(e) { "Failed to reach GitHub while creating an issue." }
                        onResult(IssueResult.Failure("couldn't reach GitHub"))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use { onResult(parseResponse(it)) }
                    }
                }
            )
    }

    private fun parseResponse(response: Response): IssueResult {
        val text = response.body.string()
        if (!response.isSuccessful) {
            // GitHub error bodies never echo the token, so they are safe to log.
            logger.warn { "GitHub rejected issue creation (status=${response.code}): $text" }
            val reason =
                when (response.code) {
                    401 -> "the token is invalid or expired"
                    403,
                    404 -> "the token can't create issues in this repo"
                    else -> "GitHub returned error ${response.code}"
                }
            return IssueResult.Failure(reason)
        }
        val number = json.readTree(text).path("number").asInt()
        return IssueResult.Created(number)
    }

    data class GitHubConfig(val token: String = "", val repo: String = "")

    sealed class IssueResult {
        data class Created(val number: Int) : IssueResult()

        data class Failure(val reason: String) : IssueResult()
    }

    private companion object {
        private val CONFIG_FILE: Path = Paths.get(".data", "github.toml")
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val REQUEST_TIMEOUT_SECONDS = 15L

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
