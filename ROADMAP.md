# Roadmap

Project: personal Old School RuneScape private server built on RSMod (revision 233), run locally from IntelliJ and played through RSProx.

How to use this file: when an item is finished, remove it from here and add it to [CHANGELOG.md](CHANGELOG.md) under that day's date. Keep each item small enough to finish and test in one go.

---

## Priority

### High (do first)
- [ ] Fix burying bones (Bugs #1)

### Medium
- [ ] Remove run energy depletion (Features #1)

### Low / someday
- Nothing here yet

---

## Bugs

1. **Fix burying bones**
   - Seen: *(add what happens when you try to bury bones, for example no Bury option, nothing happens, or no XP)*
   - Expected: the Bury option appears on bones, the bones are removed from the inventory, and Prayer XP is awarded.

---

## Features

1. **Remove run energy depletion**
   - Goal: running should no longer drain run energy.
   - Suggested approach: search the project for where energy is drained (for example `energy` or `runEnergy`) and change or disable that logic.

---

## Ideas

- Create some admin commands & create a list overlay that displays them in game as a reminder:
  - ::commands - opens an overlay showing a list of admin commands that can be used
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
