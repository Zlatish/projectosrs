package org.rsmod.content.interfaces.settings.configs

import org.rsmod.api.type.refs.varbit.VarBitReferences

typealias setting_varbits = SettingVarBits

object SettingVarBits : VarBitReferences() {
    val panel_tab = find("settings_side_panel_tab", 92479944086622)

    val stone_combat_key = find("stone_combat_key")
    val stone_stats_key = find("stone_stats_key")
    val stone_journal_key = find("stone_journal_key")
    val stone_inv_key = find("stone_inv_key")
    val stone_worn_key = find("stone_worn_key")
    val stone_prayer_key = find("stone_prayer_key")
    val stone_magic_key = find("stone_magic_key")
    val stone_clanchat_key = find("stone_clanchat_key")
    val stone_friends_key = find("stone_friends_key")
    val stone_account_key = find("stone_account_key")
    val stone_logout_key = find("stone_logout_key")
    val stone_options1_key = find("stone_options1_key")
    val stone_options2_key = find("stone_options2_key")
    val stone_music_key = find("stone_music_key")
}
