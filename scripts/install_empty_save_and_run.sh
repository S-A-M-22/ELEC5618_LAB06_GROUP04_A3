#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/install_empty_save_and_run.sh [--no-launch] [--include-settings]

Backs up Shattered Pixel Dungeon save/progress files, leaves the save directory
empty of active runs, then starts the desktop game.

By default this preserves settings.xml and keybinds.dat. Use --include-settings
to back those up too for a fully fresh local state.

Environment overrides:
  SAVE_DIR  Destination save directory.
EOF
}

launch_game=1
include_settings=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --no-launch)
      launch_game=0
      ;;
    --include-settings)
      include_settings=1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unexpected argument: $1" >&2
      usage >&2
      exit 2
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
stamp="$(date +%Y%m%d-%H%M%S)"
backup_dir="$save_dir.empty-backup.$stamp"

mkdir -p "$save_dir"

items=(
  game1 game2 game3 game4 game5 game6
  rankings.dat badges.dat journal.dat bones.dat
)

if [ "$include_settings" -eq 1 ]; then
  items+=(settings.xml keybinds.dat)
fi

echo "Save directory: $save_dir"
echo "Backup directory: $backup_dir"

backed_up=0
for item in "${items[@]}"; do
  current="$save_dir/$item"
  if [ -e "$current" ]; then
    mkdir -p "$backup_dir"
    mv "$current" "$backup_dir/$item"
    echo "Backed up $item"
    backed_up=1
  fi
done

if [ "$backed_up" -eq 0 ]; then
  echo "No active save/progress files were found."
  rmdir "$backup_dir" 2>/dev/null || true
else
  echo "Old save/progress data is in: $backup_dir"
fi

echo "Active save slots are now empty."

if [ "$launch_game" -eq 0 ]; then
  echo "Launch skipped because --no-launch was supplied."
  exit 0
fi

cd "$game_root"

if [ -z "${JAVA_HOME:-}" ] && [ -d /Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home ]; then
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home
fi

echo "Starting Shattered Pixel Dungeon with ./gradlew :desktop:debug"
exec ./gradlew :desktop:debug
