# Project OSRS

A personal Old School RuneScape private server, built on [RS Mod][rsmod] (game revision 233). It runs locally from IntelliJ and is played through [RSProx][rsprox].

This README is for collaborators: what to install, how to get the server running on your own machine, and how changes get into the project.

- **[ROADMAP.md](ROADMAP.md)**: what's planned and in what order
- **[CHANGELOG.md](CHANGELOG.md)**: what has been finished, by date
- **[Issues][issues]**: bug reports and change requests

## Tech stack

| Part | What it is |
| --- | --- |
| Language | Kotlin, running on Java 21 |
| Build | Gradle (the wrapper is included, so there's nothing to install) |
| Server base | RS Mod, game revision 233 |
| Database | SQLite, stored in `.data/saves/game.db` (created automatically) |
| Client | RSProx, which launches the Native or RuneLite client and connects it to your server |
| Hosting | Each collaborator runs their own local server on port `43594` |

## What you need to install

Install these in order. All of them are free.

| Program | Why you need it | Where to get it |
| --- | --- | --- |
| **Git** | Downloading the code and sending your changes | [git-scm.com/downloads][git] |
| **Java 21 (Temurin)** | Runs the server. It must be version 21 or later. | [adoptium.net][adoptium] (choose **JDK 21 LTS**) |
| **IntelliJ IDEA** | Editing and running the server. The free Community Edition is enough. | [jetbrains.com/idea/download][intellij] |
| **RSProx** | The client you play through | [github.com/blurite/rsprox][rsprox] (see its README for the installer) |
| **GitHub account** | Getting access to the repo and opening pull requests | [github.com/signup][github-signup] |
| **DB Browser for SQLite** *(only for the main world)* | Editing the database to set up the main world or grant ranks (see [Dev world vs main world](#dev-world-vs-main-world)) | [sqlitebrowser.org][sqlitebrowser] |

On Windows you can install Java and Git from a terminal instead:

```sh
winget install --id=EclipseAdoptium.Temurin.21.JDK -e
winget install --id=Git.Git -e
```

Check that Java is installed by opening a new terminal and running `java -version`. It should say `21` or higher.

## Getting the server running

### 1. Get the code

In IntelliJ: **File → New → Project from Version Control**, then paste this URL:

```
https://github.com/Zlatish/projectosrs.git
```

Or from a terminal:

```sh
git clone https://github.com/Zlatish/projectosrs.git
```

### 2. Load the project

1. Open the project in IntelliJ. When it shows **Load Gradle Project**, click it and wait for it to finish. The first time can take several minutes.
2. If IntelliJ asks which JDK to use, choose the Java 21 you installed (**File → Project Structure → SDK**).

### 3. Start the server

1. In the top-right run menu, select **GameServer** and click **Run**.
2. The first run downloads the game files and sets up the `.data` folder (game cache, RSA keys, config and database). This takes a while. Later runs are much faster.
3. The first run may also pack the cache and restart itself. That's normal. The server is ready once the console stops printing new lines.

If the console logging looks wrong on the first run, stop the server and run it again.

From a terminal, run `gradlew install` once and then `gradlew run` each time.

### 4. Connect with RSProx

1. Install and open RSProx.
2. Add a proxy target for your local server in RSProx's `proxy-targets.yaml`. Each server generates its own RSA key on first run. The public modulus RSProx needs is in **`.data/client.key`** in your copy of the project, so use the value from your own file, not someone else's.
3. In RSProx, pick that target, launch the Native or RuneLite client, and log in.

There's no registration page. How login and ranks work depends on which world your server runs. See [Dev world vs main world](#dev-world-vs-main-world) below. A fresh install runs the `dev` world, so logging in with any username and password creates an account with **owner** rank.

Type `::commands` in-game to see every command your rank can use.

### 5. Set up in-game bug reports (optional)

The `::bug description` command (moderator rank and above) files a GitHub issue straight from the game. The first time it runs, it creates `.data/github.toml`. Add a [fine-grained personal access token][pat] to that file. Limit the token to this repo and give it only the **Issues: Read and write** permission.

`.data/github.toml` is gitignored. **Never commit it or share your token.**

## Dev world vs main world

The server can run one of two worlds (the code calls them realms). You pick one with the `realm` line in `.data/server.toml`.

| | `dev` (the default) | `main` |
| --- | --- | --- |
| Meant for | Building and testing on your own machine | Playing the server as intended |
| New usernames | Account is created on first login | **Rejected** until registration is switched off (see below) |
| Passwords | Any password is accepted | Checked against the password saved for the account |
| Rank for new accounts | **Owner** | **Player** |
| XP rate | 100x | 1x |

Each world keeps its own characters, but an account's **rank belongs to the account**, not the world. An account that became owner on `dev` is still owner on `main`.

### Switching to the main world

1. **Stop the server.**
2. Open `.data/server.toml` and change `realm = 'dev'` to `realm = 'main'`.
3. The `main` world only lets in accounts that already exist. To let players create an account by logging in, run this SQL on `.data/saves/game.db` (see [Editing the database](#editing-the-database)):
   ```sql
   UPDATE realms SET require_registration = 0 WHERE name = 'main';
   ```
4. Start the server. The first time someone logs in with a new username, their password is saved and they get **player** rank.

### Granting admin on the main world

New accounts on `main` start as **player**. Ask the collaborator to log in once so their account exists, then use either option.

**Option A: in-game (needs an owner account)**

Log in with an owner account, for example one first created on the `dev` world, and run:

```
::setrank theirname admin
```

This works whether they're online or not. If they're online their rank changes immediately, and they should log out and back in so their crown shows everywhere.

**Option B: in the database (no owner account needed)**

1. Make sure they're logged out, then **stop the server**. Ranks are saved when a player logs out, so an edit made while the server is running can be overwritten.
2. Run this SQL on `.data/saves/game.db`, replacing `theirname` with their login name:
   ```sql
   UPDATE accounts SET modlevel = 'admin' WHERE LOWER(login_username) = LOWER('theirname');
   ```
3. Start the server. They'll be admin the next time they log in.

Valid ranks, lowest to highest, are `player`, `moderator`, `admin` and `owner`. Only an owner can use `::setrank`.

### Editing the database

The database is a single SQLite file at `.data/saves/game.db`. To run SQL on it:

1. **Stop the server** first.
2. Open the file in [DB Browser for SQLite][sqlitebrowser] (**Open Database**).
3. Paste the SQL into the **Execute SQL** tab, run it, then click **Write Changes**.
4. Close DB Browser before starting the server again.

## Making changes

Nothing is committed straight to `main`. Every change goes through its own branch and a pull request:

1. Start from the latest code: `git switch main` then `git pull`.
2. Create a branch with a prefix that describes the change: `feature/`, `fix/`, `refactor/` or `chore/`, e.g. `fix/burying-bones`.
3. Make the change and check the server still builds and runs.
4. Add a line to [CHANGELOG.md](CHANGELOG.md) under today's date. If the change finishes a [ROADMAP.md](ROADMAP.md) item, remove that item.
5. Push the branch and open a pull request into `main`. Describe what changed and how to test it in-game.

Every pull request runs the build and tests automatically (the **CI** workflow). A slower **Integration Tests** workflow can be started by hand from the **Actions** tab.

## Troubleshooting

- **Gradle sync fails or complains about Java**: make sure IntelliJ is using JDK 21 (**File → Project Structure → SDK**, and **Settings → Build Tools → Gradle → Gradle JVM**).
- **The first-run download was interrupted**: run `gradlew cleanInstall`, then `gradlew install`, then start the server again.
- **The client can't connect**: check the server is running, the RSProx target points at your own machine, and its modulus matches your `.data/client.key`.

## Credits and license

Built on [RS Mod][rsmod], which is released under the ISC license. The original copyright notice is kept in [LICENSE.md](LICENSE.md), as that license requires.

[rsmod]: https://github.com/rsmod/rsmod
[rsprox]: https://github.com/blurite/rsprox
[issues]: https://github.com/Zlatish/projectosrs/issues
[git]: https://git-scm.com/downloads
[adoptium]: https://adoptium.net/temurin/releases/?version=21
[intellij]: https://www.jetbrains.com/idea/download/
[github-signup]: https://github.com/signup
[pat]: https://github.com/settings/personal-access-tokens/new
[sqlitebrowser]: https://sqlitebrowser.org/dl/
