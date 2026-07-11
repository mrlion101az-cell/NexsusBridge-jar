# NexusBridge 2.0.0

Clean production rebuild for Paper 1.21.11 / Java 21.

## Safe defaults
Only the F.R.A.C.T.U.R.E. repository listener is enabled by default. High-volume world, combat, inventory, sign, book, and region telemetry is included but disabled until its Render endpoint is confirmed.

## Commands
- `/nexusbridge status`
- `/nexusbridge ping`
- `/nexusbridge chat <message>`
- `/nexusbridge repository list`
- `/nexusbridge repository nearest`
- `/nexusbridge reload`
- `/nexusbridge test`
- `/nexusbridge flush`
- `/nexusbridge version`
- `/nexusbridge help`

Aliases: `/bridge`, `/kairos`

## Build
The included GitHub Action always verifies and uploads exactly `NexusBridge-2.0.0.jar`.

## Install
1. Stop the server.
2. Remove every older NexusBridge JAR from `plugins/`.
3. Upload only `NexusBridge-2.0.0.jar`.
4. Start the server.
5. Run `/version NexusBridge`, `/kairos status`, then `/kairos ping`.

The default repository is configured for world `nexsus` at `-16647, 72, 9648`.
