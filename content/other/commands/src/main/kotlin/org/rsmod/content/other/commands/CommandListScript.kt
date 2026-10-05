package org.rsmod.content.other.commands

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CommandListScript @Inject constructor(private val protectedAccess: ProtectedAccessLauncher) :
    PluginScript() {
    private lateinit var commands: CheatCommandMap

    override fun ScriptContext.startup() {
        commands = cheatCommandMap
        onCommand("commands", "List all available commands", ::listCommands)
    }

    private fun listCommands(cheat: Cheat) =
        with(cheat) {
            val launched = protectedAccess.launch(player) { showCommands() }
            if (!launched) {
                player.mes(constants.dm_busy)
            }
        }

    private suspend fun ProtectedAccess.showCommands() {
        val lines =
            commands.commands.entries
                .sortedBy { it.key }
                .map { (name, handler) -> "::$name - ${handler.desc}" }
        val picked = menu(title = "Admin commands", hotkeys = false, choices = lines)
        mes(lines[picked])
    }
}
