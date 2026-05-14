# Game Save Quality Fixture

This directory contains baseline, copied, and modified save states for slots `game1` through `game6`:

- `regular/gameN`: baseline starter save for the matching slot.
- `copy/gameN`: exact copy of that slot's baseline save.
- `modified/gameN`: edited copy with a slot-specific latent quality issue.

The `.dat` files are gzipped JSON bundles, matching the save container used by Shattered Pixel Dungeon.
The adjacent `.json` files are readable mirrors for inspection and diffing.

Each modified save keeps the current depth and hero state valid so slot preview and initial load can
succeed. The faults are delayed until the player interacts with stairs or level transitions.
The modified `custom_seed` value is a short behavior label for what the save is meant to demonstrate.
The changes are captured in `metrics.json` so the latent degradation is measurable.

## Slot Cases

- `modified/game1` seed `UP-FLOOR-0-DOWN-MISSING-D2`: Missing generated depth file - Depth 2 is marked as generated, but `depth2.dat` is absent. Descending crashes later.
- `modified/game2` seed `DOWN-RELOADS-FLOOR-1`: Same-floor descent loop - The down ladder points back to depth 1, so descending reloads the same floor.
- `modified/game3` seed `DOWN-TO-DEPTH-999`: Out-of-range destination depth - The down ladder points to depth 999, forcing the fallback `DeadEndLevel` path.
- `modified/game4` seed `VISIBLE-DOWN-NO-TRANSITION`: Exit tile without transition - The visible down ladder tile has no transition, so it behaves like ordinary floor.
- `modified/game5` seed `HIDDEN-FLOOR-TILE-DESCENDS`: Transition detached from visible ladder - The visible down ladder does nothing, while an ordinary floor tile secretly descends.
- `modified/game6` seed `LOCKED-LEVEL-STAIRS-BLOCKED`: Locked level with normal-looking ladders - The floor is flagged locked, so visible transitions refuse to activate.

## Case Design

- `modified/game1` keeps `depth` at 1 and includes a valid `depth1.dat`, so the slot preview
  and first load have a complete current floor. The latent fault is split across two transition
  paths: the upstairs ladder is stored as `REGULAR_ENTRANCE`, which makes floor 1 go to depth 0;
  the downstairs ladder points to depth 2 while `generated_levels` says depth 2 already exists,
  but no `depth2.dat` is present. The game therefore passes initial save checks and only fails
  after the player uses the downstairs transition.

- `modified/game2` keeps the floor file complete but changes the down-ladder transition target
  from depth 2 to depth 1. The transition remains a normal `REGULAR_EXIT`, so it looks like an
  ordinary staircase interaction. Activating it reloads the same depth instead of advancing,
  which creates a measurable progression loop without corrupting the initial load.

- `modified/game3` keeps the current depth valid and changes only the down-ladder destination
  to depth 999. Because the bad value is in transition metadata rather than the current save
  header, loading starts normally. The fault appears only when the player descends and the
  dungeon code has to resolve an impossible destination depth.

- `modified/game4` leaves the visual exit tile in the map but removes the matching transition
  entry. The floor can be loaded and displayed because the map itself is valid. The quality
  issue is delayed until interaction: the player sees a down ladder, but the game has no
  transition bound to that cell.

- `modified/game5` intentionally separates visual presentation from transition metadata.
  The visible down-ladder tile has no transition, while cell 17, which looks like ordinary
  floor, contains a hidden `REGULAR_EXIT` transition. This keeps save parsing and rendering
  valid while making movement behavior disagree with what the map shows.

- `modified/game6` leaves both visible ladder transitions structurally normal but marks the
  level as `locked`. The save loads because `locked` is a valid level flag. The issue appears
  during play, when transition activation is refused even though the ladders look usable.

## Transition Behavior

In `modified/game1`, the first-floor upstairs ladder is intentionally stored as `REGULAR_ENTRANCE`,
not `SURFACE`. In the game code, a regular entrance computes its destination as the current depth
minus one. From floor 1, that sends the player to depth 0.

Depth 0 is outside the normal dungeon range, so `Dungeon.newLevel()` falls back to `DeadEndLevel`.
`DeadEndLevel` contains one regular entrance ladder. Taking it repeats the same depth-minus-one
calculation, producing floor -1, then -2, and so on.

The `modified/game1` downstairs path is different. The save marks depth 2 as already generated in
`generated_levels`, but `modified/game1` does not contain `depth2.dat`. When the player descends,
the game tries to load that missing generated level instead of creating a new one, which triggers
the delayed failure.
