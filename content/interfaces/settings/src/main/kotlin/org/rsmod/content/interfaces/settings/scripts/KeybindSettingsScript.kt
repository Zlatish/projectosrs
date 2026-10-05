package org.rsmod.content.interfaces.settings.scripts

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.interfaces.settings.configs.setting_components
import org.rsmod.content.interfaces.settings.configs.setting_varbits
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.type.varbit.VarBitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Persists the keybinds chosen in the "All Settings" > Controls > Keybinds section.
 *
 * The client lays the keybinds out as a 3-column grid of `settings:settings_clickzone` rows (comsub
 * 27-41, numbered left-to-right, top-to-bottom). Clicking a row opens its dropdown and picking an
 * option sends `settings:dropdown_buttons` with comsub `2 + 3 * value`, where value 0 = None, 1-12
 * = F1-F12 and 13 = Esc.
 */
class KeybindSettingsScript : PluginScript() {
    /** The keybind row whose dropdown each player currently has open. */
    private val openRows = hashMapOf<PlayerUid, Int>()

    override fun ScriptContext.startup() {
        onIfOverlayButton(setting_components.settings_clickzone) { selectRow(player, comsub) }
        onIfOverlayButton(setting_components.settings_dropdown_buttons) {
            selectKey(player, comsub)
        }
        onPlayerLogout { openRows.remove(player.uid) }
    }

    private fun selectRow(player: Player, row: Int) {
        if (row == RESTORE_DEFAULTS_ROW) {
            openRows.remove(player.uid)
            restoreDefaults(player)
            return
        }
        if (row in keybindRows) {
            openRows[player.uid] = row
        } else {
            openRows.remove(player.uid)
        }
    }

    private fun selectKey(player: Player, comsub: Int) {
        val row = openRows.remove(player.uid) ?: return
        val varbit = keybindRows[row] ?: return
        val key = (comsub - 2) / 3
        if ((comsub - 2) % 3 != 0 || key !in NONE..ESC) {
            return
        }
        // A key can only open one panel, so unbind it from any other panel using it.
        if (key != NONE) {
            for (other in keybindRows.values) {
                if (other != varbit && player.vars[other] == key) {
                    VarPlayerIntMapSetter.set(player, other, NONE)
                }
            }
        }
        VarPlayerIntMapSetter.set(player, varbit, key)
    }

    private fun restoreDefaults(player: Player) {
        for ((varbit, key) in defaultKeys) {
            VarPlayerIntMapSetter.set(player, varbit, key)
        }
    }

    private companion object {
        const val NONE = 0
        const val ESC = 13
        const val RESTORE_DEFAULTS_ROW = 41

        val keybindRows: Map<Int, VarBitType> =
            mapOf(
                // Row 1
                27 to setting_varbits.stone_combat_key,
                28 to setting_varbits.stone_prayer_key,
                29 to setting_varbits.stone_options1_key,
                // Row 2
                30 to setting_varbits.stone_stats_key,
                31 to setting_varbits.stone_magic_key,
                32 to setting_varbits.stone_options2_key,
                // Row 3
                33 to setting_varbits.stone_journal_key,
                34 to setting_varbits.stone_friends_key,
                35 to setting_varbits.stone_clanchat_key,
                // Row 4
                36 to setting_varbits.stone_inv_key,
                37 to setting_varbits.stone_account_key,
                38 to setting_varbits.stone_music_key,
                // Row 5 (41 is the "Restore default keybinds" button)
                39 to setting_varbits.stone_worn_key,
                40 to setting_varbits.stone_logout_key,
            )

        // Keys applied by "Restore default keybinds".
        val defaultKeys: List<Pair<VarBitType, Int>> =
            listOf(
                setting_varbits.stone_combat_key to 1,
                setting_varbits.stone_stats_key to 2,
                setting_varbits.stone_journal_key to 3,
                setting_varbits.stone_inv_key to 4,
                setting_varbits.stone_worn_key to 5,
                setting_varbits.stone_prayer_key to 6,
                setting_varbits.stone_magic_key to 7,
                setting_varbits.stone_clanchat_key to 8,
                setting_varbits.stone_friends_key to 9,
                setting_varbits.stone_account_key to 10,
                setting_varbits.stone_options1_key to 11,
                setting_varbits.stone_options2_key to 12,
                setting_varbits.stone_music_key to NONE,
                setting_varbits.stone_logout_key to NONE,
            )
    }
}
