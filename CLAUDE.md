## Git workflow (always follow)

Never commit or push directly to `main`. Every change, however small, goes through a branch and a pull request.

1. **Start from an up-to-date main.** Before making changes, run `git switch main` and `git pull`.
2. **Create a new branch for each change.** Use a short descriptive name with a prefix:
   `feature/` for new content, `fix/` for bug fixes, `refactor/` for restructuring, `chore/` for config or tooling.
   Example: `feature/custom-drop-tables`. One logical change per branch.
3. **Make and verify the change on that branch.** Make sure the project still builds before committing. If you can't verify something (e.g. it needs in-game testing), say so.
4. **Update the changelog and roadmap** (see the section below) as part of the change.
5. **Commit with clear messages** describing what changed and why. Small, focused commits are preferred, but the commit that completes the change must also contain its CHANGELOG.md entry and the matching ROADMAP.md removal.
6. **Push the branch and open a pull request into `main`** using the GitHub CLI (`gh pr create --base main`). In the PR description include:
   - a summary of what changed and why
   - the files and systems affected
   - how to test it in-game, step by step
   - any risks or things I should check closely
7. **Do not merge the pull request.** I will review the PR, test the branch locally, and merge it myself.
   Stop after creating the PR and give me the link.

If I ask for a follow-up change to an open PR, commit it to the same branch rather than starting a new one, and update that PR's existing changelog entry instead of adding a second one.
If you're unsure whether something should be a new branch or part of an existing one, ask me.

## Changelog and roadmap

CHANGELOG.md and ROADMAP.md already exist in the repo root. Follow their existing format and keep their intro text intact.

- **CHANGELOG.md** records finished work. When a change is complete, add an entry under a `## YYYY-MM-DD` heading for that day's date.
  - Newest dates go at the top, directly below the `---` line. If a heading for today already exists, add to it rather than creating a duplicate.
  - Within each date, group entries under `###` subheadings, using only the ones needed and in this order: `Added`, `Changed`, `Fixed`, `Removed`, `Setup` (Setup is for environment and tooling work).
  - Each entry is one short line describing the change from a player's or admin's point of view where possible. Put in-game commands in backticks, e.g. `::setrank`.
- **ROADMAP.md** holds only open work. When a change completes a roadmap item, remove it in the same commit, from every place it appears: its line in the **Priority** list and its numbered entry under **Bugs**, **Features** or **Ideas**. Renumber the remaining entries and update any `(Bugs #n)` / `(Features #n)` references so they still match.
  - Never move finished items into the **Done** section; that section only points to CHANGELOG.md.
  - If a priority group becomes empty, replace its items with `- Nothing here yet`.
- If a change doesn't correspond to any roadmap item, just add the changelog entry.
- If a change only partly completes a roadmap item, leave the item in place and update its wording to reflect what's left.
