# Installing Edge Reader

## Supported environment

- IntelliJ IDEA 2026.2.x, platform build 262
- The JetBrains Runtime bundled with IntelliJ IDEA 2026.2

Older IDE releases are not supported by this build.

## Install from a ZIP

1. Download `edge-reader-<version>.zip`. Do not extract it.
2. In IntelliJ IDEA, open **Settings/Preferences → Plugins**.
3. Open the gear menu and choose **Install Plugin from Disk…**.
4. Select the ZIP and restart the IDE if requested.
5. Open **Edge Reader** from the right Tool Window stripe, or press **Option+R** on macOS / **Alt+R** on Windows and Linux.

## Build locally

Use Java 25 or the JBR bundled with IntelliJ IDEA 2026.2:

```shell
./gradlew clean test
./gradlew verifyPlugin
./gradlew buildPlugin
```

The installable archive is written to `build/distributions/`.

## Upgrade

Install the new ZIP through **Install Plugin from Disk…**. The application-level SQLite database and reader settings are retained. Keeping a backup of the IDE system directory is recommended before upgrading development builds.

## Uninstall

Uninstall Edge Reader from **Settings/Preferences → Plugins**. Uninstalling does not delete original books. See [PRIVACY.md](PRIVACY.md) for optional local-data cleanup.
