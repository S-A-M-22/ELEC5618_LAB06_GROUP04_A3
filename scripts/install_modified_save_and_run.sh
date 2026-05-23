#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/install_modified_save_and_run.sh [--no-launch] [fixture_dir]

Installs only the three expected modified Shattered Pixel Dungeon fixtures
(`game1`, `game2`, and `game3`) into the save directory, backs up existing
numeric gameN saves, verifies the installed fixtures, then starts the desktop game.

Environment overrides:
  SAVE_DIR  Destination save directory.
  FIXTURE   Source fixture directory, e.g. .../modified or .../modified/game1.
EOF
}

launch_game=1
fixture_arg=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --no-launch)
      launch_game=0
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      if [ -n "$fixture_arg" ]; then
        echo "Unexpected extra argument: $1" >&2
        usage >&2
        exit 2
      fi
      fixture_arg="$1"
      ;;
  esac
  shift
done

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
game_root="$repo_root/shattered-pixel-dungeon-3.0.2"

default_save_dir() {
  case "$(uname -s)" in
    Darwin)
      printf '%s\n' "$HOME/Library/Application Support/Shattered Pixel Dungeon"
      ;;
    Linux)
      printf '%s\n' "${XDG_DATA_HOME:-$HOME/.local/share}/.shatteredpixel/shattered-pixel-dungeon"
      ;;
    MINGW*|MSYS*|CYGWIN*)
      printf '%s\n' "$HOME/AppData/Roaming/.shatteredpixel/Shattered Pixel Dungeon"
      ;;
    *)
      printf '%s\n' "$HOME/Library/Application Support/Shattered Pixel Dungeon"
      ;;
  esac
}

save_dir="${SAVE_DIR:-$(default_save_dir)}"

find_latest_fixture() {
  python3 - "$game_root" <<'PY'
import pathlib
import sys

game_root = pathlib.Path(sys.argv[1])
save_fixtures = game_root / "save-fixtures"
candidates = [
    path.parent.parent
    for path in save_fixtures.glob("**/modified/game1/game.dat")
    if path.is_file()
]

if not candidates:
    raise SystemExit(f"No modified fixture sets found under {save_fixtures}")

latest = max(
    candidates,
    key=lambda path: max((game / "game.dat").stat().st_mtime for game in path.glob("game*/") if (game / "game.dat").is_file()),
)
print(latest)
PY
}

checksum_code_present() {
  bundle_java="$game_root/SPD-classes/src/main/java/com/watabou/utils/Bundle.java"
  [ -f "$bundle_java" ] \
    && grep -q 'private static final String CHECKSUM = "__checksum"' "$bundle_java" \
    && grep -q 'verifyChecksum(jsonObject)' "$bundle_java"
}

generate_fixture() {
  python3 - "$repo_root" "$1" <<'PY'
import importlib.util
import pathlib
import sys
import tempfile

sys.dont_write_bytecode = True

repo_root = pathlib.Path(sys.argv[1])
emit_checksums = sys.argv[2] == "1"
script = repo_root / "scripts" / "create_quality_save_fixtures.py"

spec = importlib.util.spec_from_file_location("quality_fixture_generator", script)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
module.EMIT_BUNDLE_CHECKSUMS = emit_checksums

fixture_root = pathlib.Path(tempfile.mkdtemp(prefix="spd-quality-fixtures-"))
module.ROOT = fixture_root
module.OUT_DIR = fixture_root / "quality-issue-latent-clean"
module.main()

print(module.OUT_DIR / "modified")
PY
}

if checksum_code_present; then
  echo "Checksum-enabled code detected; generating fresh stale-checksum fixtures."
  fixture="$(generate_fixture 1 | tail -n 1)"
elif [ -n "$fixture_arg" ]; then
  fixture="$fixture_arg"
elif [ -n "${FIXTURE:-}" ]; then
  fixture="$FIXTURE"
else
  echo "Checksum code not detected; generating original-format fixtures."
  fixture="$(generate_fixture 0 | tail -n 1)"
fi

case "$fixture" in
  /*) ;;
  *) fixture="$repo_root/$fixture" ;;
esac

if [ ! -d "$fixture" ]; then
  echo "Fixture directory does not exist: $fixture" >&2
  exit 1
fi

source_slots="$(
  python3 - "$fixture" <<'PY'
import pathlib
import sys

fixture = pathlib.Path(sys.argv[1])
allowed_slots = ["game1", "game2", "game3"]

if (fixture / "game.dat").is_file():
    if fixture.name not in allowed_slots:
        raise SystemExit(f"Single-slot fixture must be one of {', '.join(allowed_slots)}: {fixture}")
    slots = [fixture]
else:
    slots = [fixture / slot_name for slot_name in allowed_slots]
    missing = [slot.name for slot in slots if not (slot / "game.dat").is_file()]
    if missing:
        raise SystemExit(f"Fixture set must contain {', '.join(allowed_slots)}; missing {', '.join(missing)}")

if not slots:
    raise SystemExit(f"No game slot fixtures found in {fixture}")

for slot in slots:
    if not slot.name.startswith("game") or not slot.name[4:].isdigit():
        raise SystemExit(f"Slot fixture must be named gameN: {slot}")
    print(slot)
PY
)"

SOURCE_SLOTS="$source_slots" python3 - <<'PY'
import gzip
import json
import os
import pathlib

for raw_path in os.environ["SOURCE_SLOTS"].splitlines():
    slot = pathlib.Path(raw_path)
    path = slot / "game.dat"
    try:
        data = json.loads(gzip.open(path, "rt", encoding="utf-8").read())
    except Exception as exc:
        raise SystemExit(f"Could not read {path} as gzipped JSON: {exc}")

    hero = data.get("hero", {})
    print(
        "fixture",
        slot.name + ":",
        "seed", data.get("custom_seed"),
        "depth", data.get("depth"),
        "hp", hero.get("HP"),
        "str", hero.get("STR"),
        "armor", "armor" in hero,
    )
PY

stamp="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$save_dir"

echo "Installing fixture set: $fixture"
echo "Save directory: $save_dir"

for current in "$save_dir"/game*; do
  [ -d "$current" ] || continue
  slot_name="$(basename "$current")"
  slot_number="${slot_name#game}"
  case "$slot_number" in
    ""|*[!0-9]*)
      continue
      ;;
  esac

  backup="$current.backup.$stamp"
  mv "$current" "$backup"
  echo "Backed up $slot_name to $(basename "$backup")"
done

while IFS= read -r slot; do
  [ -n "$slot" ] || continue
  cp -R "$slot" "$save_dir/$(basename "$slot")"
  echo "Installed $(basename "$slot")"
done <<EOF
$source_slots
EOF

SOURCE_SLOTS="$source_slots" python3 - "$save_dir" <<'PY'
import gzip
import json
import os
import pathlib
import sys

save_dir = pathlib.Path(sys.argv[1])
slot_names = [
    pathlib.Path(raw_path).name
    for raw_path in os.environ["SOURCE_SLOTS"].splitlines()
    if raw_path.strip()
]

for slot_name in slot_names:
    slot = save_dir / slot_name
    path = slot / "game.dat"
    if not path.is_file():
        continue

    data = json.loads(gzip.open(path, "rt", encoding="utf-8").read())
    hero = data["hero"]
    depth_file = slot / f"depth{data['depth']}.dat"
    generated_files = [
        slot / f"depth{depth}.dat"
        for depth in data.get("generated_levels", [])
        if isinstance(depth, int) and depth > 0 and depth < 1000
    ]
    missing_generated_files = [level_file.name for level_file in generated_files if not level_file.exists()]

    print(
        "installed",
        slot.name + ":",
        "seed", data.get("custom_seed"),
        "depth", data["depth"],
        "hp", hero["HP"],
        "str", hero["STR"],
        "armor", "armor" in hero,
    )
    print("  current depth file exists:", depth_file.exists(), f"({depth_file.name})")
    print("  generated levels:", data.get("generated_levels", []))
    print("  missing generated level files:", missing_generated_files)
PY

if [ "$launch_game" -eq 0 ]; then
  echo "Launch skipped because --no-launch was supplied."
  exit 0
fi

cd "$game_root"
echo "Starting Shattered Pixel Dungeon with ./gradlew desktop:debug"
exec ./gradlew desktop:debug
