# Roadmap

Project: personal Old School RuneScape private server built on RSMod (revision 233), run locally from IntelliJ and played through RSProx.

How to use this file: tick items off with `[x]` and move them into **Done** when finished. Keep each item small enough to finish and test in one go.

---

## Priority

### High (do first)
- [ ] Fix burying bones (Bugs #2)

### Medium
- [ ] Check all settings menu items work correctly (Bugs #1)
- [ ] Remove run energy depletion (Features #1)

### Low / someday
- Nothing here yet

---

## Bugs

1. **Check all settings menu items work correctly**
   - Go through every tab and option in the in-game settings menu and note which ones do nothing or behave wrongly.
   - Suggested approach:
     - [ ] List every settings option and toggle it in-game
     - [ ] Record the ones that fail, with what happened versus what should happen
     - [ ] Turn each failure into its own small item here
   - Found and fixed so far:
     - [x] The X button did not close the All Settings window
     - [x] Keybinds (Controls > Keybinds) reset after closing the window instead of saving
2. **Fix burying bones**
   - Seen: *(add what happens when you try to bury bones, for example no Bury option, nothing happens, or no XP)*
   - Expected: the Bury option appears on bones, the bones are removed from the inventory, and Prayer XP is awarded.

---

## Features

1. **Remove run energy depletion**
   - Goal: running should no longer drain run energy.
   - Suggested approach: search the project for where energy is drained (for example `energy` or `runEnergy`) and change or disable that logic.

---

## Ideas

- Nothing here yet

---

## Done

- [x] Installed IntelliJ, git and Temurin JDK 21
- [x] Cloned RSMod and got the Gradle sync working
- [x] Server runs from IntelliJ (revision 233, port 43594)
- [x] RSProx installed and configured with a custom `proxy-targets.yaml` target
- [x] Logged in with both the Native and RuneLite clients
- [x] Changed the login message using a new SQL migration
- [x] Forked the repository on GitHub and pushed commits
- [x] Installed Claude Code
- [x] Fixed the All Settings window close (X) button
- [x] Added the `::setrank` command for giving accounts moderator, admin or owner rank
- [x] Fixed keybind settings not saving (keybinds now persist, clear duplicate keys, and "Restore default keybinds" works)
