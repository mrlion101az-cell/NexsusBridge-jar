# NexusBridge 1.0.0

Paper 1.21.11 / Java 21 event bridge for Nexus Universe and Kairos.

## Included
- Repository chest submissions with accepted-item removal after Kairos confirms acceptance
- Inventory snapshots
- Citizens/scoreboard-tag NPC interaction reporting
- Books, signs, block/button/lever interactions
- Configurable cuboid region enter/exit events
- Combat, deaths, mob kills
- Advancements, crafting, fishing, portals, sleeping
- Async HTTP queue with retries and timeouts
- `/nexusbridge status|reload|test|flush`

## Install
1. Build with `./gradlew build` or Gradle 8.10+.
2. Copy `build/libs/NexusBridge-1.0.0.jar` to the server `plugins` folder.
3. Start once, then edit `plugins/NexusBridge/config.yml`.
4. Set `bridge.base-url` and `bridge.auth-token`.
5. Confirm the repository world name and coordinates.
6. Restart or run `/nexusbridge reload`.

## Artifact tagging
Preferred PDC string key: `nexus_artifact` or namespaced equivalent, value such as:
`artifact_001_archive_access_badge`

The configured display-name fallback is accepted for early testing.
