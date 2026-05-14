#!/usr/bin/env python3
"""Create game-save fixtures for quality degradation testing.

The generated files use Shattered Pixel Dungeon's save container shape:
gzipped JSON bundles named game.dat/depthN.dat inside a game slot folder.
"""

from __future__ import annotations

import copy
import gzip
import hashlib
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "shattered-pixel-dungeon-3.0.2" / "save-fixtures" / "quality-issue-latent-clean"
VERSION_CODE = 833
SAVE_TIME_MS = int(datetime(2026, 5, 14, 0, 0, tzinfo=timezone.utc).timestamp() * 1000)


LIMITED_DROPS = [
    "STRENGTH_POTIONS",
    "UPGRADE_SCROLLS",
    "ARCANE_STYLI",
    "ENCH_STONE",
    "INT_STONE",
    "TRINKET_CATA",
    "LAB_ROOM",
    "SWARM_HP",
    "NECRO_HP",
    "BAT_HP",
    "WARLOCK_HP",
    "COOKING_HP",
    "BLANDFRUIT_SEED",
    "SLIME_WEP",
    "SKELE_WEP",
    "THEIF_MISC",
    "GUARD_ARM",
    "SHAMAN_WAND",
    "DM200_EQUIP",
    "GOLEM_EQUIP",
    "VELVET_POUCH",
    "SCROLL_HOLDER",
    "POTION_BANDOLIER",
    "MAGICAL_HOLSTER",
    "LORE_SEWERS",
    "LORE_PRISON",
    "LORE_CAVES",
    "LORE_CITY",
    "LORE_HALLS",
]


def armor() -> dict:
    return {
        "__className": "com.shatteredpixel.shatteredpixeldungeon.items.armor.ClothArmor",
        "quantity": 1,
        "level": 0,
        "levelKnown": True,
        "cursed": False,
        "cursedKnown": True,
        "kept_lost": False,
        "uses_left_to_id": 0,
        "available_uses": 0,
        "glyph_hardened": False,
        "curse_infusion_bonus": False,
        "mastery_potion_bonus": False,
        "augment": "NONE",
    }


def hero() -> dict:
    return {
        "__className": "com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero",
        "time": 0.0,
        "id": 1,
        "pos": 12,
        "HP": 20,
        "HT": 20,
        "SHLD": 0,
        "buffs": [],
        "class": "WARRIOR",
        "subClass": "NONE",
        "attackSkill": 10,
        "defenseSkill": 5,
        "STR": 10,
        "lvl": 1,
        "exp": 0,
        "htboost": 0,
        "inventory": [],
        "armor": armor(),
    }


def stats() -> dict:
    return {
        "score": 0,
        "maxDepth": 1,
        "maxAscent": 0,
        "enemiesSlain": 0,
        "foodEaten": 0,
        "potionsCooked": 0,
        "priranhas": 0,
        "ankhsUsed": 0,
        "item_types_discovered": [],
        "prog_score": 0,
        "item_val": 0,
        "tres_score": 0,
        "expl_score": 0,
        "boss_scores": [0, 0, 0, 0, 0],
        "tot_boss": 0,
        "quest_scores": [0, 0, 0, 0, 0],
        "tot_quest": 0,
        "win_mult": 1.0,
        "chal_mult": 1.0,
        "total_score": 0,
        "upgradesUsed": 0,
        "sneakAttacks": 0,
        "thrownAssists": 0,
        "spawnersAlive": 0,
        "duration": 0.0,
        "qualifiedForNoKilling": False,
        "qualifiedForBossRemainsBadge": False,
        "qualifiedForBossChallengeBadge": False,
        "amuletObtained": False,
        "won": False,
        "ascended": False,
    }


def regular_game() -> dict:
    limited_drops = {name: 0 for name in LIMITED_DROPS}
    limited_drops["VELVET_POUCH"] = 1

    game = {
        "init_ver": VERSION_CODE,
        "version": VERSION_CODE,
        "seed": 56180302,
        "custom_seed": "QUALITY-BASELINE",
        "daily": False,
        "daily_replay": False,
        "last_played": SAVE_TIME_MS,
        "challenges": 0,
        "mobs_to_champion": -1,
        "hero": hero(),
        "depth": 1,
        "branch": 0,
        "gold": 0,
        "energy": 0,
        "placeholders": [],
        "placements": [False, False, False, False, False, False],
        "limited_drops": limited_drops,
        "chapters": [],
        "quests": {},
        "special_rooms": [],
        "pit_needed": 0,
        "secret_rooms": [],
        "region_secrets": [],
        "generated_levels": [1],
        "nextid": 2,
        "badges": {
            "badges": [],
        },
        "records": [],
        "next_custom_id": 0,
    }
    game.update(stats())
    return game


def transition(cell: int, kind: str, dest_depth: int, dest_type: str) -> dict:
    x = cell % 5
    y = cell // 5
    return {
        "__className": "com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition",
        "left": x,
        "top": y,
        "right": x,
        "bottom": y,
        "center": cell,
        "type": kind,
        "dest_depth": dest_depth,
        "dest_branch": 0,
        "dest_type": dest_type,
    }


def regular_depth() -> dict:
    wall = 4
    empty = 1
    entrance = 7
    exit_tile = 8
    level_map = [
        wall,
        wall,
        wall,
        wall,
        wall,
        wall,
        empty,
        empty,
        empty,
        wall,
        wall,
        empty,
        entrance,
        exit_tile,
        wall,
        wall,
        empty,
        empty,
        empty,
        wall,
        wall,
        wall,
        wall,
        wall,
        wall,
    ]
    visited = [False] * 25
    mapped = [False] * 25
    visited[12] = True
    mapped[12] = True

    return {
        "level": {
            "__className": "com.shatteredpixel.shatteredpixeldungeon.levels.SewerLevel",
            "version": VERSION_CODE,
            "width": 5,
            "height": 5,
            "map": level_map,
            "visited": visited,
            "mapped": mapped,
            "transitions": [
                transition(12, "REGULAR_ENTRANCE", 0, "REGULAR_EXIT"),
                transition(13, "REGULAR_EXIT", 2, "REGULAR_ENTRANCE"),
            ],
            "locked": False,
            "heaps": [],
            "plants": [],
            "traps": [],
            "customTiles": [],
            "customWalls": [],
            "mobs": [],
            "blobs": [],
            "feeling": "NONE",
            "mobs_to_spawn": [],
            "rooms": [],
        }
    }


def modified_game(game: dict) -> dict:
    result = copy.deepcopy(game)

    # Latent corruption: the current depth remains valid, but the game will
    # later believe depth 2 has already been generated and try to load a
    # missing depth2.dat when the player descends.
    result["generated_levels"] = [1, 2]
    result["custom_seed"] = "QUALITY-LATENT"
    return result


def write_bundle(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(data, separators=(",", ":"), ensure_ascii=True).encode("utf-8")
    with gzip.GzipFile(filename=str(path), mode="wb", mtime=0) as archive:
        archive.write(payload)


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, sort_keys=True, ensure_ascii=True) + "\n", encoding="utf-8")


def file_info(path: Path) -> dict:
    data = path.read_bytes()
    return {
        "path": str(path.relative_to(ROOT)),
        "bytes": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
    }


def write_slot(base: Path, game_data: dict, depth_data: dict) -> None:
    slot = base / "game1"
    write_bundle(slot / "game.dat", game_data)
    write_bundle(slot / "depth1.dat", depth_data)
    write_json(slot / "game.json", game_data)
    write_json(slot / "depth1.json", depth_data)


def build_metrics(regular: dict, modified: dict, regular_path: Path, copy_path: Path, modified_path: Path) -> dict:
    current_depth_file = f"depth{modified['depth']}.dat"
    latent_depth = 2
    latent_depth_file = f"depth{latent_depth}.dat"
    modified_slot = modified_path.parent
    return {
        "regular_game_dat": file_info(regular_path),
        "copied_game_dat": file_info(copy_path),
        "modified_game_dat": file_info(modified_path),
        "copy_matches_regular": hashlib.sha256(regular_path.read_bytes()).hexdigest()
        == hashlib.sha256(copy_path.read_bytes()).hexdigest(),
        "quality_issue_deltas": {
            "initial_load_depth": {
                "regular": regular["depth"],
                "modified": modified["depth"],
                "current_depth_file_exists": (modified_slot / current_depth_file).exists(),
            },
            "visible_hero_state": {
                "hp": {
                    "regular": regular["hero"]["HP"],
                    "modified": modified["hero"]["HP"],
                },
                "strength": {
                    "regular": regular["hero"]["STR"],
                    "modified": modified["hero"]["STR"],
                },
                "armor_present": {
                    "regular": "armor" in regular["hero"],
                    "modified": "armor" in modified["hero"],
                },
            },
            "latent_transition_fault": {
                "regular_generated_levels": regular["generated_levels"],
                "modified_generated_levels": modified["generated_levels"],
                "trigger": "descend from depth 1 to depth 2",
                "expected_missing_file": latent_depth_file,
                "missing_file_exists": (modified_slot / latent_depth_file).exists(),
            },
        },
    }


def write_readme(path: Path) -> None:
    path.write_text(
        "\n".join(
            [
                "# Game Save Quality Fixture",
                "",
                "This directory contains three save states for slot `game1`:",
                "",
                "- `regular/game1`: baseline starter save.",
                "- `copy/game1`: exact copy of the baseline save.",
                "- `modified/game1`: edited copy with measurable quality issues.",
                "",
                "The `.dat` files are gzipped JSON bundles, matching the save container used by Shattered Pixel Dungeon.",
                "The adjacent `.json` files are readable mirrors for inspection and diffing.",
                "",
                "The modified save intentionally keeps the current depth and hero state valid, but marks depth 2 as",
                "already generated without providing `depth2.dat`. The game should restore depth 1 normally, then fail",
                "later if the player descends and the transition code tries to load the missing future level.",
                "These changes are captured in `metrics.json` so the latent degradation is measurable.",
                "",
                "## Transition Behavior",
                "",
                "The first-floor upstairs ladder is intentionally stored as `REGULAR_ENTRANCE`, not `SURFACE`.",
                "In the game code, a regular entrance computes its destination as the current depth minus one.",
                "From floor 1, that sends the player to depth 0.",
                "",
                "Depth 0 is outside the normal dungeon range, so `Dungeon.newLevel()` falls back to `DeadEndLevel`.",
                "`DeadEndLevel` contains one regular entrance ladder. Taking it repeats the same depth-minus-one",
                "calculation, producing floor -1, then -2, and so on.",
                "",
                "The downstairs path is different. The save marks depth 2 as already generated in `generated_levels`,",
                "but `modified/game1` does not contain `depth2.dat`. When the player descends, the game tries to load",
                "that missing generated level instead of creating a new one, which triggers the delayed failure.",
                "",
            ]
        ),
        encoding="utf-8",
    )


def main() -> None:
    if OUT_DIR.exists():
        raise SystemExit(f"{OUT_DIR} already exists; move or remove it before regenerating fixtures.")

    regular = regular_game()
    depth = regular_depth()
    modified = modified_game(regular)

    regular_dir = OUT_DIR / "regular"
    copy_dir = OUT_DIR / "copy"
    modified_dir = OUT_DIR / "modified"

    write_slot(regular_dir, regular, depth)
    shutil.copytree(regular_dir, copy_dir)
    write_slot(modified_dir, modified, depth)

    metrics = build_metrics(
        regular,
        modified,
        regular_dir / "game1" / "game.dat",
        copy_dir / "game1" / "game.dat",
        modified_dir / "game1" / "game.dat",
    )
    write_json(OUT_DIR / "metrics.json", metrics)
    write_readme(OUT_DIR / "README.md")
    print(f"Created save fixtures in {OUT_DIR.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
