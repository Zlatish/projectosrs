# Roadmap

Project: personal Old School RuneScape private server built on RSMod (revision 233), run locally from IntelliJ and played through RSProx.

How to use this file: when an item is finished, remove it from here and add it to [CHANGELOG.md](CHANGELOG.md) under that day's date. Keep each item small enough to finish and test in one go.

---

## Priority

### High (do first)
- [ ] Fix burying bones (Bugs #1)
- [ ] Default attack options to "Left-click where available" for new characters (Bugs #2)
- [ ] Build an NPC drop table system (Features #2)
- [ ] Add drop tables for the Lumbridge area NPCs (Features #3, needs Features #2 first)

### Medium
- [ ] Remove run energy depletion (Features #1)

### Low / someday
- Nothing here yet

---

## Bugs

1. **Fix burying bones**
   - Seen: *(add what happens when you try to bury bones, for example no Bury option, nothing happens, or no XP)*
   - Expected: the Bury option appears on bones, the bones are removed from the inventory, and Prayer XP is awarded.

2. **Default attack options to "Left-click where available" for new characters**
   - Seen: new characters start with **NPC attack options** and **Player attack options** (Settings → Controls) set to "Depends on combat levels". Higher-level NPCs then have no left-click Attack, which makes it look like NPCs can't be attacked.
   - Expected: both settings start as "Left-click where available". Players can still change them, and the change is saved.
   - Suggested approach: the settings are stored in the varps `option_attackpriority` (players) and `option_attackpriority_npc` (NPCs), handled in `ControlSettingsScript.kt`. Set both when a new character is first created, rather than on every login, so a player's own choice isn't overwritten.

---

## Features

1. **Remove run energy depletion**
   - Goal: running should no longer drain run energy.
   - Suggested approach: search the project for where energy is drained (for example `energy` or `runEnergy`) and change or disable that logic.

2. **Build an NPC drop table system**
   - Seen: there's no drop table system yet. Every NPC that dies drops one set of regular bones and nothing else (the `TODO: Drop tables` in `api/death/src/main/kotlin/org/rsmod/api/death/NpcDeath.kt`).
   - Goal: a reusable way to give each NPC type its own drop table. It should support:
     - always drops (e.g. bones or ashes)
     - a main table with weighted chances
     - quantity ranges (e.g. 5–15 coins)
     - noted items
     - rare tertiary drops (e.g. clue scrolls)
     - shared tables that many monsters use (e.g. the gem drop table)
   - Done when: an NPC with a table drops from it for the player who did the most damage, NPCs without a table still drop bones as now, and one test NPC (e.g. goblin) has a working table.

3. **Add drop tables for the Lumbridge area NPCs**
   - Goal: every attackable NPC currently spawned has its proper OSRS drop table, using the OSRS Wiki as the source. Variants share one table, e.g. all 8 goblin types use the goblin table.
   - NPCs: goblins, cows and calves, chickens, rats, giant rats, spiders, giant spiders, men and women, farmers, imps, muggers, Lumbridge guards, sheep and rams, unicorn.
   - Some items may need adding to the server's item references before they can be dropped.
   - Needs Features #2 first. Any new area added later should come with drop tables for its NPCs.

---

## Ideas

- Create some admin commands (they will appear in `::commands` automatically):
  - ::setlevel skill playername lvl (Sets a skill to the allocated level)
  - ::resetlevels (resets all skill levels to 1)
  - A teleport to player command (Retrieves player coordinates and teleports you to them.)
- Test creating a shop NPC
- Plan the creation of a home area, this should be a QoL hub;
  - Banking
  - Shop NPCs
  - Slayer Masters
  - Restore Fountain (HP, Special attack, cure everything, prayer)
  - Crystal chests
    - Teleport Nexus / Menu

---

## Done

Finished work is recorded by date in [CHANGELOG.md](CHANGELOG.md).
