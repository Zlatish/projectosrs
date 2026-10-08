package org.rsmod.content.other.commands

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.api.db.gateway.model.isErr
import org.rsmod.api.player.output.mes
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.type.mod.ModLevelTypeList
import org.rsmod.game.type.mod.UnpackedModLevelType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The `::setrank username rank` command, which lets an owner change another account's staff rank
 * (player, moderator, admin or owner). The new rank is saved in the database, and if the player is
 * online it also takes effect for them straight away.
 *
 * A `PluginScript` is RS Mod's building block for game content: the server finds every class that
 * extends it when it starts up and calls its `startup()` function, which is where the script
 * registers what it reacts to (here, one command).
 *
 * `@Inject constructor(...)` means we never create this class ourselves. The server's dependency
 * injection library (Guice) builds it and passes in the three things listed in the brackets:
 * - `playerList`: everyone currently logged in, used to find the target player if they're online.
 * - `modLevelTypes`: every rank that exists in the game cache.
 * - `db`: lets us send work to the database.
 *
 * `private val` inside the brackets turns each one into a read-only field the rest of the class can
 * use.
 */
class RankCommands
@Inject
constructor(
    private val playerList: PlayerList,
    private val modLevelTypes: ModLevelTypeList,
    private val db: GameDbManager,
) : PluginScript() {
    // A lookup table from rank name to rank, e.g. "admin" -> the admin rank. It's used to turn the
    // word typed in the command into a real rank.
    //
    // `by lazy { ... }` means the table isn't built until the first time it's used, and is then
    // reused. That matters because the ranks aren't loaded yet when this class is first created.
    //
    // How it's built, step by step:
    // - `filter { ... }` keeps only ranks that have a name. `it` is Kotlin's default name for "the
    //   current item" inside a `{ ... }` block (a lambda).
    // - `associateBy { ... }` turns the list into a map, using each rank's lowercase name as its
    //   key. The `!!` tells Kotlin "I know this isn't null", which is safe because the filter
    //   above already removed nameless ranks.
    private val levelsByName: Map<String, UnpackedModLevelType> by lazy {
        modLevelTypes.values
            .filter { it.internalName != null }
            .associateBy { it.internalName!!.lowercase() }
    }

    // Called once when the server starts. Registers the ::setrank command and its description
    // (shown in the ::commands menu). `::setRank` passes the function below as the code to run
    // when someone types the command. The block at the end sets who can use it: only owners.
    // The server checks the rank before running the command, so `setRank` never runs for anyone
    // below owner.
    //
    // Note: the server lowercases everything typed after "::", so `args` is always lowercase.
    override fun ScriptContext.startup() {
        onCommand("setrank", "Set an account's staff rank", ::setRank) {
            modLevel = modlevels.owner
        }
    }

    // Runs when an owner types ::setrank. `cheat` holds who typed it (`player`) and the words
    // after the command (`args`).
    //
    // `with(cheat) { ... }` lets us write `player` and `args` instead of `cheat.player` and
    // `cheat.args` inside the block.
    private fun setRank(cheat: Cheat) =
        with(cheat) {
            // Needs at least a username and a rank. If not, explain how to use the command and
            // stop. `${...}` inside a string inserts the result of the code in the braces.
            if (args.size < 2) {
                player.mes("Use as ::setrank username rank (ranks: ${rankNames()})")
                return
            }

            // Usernames may contain spaces, so the rank is always the final argument.
            // `last()` takes the last word. `dropLast(1)` removes it from the list, and
            // `joinToString(" ")` glues the remaining words back together with spaces, so a name
            // like "big jay" still works.
            val rankArg = args.last().lowercase()
            val username = args.dropLast(1).joinToString(" ")

            // Look up the typed rank. If the word isn't a real rank, the lookup gives `null`,
            // so tell the player which ranks are valid and stop.
            val level = levelsByName[rankArg]
            if (level == null) {
                player.mes("Unknown rank '$rankArg'. Valid ranks: ${rankNames()}")
                return
            }

            // Stop an owner from changing their own rank, so they can't lock themselves out by
            // accident.
            if (username.equals(player.username, ignoreCase = true)) {
                player.mes("You cannot change your own rank.")
                return
            }

            // Ask the database to save the new rank.
            //
            // `db.request` runs `request` on a separate database thread, so a slow database never
            // freezes the game for everyone. When it finishes, `response` runs back on the game
            // thread with the result. `it` is the value each block receives: the database
            // connection for `request`, and the result for `response`.
            //
            // We store the player's `uid` (a unique ID) instead of the player object, because the
            // player might log out before the database replies.
            val uid = player.uid
            db.request(
                request = { updateRank(username, level, it) },
                response = { onRankUpdated(uid, username, level, it) },
            )
        }

    // Runs on the database thread. Saves the new rank against the account and reports whether an
    // account with that username was found.
    private fun updateRank(
        username: String,
        level: UnpackedModLevelType,
        connection: DatabaseConnection,
    ): GameDbResult<Boolean> {
        // The SQL to run. Each `?` is a placeholder filled in below. Using placeholders, instead
        // of pasting the username into the SQL text, stops a username from being able to change
        // what the SQL does (an attack called "SQL injection"). `LOWER(...)` on both sides makes
        // the match ignore capital letters.
        //
        // The `"""` quotes make a multi-line string, and `trimIndent()` removes the leading spaces
        // that are only there to keep the code tidy.
        val update =
            connection.prepareStatement(
                """
                    UPDATE accounts
                    SET modlevel = ?
                    WHERE LOWER(login_username) = LOWER(?)
                """
                    .trimIndent()
            )
        // `use { ... }` runs the block and then always closes the statement afterwards, even if
        // something goes wrong, so database resources aren't left open.
        update.use {
            // Fill in the two `?` placeholders: first the rank name, then the username.
            it.setString(1, level.internalName)
            it.setString(2, username)
            // `executeUpdate()` runs the SQL and returns how many accounts were changed. More than
            // 0 means the account was found. `GameDbResult.Ok` wraps the answer as a success.
            return GameDbResult.Ok(it.executeUpdate() > 0)
        }
    }

    // Runs back on the game thread once the database has finished. Tells the owner what happened
    // and, if the target player is online, updates their rank straight away.
    private fun onRankUpdated(
        uid: PlayerUid,
        username: String,
        level: UnpackedModLevelType,
        result: GameDbResult<Boolean>,
    ) {
        // Find the owner who typed the command. This gives `null` if they've logged out since.
        // `player?.mes(...)` only sends a message if `player` isn't null, so nothing crashes.
        val player = uid.resolve(playerList)

        // The database reported an error, so nothing was saved.
        if (result.isErr()) {
            player?.mes("Failed to update rank for '$username'. Please try again later.")
            return
        }
        // The SQL ran but no account has that username.
        if (!result.value) {
            player?.mes("No account found with username '$username'.")
            return
        }

        // The account's mod level is also saved on logout, so online players must be updated
        // in-memory too or the old rank would overwrite this change.
        // `firstOrNull { ... }` returns the first online player whose name matches, or `null` if
        // they're offline.
        val target = playerList.firstOrNull { it.username.equals(username, ignoreCase = true) }
        if (target != null) {
            target.modLevel = level
            target.mes("Your rank has been set to '${level.internalName}'.")
            target.mes("Log out and back in for your crown to update everywhere.")
        }

        // Confirm to the owner, saying whether the player was online or offline. In Kotlin,
        // `if ... else` can produce a value, which is stored in `status`.
        val status = if (target != null) "online" else "offline"
        player?.mes("Set rank of '$username' ($status) to '${level.internalName}'.")
    }

    // Builds the list of valid rank names for help messages, e.g. "player, moderator, admin,
    // owner", sorted from lowest rank to highest. A function written with `=` returns the result
    // of that one expression.
    private fun rankNames(): String =
        levelsByName.values.sortedBy { it.id }.joinToString { it.internalName!! }
}
