# NexusBridge 3.0.0

Clean Paper 1.21.11 / Java 21 mission bridge for Nexus Universe.

## Included
- `/kairos`, `/bridge`, and `/nexusbridge` commands
- Kairos health ping and chat forwarding
- F.R.A.C.T.U.R.E. repository chest listener
- Artifact name and PDC recognition
- Accepted-item removal after Kairos confirms acceptance
- Rate-limited failures and a circuit breaker

## Removed
General world-event telemetry is not registered. Block interactions, combat, inventory movement, signs, books, crafting, fishing, portals, sleeping, advancements, and region events are neither sent nor displayed in Minecraft chat.

## Build
GitHub Actions produces exactly `NexusBridge-3.0.0.jar`.
