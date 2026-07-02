# Sky Legends

[![View on GitHub](https://img.shields.io/badge/GitHub-llassan%2Fsky__legends-181717?logo=github)](https://github.com/llassan/sky_legends)

A premium-feel vertical scrolling shoot-'em-up (SHMUP) for Android, built in **pure Kotlin +
Canvas** (no game engine), in the same lineage as *Iron Fury*. It ships an 8-sector campaign,
3 unlockable aircraft with special abilities, a coin-spend upgrade lab, and fully procedural
audio — all on the data-driven architecture the design called for.

## Play

```bash
./gradlew :app:assembleDebug        # build the debug APK
./gradlew :app:installDebug         # install on a connected device / emulator
```

- **Drag** anywhere to fly (the ship rises above your thumb so it's never hidden).
- **Auto-fire** is always on — focus on dodging and positioning.
- Tap the **ability button** (bottom-right) to trigger your aircraft's special move.
- Grab **power-ups**: `M`/`S`/`P` swap or upgrade your weapon, `◇` shield, `+` heal.
- Chain kills to build the **combo multiplier**; clear each sector's boss to progress.

## Campaign

8 sectors × 5 stages (4 enemy waves + 1 boss) = 40 stages, generated from a difficulty curve
rather than hand-authored file-by-file — enemy types unlock progressively, spawn density and
pacing tighten, and each sector culminates in its own named boss (`SENTINEL MK-I` through
`DREADNOUGHT PRIME`), scaled in HP and aggression. Clearing a sector unlocks the next and
banks it in the save; dying replays the same sector. See `level/Level.kt` + `bosses/BossSpec.kt`.

## Aircraft & abilities

3 data-driven ships in the Hangar, each with a hull silhouette, stats, a default weapon, and
one active special ability on a cooldown (tap the in-game ability button):

| Aircraft | Style | Ability |
|---|---|---|
| Vanguard (free) | Balanced | **Overdrive** — temporary +60% damage, faster fire rate |
| Nova (500 coins) | Glass-cannon interceptor | **Phase Dash** — instant reposition + i-frames |
| Titan (1200 coins) | Fortress bomber | **Siege Burst** — damages everything on screen + shield refill |

## Meta-progression & audio

- **Procedural audio** (`audio/SoundManager.kt`) — every SFX and all music are *synthesized at
  runtime* (PCM → in-cache WAV → `SoundPool`/`MediaPlayer`). No asset files. Music is
  **adaptive and layered**: a base pulse, a combat groove, and a boss-intensity layer all loop
  simultaneously on separate players, sharing one arrangement so [SoundManager.updateMusicMix]
  can crossfade their volumes toward the situation (calm menu → active combat → boss fight)
  without ever sounding like a track switch. A heartbeat SFX cues low health. Music/SFX mute
  toggles on the menu, persisted.
- **Hangar** (`aircraft/AircraftSpec.kt`) — browse aircraft, view stats + ability, **select**
  or **unlock with coins**. Selection persists.
- **Upgrade Lab** (`upgrade/Upgrades.kt`) — 6 permanent, coin-bought upgrades (Damage, Fire
  Rate, Armor, Shield, Magnet, Payload/coin-bonus) with per-level scaling costs. Bonuses are
  applied to the player at the start of every run. All levels persist in the save.

## Architecture (why it scales)

Everything content-shaped is **pure data**:

| System | File | How you extend it |
|---|---|---|
| Weapons | `weapons/WeaponSpec.kt` | add a `WeaponSpec` to `WeaponCatalog` |
| Enemies | `enemies/EnemySpec.kt` | add an `EnemySpec` to `EnemyCatalog` |
| Campaign | `level/Level.kt` | tune the difficulty curve or add a sector name |
| Bosses | `bosses/BossSpec.kt` | add a `BossSpec` tier; `entities/Boss.kt` holds the attack patterns |
| Aircraft | `aircraft/AircraftSpec.kt` | add an `AircraftSpec` (+ an `AbilityType` if new) |
| Upgrades | `upgrade/Upgrades.kt` | add an `UpgradeDef` to `UpgradeCatalog` + a bonus getter |

Engine plumbing is separated from content:

- `GameThread` — fixed-timestep loop with frame-skip guard.
- `GameView` — orchestrator: state machine, collision resolution, render pipeline, input;
  implements `LevelDirector.Context` so the director spawns into it.
- `entities/` — `Entity` base (center-based, circular hitboxes), `Player` (aircraft-driven
  stats/abilities), `Enemy`, `Bullet`, `Boss` (spec-driven), `Coin`, `PowerUp`.
- `effects/` — `ParticleSystem` (pooled), `Background` (parallax).
- `fx/Camera.kt` — trauma shake, flash, hitstop.
- `level/LevelDirector.kt` — plays a timeline (delays / spawns / wait-clear / boss),
  expands formations, staggers streams & swoops.
- `ui/` — `Hud` (all drawing) + `UiLayout` (shared button rects for draw & hit-test).
- `utils/` — `Vector2`, `Pool`, `Constants`, `SaveManager` (coins, unlocks, campaign
  progress, upgrade levels, audio prefs).

All gameplay runs at a fixed **540×960 virtual resolution**, letterbox-scaled to the device,
so behaviour is identical on every screen.

## Roadmap toward the full design

Done so far: 8-sector campaign (40 stages) with escalating named bosses, 3 aircraft with
special abilities + hangar, upgrade lab, adaptive layered procedural audio. Next, in rough
order: environment/visual themes per sector, more aircraft, daily/weekly challenge modes,
and gamepad/keyboard input. None require engine rewrites — they slot into the data-driven
systems above.
