# Game Save Quality Fixture

This directory contains three save states for slot `game1`:

- `regular/game1`: baseline starter save.
- `copy/game1`: exact copy of the baseline save.
- `modified/game1`: edited copy with measurable quality issues.

The `.dat` files are gzipped JSON bundles, matching the save container used by Shattered Pixel Dungeon.
The adjacent `.json` files are readable mirrors for inspection and diffing.

The modified save intentionally keeps the current depth and hero state valid, but marks depth 2 as
already generated without providing `depth2.dat`. The game should restore depth 1 normally, then fail
later if the player descends and the transition code tries to load the missing future level.
These changes are captured in `metrics.json` so the latent degradation is measurable.

## Transition Behavior

The first-floor upstairs ladder is intentionally stored as `REGULAR_ENTRANCE`, not `SURFACE`.
In the game code, a regular entrance computes its destination as the current depth minus one.
From floor 1, that sends the player to depth 0.

Depth 0 is outside the normal dungeon range, so `Dungeon.newLevel()` falls back to `DeadEndLevel`.
`DeadEndLevel` contains one regular entrance ladder. Taking it repeats the same depth-minus-one
calculation, producing floor -1, then -2, and so on.

The downstairs path is different. The save marks depth 2 as already generated in `generated_levels`,
but `modified/game1` does not contain `depth2.dat`. When the player descends, the game tries to load
that missing generated level instead of creating a new one, which triggers the delayed failure.
