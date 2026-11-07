# RankedCore (Paper 26.2 target)

This project was reconstructed from the source text supplied in the conversation. The Paper API target and Java release are set for Paper 26.2 / Java 25.

## Build

Requires JDK 25 and Maven 3.9+. Run `mvn clean package`; output is `target/RankedCore-1.0.0.jar`. A GitHub Actions workflow is included at `.github/workflows/build.yml` and uploads the JAR as a workflow artifact.

## Important status

This is a reconstructed source package, **not yet a verified release**. The pasted source contains known compile/runtime defects that still need correction and a successful build/test pass before installation. In particular, several command handlers chain `.replace(...)` onto `Msg.send(...)` even though that method returns `void`; SQLite stats restoration is a no-op; and the MySQL connection currently ignores configured credentials. Do not install on a live server until these are fixed and the build passes.

RankedCore is intended to expose PlaceholderAPI placeholders and not manage LuckPerms groups/prefixes or edit TAB/LPC configuration.
