# Creates the project's labels on your GitHub repo.
# Needs the GitHub CLI (gh) installed and signed in:  gh auth login
# Run from inside your repo folder:  .\.github\create-labels.ps1
# Safe to run again: --force updates labels that already exist.

$labels = @(
  @{ n = "bug";                 c = "d73a4a"; d = "Something is broken" },
  @{ n = "change";              c = "0e8a16"; d = "Add or change behaviour" },

  @{ n = "severity: critical";  c = "b60205"; d = "Crashes, data loss, or unplayable" },
  @{ n = "severity: high";      c = "d93f0b"; d = "A feature is broken" },
  @{ n = "severity: medium";    c = "fbca04"; d = "Works, but wrong" },
  @{ n = "severity: low";       c = "c5def5"; d = "Cosmetic or minor" },

  @{ n = "P1";                  c = "000000"; d = "Fix next" },
  @{ n = "P2";                  c = "555555"; d = "Fix soon" },
  @{ n = "P3";                  c = "aaaaaa"; d = "Fix later" },

  @{ n = "area: combat";        c = "1d76db"; d = "" },
  @{ n = "area: skills";        c = "1d76db"; d = "" },
  @{ n = "area: prayer";        c = "1d76db"; d = "" },
  @{ n = "area: items";         c = "1d76db"; d = "" },
  @{ n = "area: interfaces";    c = "1d76db"; d = "" },
  @{ n = "area: settings";      c = "1d76db"; d = "" },
  @{ n = "area: movement";      c = "1d76db"; d = "" },
  @{ n = "area: login";         c = "1d76db"; d = "" },
  @{ n = "area: server-db";     c = "1d76db"; d = "" },
  @{ n = "area: other";         c = "1d76db"; d = "" }
)

foreach ($l in $labels) {
  gh label create $l.n --color $l.c --description $l.d --force
}
