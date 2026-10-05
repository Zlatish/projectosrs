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

class RankCommands
@Inject
constructor(
    private val playerList: PlayerList,
    private val modLevelTypes: ModLevelTypeList,
    private val db: GameDbManager,
) : PluginScript() {
    private val levelsByName: Map<String, UnpackedModLevelType> by lazy {
        modLevelTypes.values
            .filter { it.internalName != null }
            .associateBy { it.internalName!!.lowercase() }
    }

    override fun ScriptContext.startup() {
        onCommand("setrank", "Set an account's staff rank", ::setRank) {
            modLevel = modlevels.owner
        }
    }

    private fun setRank(cheat: Cheat) =
        with(cheat) {
            if (args.size < 2) {
                player.mes("Use as ::setrank username rank (ranks: ${rankNames()})")
                return
            }

            // Usernames may contain spaces, so the rank is always the final argument.
            val rankArg = args.last().lowercase()
            val username = args.dropLast(1).joinToString(" ")

            val level = levelsByName[rankArg]
            if (level == null) {
                player.mes("Unknown rank '$rankArg'. Valid ranks: ${rankNames()}")
                return
            }

            if (username.equals(player.username, ignoreCase = true)) {
                player.mes("You cannot change your own rank.")
                return
            }

            val uid = player.uid
            db.request(
                request = { updateRank(username, level, it) },
                response = { onRankUpdated(uid, username, level, it) },
            )
        }

    private fun updateRank(
        username: String,
        level: UnpackedModLevelType,
        connection: DatabaseConnection,
    ): GameDbResult<Boolean> {
        val update =
            connection.prepareStatement(
                """
                    UPDATE accounts
                    SET modlevel = ?
                    WHERE LOWER(login_username) = LOWER(?)
                """
                    .trimIndent()
            )
        update.use {
            it.setString(1, level.internalName)
            it.setString(2, username)
            return GameDbResult.Ok(it.executeUpdate() > 0)
        }
    }

    private fun onRankUpdated(
        uid: PlayerUid,
        username: String,
        level: UnpackedModLevelType,
        result: GameDbResult<Boolean>,
    ) {
        val player = uid.resolve(playerList)
        if (result.isErr()) {
            player?.mes("Failed to update rank for '$username'. Please try again later.")
            return
        }
        if (!result.value) {
            player?.mes("No account found with username '$username'.")
            return
        }

        // The account's mod level is also saved on logout, so online players must be updated
        // in-memory too or the old rank would overwrite this change.
        val target = playerList.firstOrNull { it.username.equals(username, ignoreCase = true) }
        if (target != null) {
            target.modLevel = level
            target.mes("Your rank has been set to '${level.internalName}'.")
            target.mes("Log out and back in for your crown to update everywhere.")
        }

        val status = if (target != null) "online" else "offline"
        player?.mes("Set rank of '$username' ($status) to '${level.internalName}'.")
    }

    private fun rankNames(): String =
        levelsByName.values.sortedBy { it.id }.joinToString { it.internalName!! }
}
