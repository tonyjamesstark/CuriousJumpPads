# CuriousJumpPads

Launch players across a map: commands that set player velocity, and jump pads that launch
whoever steps on them. Can aim at a landing point: give it the spot, and it works out the arc.

Paper/Purpur 26.2, Java 25.

## Documentation

- [Quick start](docs/quick-start.md): install, then make a pad that lands players on a block.
- [Commands and permissions](docs/commands.md): every command, permission and config key.
- [Command block design](docs/command-blocks.md): pads built from vanilla command blocks.
- [Limits](docs/limits.md): reach, accuracy and the velocity cap.
- [API reference](https://tonyjamesstark.github.io/CuriousJumpPads/api/): Javadoc.

Hosted at <https://tonyjamesstark.github.io/CuriousJumpPads/>, published from `main` by
`.github/workflows/docs.yml`.

## Building and testing

Needs JDK 25; the scripts default to `~/.sdkman/candidates/java/25.0.4-tem` (override with
`JAVA_HOME_25`).

| Command | Does |
|---|---|
| `mvn -o verify` | Build, Spotless check, unit tests |
| `scripts/gen-docs.sh` | Build the API reference into `docs/api/` (gitignored) for a local preview |
| `scripts/audit-docs.sh` | Fail on missing Javadoc, commands or permissions missing from `docs/commands.md`, or broken links |
| `scripts/coverage.sh` | Unit-test line and branch coverage per file; HTML in `target/coverage/unit/` |
| `scripts/coverage.sh --live` | Also runs the live server test with the JaCoCo agent and merges both; needs the test wrapper (`../tests/launch.sh`) |

`MIN_LINE=<percent> scripts/coverage.sh` fails below that total line coverage.
