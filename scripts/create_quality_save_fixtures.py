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
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "shattered-pixel-dungeon-3.0.2" / "save-fixtures" / "quality-issue-latent-clean"
VERSION_CODE = 833
SAVE_TIME_MS = int(datetime(2026, 5, 14, 0, 0, tzinfo=timezone.utc).timestamp() * 1000)


SCENARIOS = [
    {
        "slot": 1,
        "name": "missing_generated_depth",
        "seed_label": "UP-FLOOR-0-DOWN-MISSING-D2",
        "title": "Missing generated depth file",
        "summary": "Depth 2 is marked as generated, but `depth2.dat` is absent. Descending crashes later.",
    },
    {
        "slot": 2,
        "name": "same_floor_loop",
        "seed_label": "DOWN-RELOADS-FLOOR-1",
        "title": "Same-floor descent loop",
        "summary": "The down ladder points back to depth 1, so descending reloads the same floor.",
    },
    {
        "slot": 3,
        "name": "out_of_range_depth",
        "seed_label": "DOWN-TO-DEPTH-999",
        "title": "Out-of-range destination depth",
        "summary": "The down ladder points to depth 999, forcing the fallback `DeadEndLevel` path.",
    },
    {
        "slot": 4,
        "name": "unbound_exit_tile",
        "seed_label": "VISIBLE-DOWN-NO-TRANSITION",
        "title": "Exit tile without transition",
        "summary": "The visible down ladder tile has no transition, so it behaves like ordinary floor.",
    },
    {
        "slot": 5,
        "name": "invisible_transition",
        "seed_label": "HIDDEN-FLOOR-TILE-DESCENDS",
        "title": "Transition detached from visible ladder",
        "summary": "The visible down ladder does nothing, while an ordinary floor tile secretly descends.",
    },
    {
        "slot": 6,
        "name": "locked_level",
        "seed_label": "LOCKED-LEVEL-STAIRS-BLOCKED",
        "title": "Locked level with normal-looking ladders",
        "summary": "The floor is flagged locked, so visible transitions refuse to activate.",
    },
]


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


def regular_game(slot: int = 1, custom_seed: str = "QUALITY-BASELINE") -> dict:
    limited_drops = {name: 0 for name in LIMITED_DROPS}
    limited_drops["VELVET_POUCH"] = 1

    game = {
        "init_ver": VERSION_CODE,
        "version": VERSION_CODE,
        "seed": 56180302,
        "custom_seed": custom_seed,
        "daily": False,
        "daily_replay": False,
        "last_played": SAVE_TIME_MS + slot,
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


def transition(cell: int, kind: str, dest_depth: int, dest_type: str | None) -> dict:
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


def regular_depth(
    *,
    entrance_type: str = "REGULAR_ENTRANCE",
    entrance_dest_depth: int = 0,
    entrance_dest_type: str | None = "REGULAR_EXIT",
    exit_cell: int | None = 13,
    exit_dest_depth: int = 2,
    exit_dest_type: str | None = "REGULAR_ENTRANCE",
    hidden_exit_cell: int | None = None,
    locked: bool = False,
) -> dict:
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

    transitions = [
        transition(12, entrance_type, entrance_dest_depth, entrance_dest_type),
    ]
    if exit_cell is not None:
        transitions.append(transition(exit_cell, "REGULAR_EXIT", exit_dest_depth, exit_dest_type))
    if hidden_exit_cell is not None:
        transitions.append(transition(hidden_exit_cell, "REGULAR_EXIT", exit_dest_depth, exit_dest_type))

    return {
        "level": {
            "__className": "com.shatteredpixel.shatteredpixeldungeon.levels.SewerLevel",
            "version": VERSION_CODE,
            "width": 5,
            "height": 5,
            "map": level_map,
            "visited": visited,
            "mapped": mapped,
            "transitions": transitions,
            "locked": locked,
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


def modified_game(game: dict, scenario: dict) -> dict:
    result = copy.deepcopy(game)

    result["custom_seed"] = scenario["seed_label"]

    if scenario["name"] == "missing_generated_depth":
        # Latent corruption: the current depth remains valid, but the game will
        # later believe depth 2 has already been generated and try to load a
        # missing depth2.dat when the player descends.
        result["generated_levels"] = [1, 2]

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


def write_slot(base: Path, slot_number: int, game_data: dict, depth_data: dict) -> None:
    slot = base / f"game{slot_number}"
    write_bundle(slot / "game.dat", game_data)
    write_bundle(slot / "depth1.dat", depth_data)
    write_json(slot / "game.json", game_data)
    write_json(slot / "depth1.json", depth_data)


def branchless_depth_file_name(depth: int) -> str:
    return f"depth{depth}.dat"


def build_slot_metrics(
    scenario: dict,
    regular: dict,
    modified: dict,
    regular_path: Path,
    copy_path: Path,
    modified_path: Path,
    modified_depth: dict,
) -> dict:
    current_depth_file = f"depth{modified['depth']}.dat"
    modified_slot = modified_path.parent
    missing_generated_files = [
        branchless_depth_file_name(depth)
        for depth in modified["generated_levels"]
        if depth != modified["depth"] and not (modified_slot / branchless_depth_file_name(depth)).exists()
    ]
    transitions = modified_depth["level"]["transitions"]
    return {
        "name": scenario["name"],
        "custom_seed": modified["custom_seed"],
        "title": scenario["title"],
        "summary": scenario["summary"],
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
            "transition_data": {
                "transitions": transitions,
                "level_locked": modified_depth["level"]["locked"],
            },
            "generated_level_files": {
                "regular_generated_levels": regular["generated_levels"],
                "modified_generated_levels": modified["generated_levels"],
                "missing_generated_files": missing_generated_files,
            },
        },
}


def build_metrics(slot_metrics: list[dict]) -> dict:
    return {
        "slots": {
            f"game{index + 1}": metrics
            for index, metrics in enumerate(slot_metrics)
        },
    }


def depth_for_scenario(scenario: dict) -> dict:
    name = scenario["name"]
    if name == "missing_generated_depth":
        return regular_depth()
    if name == "same_floor_loop":
        return regular_depth(
            entrance_type="SURFACE",
            entrance_dest_type=None,
            exit_dest_depth=1,
            exit_dest_type="REGULAR_ENTRANCE",
        )
    if name == "out_of_range_depth":
        return regular_depth(
            entrance_type="SURFACE",
            entrance_dest_type=None,
            exit_dest_depth=999,
            exit_dest_type="REGULAR_ENTRANCE",
        )
    if name == "unbound_exit_tile":
        return regular_depth(
            entrance_type="SURFACE",
            entrance_dest_type=None,
            exit_cell=None,
        )
    if name == "invisible_transition":
        return regular_depth(
            entrance_type="SURFACE",
            entrance_dest_type=None,
            exit_cell=None,
            hidden_exit_cell=17,
        )
    if name == "locked_level":
        return regular_depth(
            entrance_type="SURFACE",
            entrance_dest_type=None,
            locked=True,
        )
    raise ValueError(f"Unknown scenario: {name}")


def write_readme(path: Path) -> None:
    path.write_text(
        "\n".join(
            [
                "# Game Save Quality Fixture",
                "",
                "This directory contains baseline, copied, and modified save states for slots `game1` through `game6`:",
                "",
                "- `regular/gameN`: baseline starter save for the matching slot.",
                "- `copy/gameN`: exact copy of that slot's baseline save.",
                "- `modified/gameN`: edited copy with a slot-specific latent quality issue.",
                "",
                "The `.dat` files are gzipped JSON bundles, matching the save container used by Shattered Pixel Dungeon.",
                "The adjacent `.json` files are readable mirrors for inspection and diffing.",
                "",
                "Each modified save keeps the current depth and hero state valid so slot preview and initial load can",
                "succeed. The faults are delayed until the player interacts with stairs or level transitions.",
                "The modified `custom_seed` value is a short behavior label for what the save is meant to demonstrate.",
                "The changes are captured in `metrics.json` so the latent degradation is measurable.",
                "",
                "## Slot Cases",
                "",
                *[
                    f"- `modified/game{scenario['slot']}` seed `{scenario['seed_label']}`: "
                    f"{scenario['title']} - {scenario['summary']}"
                    for scenario in SCENARIOS
                ],
                "",
                "## Case Design",
                "",
                "- `modified/game1` keeps `depth` at 1 and includes a valid `depth1.dat`, so the slot preview",
                "  and first load have a complete current floor. The latent fault is split across two transition",
                "  paths: the upstairs ladder is stored as `REGULAR_ENTRANCE`, which makes floor 1 go to depth 0;",
                "  the downstairs ladder points to depth 2 while `generated_levels` says depth 2 already exists,",
                "  but no `depth2.dat` is present. The game therefore passes initial save checks and only fails",
                "  after the player uses the downstairs transition.",
                "",
                "- `modified/game2` keeps the floor file complete but changes the down-ladder transition target",
                "  from depth 2 to depth 1. The transition remains a normal `REGULAR_EXIT`, so it looks like an",
                "  ordinary staircase interaction. Activating it reloads the same depth instead of advancing,",
                "  which creates a measurable progression loop without corrupting the initial load.",
                "",
                "- `modified/game3` keeps the current depth valid and changes only the down-ladder destination",
                "  to depth 999. Because the bad value is in transition metadata rather than the current save",
                "  header, loading starts normally. The fault appears only when the player descends and the",
                "  dungeon code has to resolve an impossible destination depth.",
                "",
                "- `modified/game4` leaves the visual exit tile in the map but removes the matching transition",
                "  entry. The floor can be loaded and displayed because the map itself is valid. The quality",
                "  issue is delayed until interaction: the player sees a down ladder, but the game has no",
                "  transition bound to that cell.",
                "",
                "- `modified/game5` intentionally separates visual presentation from transition metadata.",
                "  The visible down-ladder tile has no transition, while cell 17, which looks like ordinary",
                "  floor, contains a hidden `REGULAR_EXIT` transition. This keeps save parsing and rendering",
                "  valid while making movement behavior disagree with what the map shows.",
                "",
                "- `modified/game6` leaves both visible ladder transitions structurally normal but marks the",
                "  level as `locked`. The save loads because `locked` is a valid level flag. The issue appears",
                "  during play, when transition activation is refused even though the ladders look usable.",
                "",
                "## Transition Behavior",
                "",
                "In `modified/game1`, the first-floor upstairs ladder is intentionally stored as `REGULAR_ENTRANCE`,",
                "not `SURFACE`. In the game code, a regular entrance computes its destination as the current depth",
                "minus one. From floor 1, that sends the player to depth 0.",
                "",
                "Depth 0 is outside the normal dungeon range, so `Dungeon.newLevel()` falls back to `DeadEndLevel`.",
                "`DeadEndLevel` contains one regular entrance ladder. Taking it repeats the same depth-minus-one",
                "calculation, producing floor -1, then -2, and so on.",
                "",
                "The `modified/game1` downstairs path is different. The save marks depth 2 as already generated in",
                "`generated_levels`, but `modified/game1` does not contain `depth2.dat`. When the player descends,",
                "the game tries to load that missing generated level instead of creating a new one, which triggers",
                "the delayed failure.",
                "",
            ]
        ),
        encoding="utf-8",
    )


def main() -> None:
    if OUT_DIR.exists():
        raise SystemExit(f"{OUT_DIR} already exists; move or remove it before regenerating fixtures.")

    regular_dir = OUT_DIR / "regular"
    copy_dir = OUT_DIR / "copy"
    modified_dir = OUT_DIR / "modified"

    slot_metrics = []
    for scenario in SCENARIOS:
        slot_number = scenario["slot"]
        regular = regular_game(slot_number, f"BASELINE-SLOT-{slot_number}-NO-FAULT")
        regular_depth_data = regular_depth()
        modified = modified_game(regular, scenario)
        modified_depth_data = depth_for_scenario(scenario)

        write_slot(regular_dir, slot_number, regular, regular_depth_data)
        write_slot(copy_dir, slot_number, copy.deepcopy(regular), copy.deepcopy(regular_depth_data))
        write_slot(modified_dir, slot_number, modified, modified_depth_data)

        slot_metrics.append(
            build_slot_metrics(
                scenario,
                regular,
                modified,
                regular_dir / f"game{slot_number}" / "game.dat",
                copy_dir / f"game{slot_number}" / "game.dat",
                modified_dir / f"game{slot_number}" / "game.dat",
                modified_depth_data,
            )
        )

    metrics = build_metrics(slot_metrics)
    write_json(OUT_DIR / "metrics.json", metrics)
    write_readme(OUT_DIR / "README.md")
    print(f"Created save fixtures in {OUT_DIR.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
