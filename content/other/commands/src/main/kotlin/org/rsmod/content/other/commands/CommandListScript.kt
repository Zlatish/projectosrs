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

/**
 * The `::commands` command, which opens an in-game menu listing every registered command with its
 * description. Clicking an entry prints that line to the chat box.
 *
 * It reads the server's command list as it is when used, so any new command added with
 * `onCommand(...)` shows up here automatically.
 *
 * A `PluginScript` is RS Mod's building block for game content: the server calls `startup()` on
 * every one when it starts. `@Inject constructor(...)` means the server builds this class and
 * passes in `protectedAccess`, which we need to open a menu for a player (explained below).
 */
class CommandListScript @Inject constructor(private val protectedAccess: ProtectedAccessLauncher) :
    PluginScript() {
    // The server's table of every registered command. `lateinit` tells Kotlin this will be filled
    // in later (in `startup()`), not when the class is created. Using it before then would crash.
    private lateinit var commands: CheatCommandMap

    // Called once when the server starts. Keeps a reference to the command table, then registers
    // ::commands itself. `::listCommands` passes the function below as the code to run when
    // someone types it.
    //
    // Unlike ::setrank, no `{ modLevel = ... }` block is given here, so the server doesn't limit
    // who can use ::commands by rank.
    override fun ScriptContext.startup() {
        commands = cheatCommandMap
        onCommand("commands", "List all available commands", ::listCommands)
    }

    // Runs when someone types ::commands. `with(cheat) { ... }` lets us write `player` instead of
    // `cheat.player` inside the block.
    private fun listCommands(cheat: Cheat) =
        with(cheat) {
            // Menus wait for the player to click something, so they have to run inside
            // "protected access": RS Mod's safe mode for player actions that take time. It makes
            // sure nothing else, such as another dialogue, is controlling the player at the same
            // time. `launch` starts `showCommands()` in that mode and returns `false` if the
            // player is already busy.
            val launched = protectedAccess.launch(player) { showCommands() }
            if (!launched) {
                // Shows "Please finish what you are doing first."
                player.mes(constants.dm_busy)
            }
        }

    // Builds and shows the menu.
    //
    // `suspend` marks a function that can pause and resume later without freezing the server.
    // Here it pauses at `menu(...)` until the player clicks an entry.
    //
    // `ProtectedAccess.showCommands()` is an extension function: it's written here but is called
    // as if it belonged to `ProtectedAccess`, so inside it we can call `menu(...)` and `mes(...)`
    // directly for that player.
    private suspend fun ProtectedAccess.showCommands() {
        // Turn the command table into lines like "::setrank - Set an account's staff rank",
        // sorted alphabetically by command name. This lists every command, including ones the
        // player's rank can't use.
        // `(name, handler) ->` splits each table entry into its name and its details.
        val lines =
            commands.commands.entries
                .sortedBy { it.key }
                .map { (name, handler) -> "::$name - ${handler.desc}" }
        // Show the menu and wait for a click. `picked` is the position of the chosen line in the
        // list (0 for the first).
        val picked = menu(title = "Admin commands", hotkeys = false, choices = lines)
        // Print the chosen line in the chat box so the player can read it in full.
        mes(lines[picked])
    }
}
