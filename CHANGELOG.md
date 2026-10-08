# Changelog

All notable changes to this server, newest first. Planned work lives in [ROADMAP.md](ROADMAP.md).

Each date uses these categories where relevant: **Added**, **Changed**, **Fixed**, **Removed**, and **Setup** for environment and tooling work.

---

## 2026-10-08

### Changed
- Added beginner-friendly comments to the code behind `::setrank`, `::bug` and `::commands`, explaining what each part does and the Kotlin features it uses (no change to how the commands work)
- Rewrote `README.md` for this server: tech stack, programs to install and where to get them, step-by-step setup for collaborators, and how the dev and main worlds differ, including how to grant admin on the main world

### Removed
- Old RS Mod setup screenshots in `docs/images`, which the new README no longer uses

### Setup
- The Integration Tests workflow no longer runs every night; start it by hand from the GitHub Actions tab
- Added a code comments rule to `CLAUDE.md`: all new or changed code must have plain-English comments that explain the code and its Kotlin features to a beginner

## 2026-10-05

### Added
- `::setrank username rank` command for giving accounts player, moderator, admin or owner rank (owner only)
- `::commands` command that opens an in-game menu listing every command with its description (admin only)
- `::bug description` command that files a GitHub issue with the reporter, location and time filled in (moderator and above)

### Fixed
- The All Settings window close (X) button did nothing
- Keybind settings reset after closing the window instead of saving. Keybinds now persist, clear duplicate keys, and "Restore default keybinds" works.
- Checked every settings menu item; the close button and keybinds above were the only failures found

### Setup
- Added `CLAUDE.md` with the Claude Code workflow rules: every change goes through a branch and a pull request, and finished work is logged in the changelog and removed from the roadmap
- Added GitHub issue templates for bug reports and change requests, plus a `.github/create-labels.ps1` script that creates the bug, change, severity, priority and area labels

## 2026-10-04

### Changed
- Changed the login message using a new SQL migration

### Setup
Dates for items without a commit are approximate (on or before this date).
- Installed IntelliJ, git and Temurin JDK 21
- Cloned RSMod (revision 233) and got the Gradle sync working
- Server runs from IntelliJ on port 43594
- RSProx installed and configured with a custom `proxy-targets.yaml` target
- Logged in with both the Native and RuneLite clients
- Forked the repository on GitHub and pushed commits
- Installed Claude Code
