package org.rsmod.content.other.commands

import jakarta.inject.Inject
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentLinkedQueue
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onEvent
import org.rsmod.content.other.commands.GitHubIssueClient.IssueResult
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The `::bug description` command, which files a GitHub issue from in-game with the reporter's
 * name, location and the time filled in automatically. Moderators and above can use it.
 *
 * The actual sending to GitHub is done by [GitHubIssueClient]. This class handles the command
 * itself: checking the input, stopping spam, building the issue text, and telling the player
 * whether it worked.
 *
 * A `PluginScript` is RS Mod's building block for game content: the server calls `startup()` on
 * every one when it starts. `@Inject constructor(...)` means the server builds this class and
 * passes in `playerList` (everyone online) and `github` (the client that talks to GitHub).
 */
class BugReportCommands
@Inject
constructor(private val playerList: PlayerList, private val github: GitHubIssueClient) :
    PluginScript() {
    // Results arrive on GitHub client threads and are handed to the game thread through this
    // queue, which is drained at the start of every game cycle.
    //
    // Why: the game runs on one main thread, and game state (like messaging a player) must only
    // be changed from that thread. GitHub replies arrive on other threads, so they're put in this
    // queue and picked up safely by the game thread in `deliverResults()`. A
    // `ConcurrentLinkedQueue` is a list that several threads can safely add to and read from at
    // the same time.
    private val completed = ConcurrentLinkedQueue<CompletedReport>()

    // Only touched on the game thread.
    // `pending`: players whose report is still being sent, so they can't send two at once.
    // `lastReportMillis`: when each player last sent a report, for the cooldown below.
    // `mutableSetOf` / `mutableMapOf` create a set and a map that can be changed after creation.
    private val pending = mutableSetOf<PlayerUid>()
    private val lastReportMillis = mutableMapOf<PlayerUid, Long>()

    // Called once when the server starts.
    // - Registers ::bug, limited to moderator rank and above. `::reportBug` passes the function
    //   below as the code to run.
    // - `onEvent<GameLifecycle.StartCycle>` runs `deliverResults()` at the start of every game
    //   tick (every 0.6 seconds), so finished reports are passed on to players quickly.
    override fun ScriptContext.startup() {
        onCommand("bug", "Report a bug to the GitHub issue tracker", ::reportBug) {
            modLevel = modlevels.moderator
        }
        onEvent<GameLifecycle.StartCycle> { deliverResults() }
    }

    // Runs when someone types ::bug. `with(cheat) { ... }` lets us write `player` and `args`
    // instead of `cheat.player` and `cheat.args` inside the block.
    private fun reportBug(cheat: Cheat) =
        with(cheat) {
            // Join the typed words back into one sentence. If nothing was typed after ::bug,
            // explain how to use it and stop. (The server lowercases everything typed after
            // "::", so the description arrives in lowercase.)
            val description = args.joinToString(" ").trim()
            if (description.isEmpty()) {
                player.mes("Use as ::bug description of the problem")
                return
            }

            // Spam protection, part 1: only one report at a time per player.
            // `uid` is the player's unique ID. `in` checks whether it's in the `pending` set.
            val uid = player.uid
            if (uid in pending) {
                player.mes("Your last bug report is still being sent.")
                return
            }
            // Spam protection, part 2: a short cooldown (10 seconds) between reports.
            // `System.currentTimeMillis()` is the current time in milliseconds.
            // `lastReportMillis[uid]` is `null` if this player hasn't reported yet.
            val now = System.currentTimeMillis()
            val last = lastReportMillis[uid]
            if (last != null && now - last < REPORT_COOLDOWN_MILLIS) {
                player.mes("You just sent a bug report. Wait a few seconds before sending another.")
                return
            }

            // Read the GitHub token and repo from .data/github.toml. `null` means the file hasn't
            // been filled in yet, so bug reporting isn't set up on this server.
            val config = github.loadConfig()
            if (config == null) {
                player.mes("Bug reporting isn't set up. Add a GitHub token to .data/github.toml.")
                return
            }

            // Build the issue. The title is the description, cut to 70 characters by `take(...)`.
            // The body is GitHub Markdown, filled in with the description, the reporter's name,
            // their exact location (plus a ::tele command to go there), and the current time in
            // UTC (`Instant.now()`, with fractions of a second removed by `truncatedTo`).
            // `${...}` inside the string inserts the result of the code in the braces.
            val coords = player.coords
            val title = "[Bug] " + description.take(TITLE_MAX_LENGTH)
            val body =
                """
                ### What happens
                $description

                ### Reported in-game
                - **Reporter:** ${player.username}
                - **Location:** x=${coords.x}, z=${coords.z}, level=${coords.level} (`::tele ${coords.level},${coords.mx},${coords.mz},${coords.lx},${coords.lz}`)
                - **Time:** ${Instant.now().truncatedTo(ChronoUnit.SECONDS)}

                _Sent with the in-game `::bug` command. Add steps to reproduce, severity and area when triaging._
                """
                    .trimIndent()

            // Mark the player as waiting and start their cooldown, then send the issue.
            // `+=` adds to the set, and `lastReportMillis[uid] = now` stores the time in the map.
            pending += uid
            lastReportMillis[uid] = now
            player.mes("Sending bug report...")
            // `createIssue` returns immediately and sends the request in the background. The
            // `{ result -> ... }` block (a lambda) runs later on a GitHub thread when the reply
            // arrives. It only adds the result to the `completed` queue, because it's not on the
            // game thread and so mustn't message the player directly.
            github.createIssue(config, title, body, ISSUE_LABELS) { result ->
                completed += CompletedReport(uid, result)
            }
        }

    // Runs on the game thread at the start of every tick. Takes each finished report off the
    // queue and tells the reporter whether it worked.
    private fun deliverResults() {
        while (true) {
            // `poll()` takes the next report off the queue, or gives `null` if it's empty.
            // `?: break` means "if that's null, leave the loop", so this stops once the queue is
            // empty.
            val report = completed.poll() ?: break
            // The report is finished, so the player may send another (once the cooldown passes).
            pending -= report.uid
            // The reporter may have logged out while the request was in flight.
            // `?: continue` skips to the next report if they're no longer online.
            val player = report.uid.resolve(playerList) ?: continue
            // `when` checks which kind of result this is, like a switch statement. `is` checks the
            // type, and inside each branch Kotlin knows which one it is, so we can read `number`
            // or `reason`.
            when (val result = report.result) {
                is IssueResult.Created ->
                    player.mes("Bug reported as issue #${result.number}. Thanks!")
                is IssueResult.Failure -> player.mes("Couldn't send bug report: ${result.reason}.")
            }
        }
    }

    // A small holder pairing a player with their report's result, for passing through the queue.
    // A `data class` is a class that just holds values; Kotlin writes the boilerplate for it.
    private data class CompletedReport(val uid: PlayerUid, val result: IssueResult)

    // Fixed settings for this command. A `companion object` holds values that belong to the class
    // itself rather than to one instance, much like "static" in Java. `const val` marks a value
    // fixed when the code is compiled.
    private companion object {
        // 10 seconds between reports. The `_` is just a digit separator for readability.
        private const val REPORT_COOLDOWN_MILLIS = 10_000L
        // Longest issue title, in characters, before the description is cut off.
        private const val TITLE_MAX_LENGTH = 70
        // Labels added to every issue made by ::bug, so they're easy to find on GitHub.
        private val ISSUE_LABELS = listOf("bug", "source: in-game")
    }
}
