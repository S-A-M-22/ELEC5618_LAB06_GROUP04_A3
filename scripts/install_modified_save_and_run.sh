#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/install_modified_save_and_run.sh [--no-launch] [fixture_dir]

Installs the newest modified Shattered Pixel Dungeon save fixture into the
macOS save directory, backs up existing game1-game6 saves, verifies the
installed save, then starts the desktop game.

Environment overrides:
  SAVE_DIR  Destination save directory.
  FIXTURE   Source fixture directory, e.g. .../modified/game1.
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
save_dir="${SAVE_DIR:-$HOME/Library/Application Support/Shattered Pixel Dungeon}"

find_latest_fixture() {
  python3 - "$game_root" <<'PY'
import pathlib
import sys

game_root = pathlib.Path(sys.argv[1])
save_fixtures = game_root / "save-fixtures"
candidates = [
    path.parent
    for path in save_fixtures.glob("**/modified/game1/game.dat")
    if path.is_file()
]

if not candidates:
    raise SystemExit(f"No modified game1 fixtures found under {save_fixtures}")

latest = max(candidates, key=lambda path: (path / "game.dat").stat().st_mtime)
print(latest)
PY
}

if [ -n "$fixture_arg" ]; then
  fixture="$fixture_arg"
elif [ -n "${FIXTURE:-}" ]; then
  fixture="$FIXTURE"
else
  fixture="$(find_latest_fixture)"
fi

case "$fixture" in
  /*) ;;
  *) fixture="$repo_root/$fixture" ;;
esac

if [ ! -d "$fixture" ]; then
  echo "Fixture directory does not exist: $fixture" >&2
  exit 1
fi

if [ ! -f "$fixture/game.dat" ]; then
  echo "Fixture is missing game.dat: $fixture/game.dat" >&2
  exit 1
fi

python3 - "$fixture/game.dat" <<'PY'
import gzip
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
try:
    data = json.loads(gzip.open(path, "rt", encoding="utf-8").read())
except Exception as exc:
    raise SystemExit(f"Could not read fixture game.dat as gzipped JSON: {exc}")

hero = data.get("hero", {})
print(
    "fixture:",
    "depth", data.get("depth"),
    "hp", hero.get("HP"),
    "str", hero.get("STR"),
    "armor", "armor" in hero,
)
PY

stamp="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$save_dir"

echo "Installing fixture: $fixture"
echo "Save directory: $save_dir"

for slot in 1 2 3 4 5 6; do
  current="$save_dir/game$slot"
  if [ -d "$current" ]; then
    backup="$current.backup.$stamp"
    mv "$current" "$backup"
    echo "Backed up game$slot to $(basename "$backup")"
  fi
done

cp -R "$fixture" "$save_dir/game1"

python3 - "$save_dir/game1/game.dat" <<'PY'
import gzip
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
data = json.loads(gzip.open(path, "rt", encoding="utf-8").read())
hero = data["hero"]
depth_file = path.parent / f"depth{data['depth']}.dat"
generated_files = [
    path.parent / f"depth{depth}.dat"
    for depth in data.get("generated_levels", [])
    if isinstance(depth, int) and depth > 0 and depth < 1000
]
missing_generated_files = [level_file.name for level_file in generated_files if not level_file.exists()]

print(
    "installed modified save:",
    "depth", data["depth"],
    "hp", hero["HP"],
    "str", hero["STR"],
    "armor", "armor" in hero,
)
print("current depth file exists:", depth_file.exists(), f"({depth_file.name})")
print("generated levels:", data.get("generated_levels", []))
print("missing generated level files:", missing_generated_files)
PY

if [ "$launch_game" -eq 0 ]; then
  echo "Launch skipped because --no-launch was supplied."
  exit 0
fi

cd "$game_root"
echo "Starting Shattered Pixel Dungeon with ./gradlew desktop:debug"
exec ./gradlew desktop:debug
