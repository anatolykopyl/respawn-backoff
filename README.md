# Respawn Backoff

Fabric mod for Minecraft: after you die, you wait out a real-time cooldown before you are fully back in play.

The wait length **doubles** with each death in a streak, up until a customizable maximum. 
The streak **resets once per calendar day** so the next death starts from the shortest wait again.

## Configuration

| Command | Effect |
|--------|--------|
| `/respawnbackoff config show` | Print current min and max wait (minutes). |
| `/respawnbackoff config min <minutes>` | Set minimum wait; must not exceed current max. |
| `/respawnbackoff config max <minutes>` | Set maximum wait; must not be below current min. |
| `/respawnbackoff reset` | Set **your** death-chain exponent so the **next** death uses the minimum wait only. Does **not** cancel an active countdown. |
| `/respawnbackoff reset <targets>` | Same for named players. |
| `/respawnbackoff skip` | End **your** active penalty now and return you to survival at your respawn point. |
| `/respawnbackoff skip <targets>` | Same for named players. |

## Build

`./gradlew build` → `build/libs/respawn-backoff-*.jar`
