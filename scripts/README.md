# Scripts

These scripts create and install save fixtures for the checksum benchmark.

## `create_quality_save_fixtures.py`

Flow:

1. Detect whether the target repo has save checksum code.
2. Create three save slots:
   - `game1`: original valid save.
   - `game2`: `DOWN-TO-DEPTH-999`.
   - `game3`: `UP-FLOOR-0-DOWN-MISSING-D2`.
3. If checksum code exists, write `__checksum` fields and make `game2`/`game3` fail on `depth1.dat`.
4. If checksum code does not exist, write original-format saves without `__checksum`.
5. Write readable `.json` mirrors and `metrics.json`.

## `install_modified_save_and_run.sh`

Flow:

1. Detect whether the current repo has checksum code.
2. Generate fresh fixtures in a temporary folder.
3. Install only `game1`, `game2`, and `game3`.
4. Back up existing numeric `gameN` save folders first.
5. Print installed save details.
6. Start the desktop game, unless `--no-launch` is used.

Use:

```bash
scripts/install_modified_save_and_run.sh
```

Dry run without launching:

```bash
scripts/install_modified_save_and_run.sh --no-launch
```

## `install_empty_save_and_run.sh`

Flow:

1. Find the Shattered Pixel Dungeon save directory.
2. Back up numeric `gameN` save folders.
3. Back up progress files like `rankings.dat`, `badges.dat`, `journal.dat`, and `bones.dat`.
4. Keep settings by default.
5. Start the desktop game, unless `--no-launch` is used.

Use:

```bash
scripts/install_empty_save_and_run.sh
```

Fully fresh state including settings:

```bash
scripts/install_empty_save_and_run.sh --include-settings
```
