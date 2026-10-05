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

class BugReportCommands
@Inject
constructor(private val playerList: PlayerList, private val github: GitHubIssueClient) :
    PluginScript() {
    // Results arrive on GitHub client threads and are handed to the game thread through this
    // queue, which is drained at the start of every game cycle.
    private val completed = ConcurrentLinkedQueue<CompletedReport>()

    // Only touched on the game thread.
    private val pending = mutableSetOf<PlayerUid>()
    private val lastReportMillis = mutableMapOf<PlayerUid, Long>()

    override fun ScriptContext.startup() {
        onCommand("bug", "Report a bug to the GitHub issue tracker", ::reportBug) {
            modLevel = modlevels.moderator
        }
        onEvent<GameLifecycle.StartCycle> { deliverResults() }
    }

    private fun reportBug(cheat: Cheat) =
        with(cheat) {
            val description = args.joinToString(" ").trim()
            if (description.isEmpty()) {
                player.mes("Use as ::bug description of the problem")
                return
            }

            val uid = player.uid
            if (uid in pending) {
                player.mes("Your last bug report is still being sent.")
                return
            }
            val now = System.currentTimeMillis()
            val last = lastReportMillis[uid]
            if (last != null && now - last < REPORT_COOLDOWN_MILLIS) {
                player.mes("You just sent a bug report. Wait a few seconds before sending another.")
                return
            }

            val config = github.loadConfig()
            if (config == null) {
                player.mes("Bug reporting isn't set up. Add a GitHub token to .data/github.toml.")
                return
            }

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

            pending += uid
            lastReportMillis[uid] = now
            player.mes("Sending bug report...")
            github.createIssue(config, title, body, ISSUE_LABELS) { result ->
                completed += CompletedReport(uid, result)
            }
        }

    private fun deliverResults() {
        while (true) {
            val report = completed.poll() ?: break
            pending -= report.uid
            // The reporter may have logged out while the request was in flight.
            val player = report.uid.resolve(playerList) ?: continue
            when (val result = report.result) {
                is IssueResult.Created ->
                    player.mes("Bug reported as issue #${result.number}. Thanks!")
                is IssueResult.Failure -> player.mes("Couldn't send bug report: ${result.reason}.")
            }
        }
    }

    private data class CompletedReport(val uid: PlayerUid, val result: IssueResult)

    private companion object {
        private const val REPORT_COOLDOWN_MILLIS = 10_000L
        private const val TITLE_MAX_LENGTH = 70
        private val ISSUE_LABELS = listOf("bug", "source: in-game")
    }
}
