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
        "name": "original_game",
        "seed_label": "ORIGINAL-GAME",
        "title": "Original game",
        "summary": "A clean first-floor starter save with valid bundle checksums.",
    },
    {
        "slot": 2,
        "name": "down_to_depth_999",
        "seed_label": "DOWN-TO-DEPTH-999",
        "title": "Down ladder to depth 999",
        "summary": "Only the down ladder points to depth 999, while the depth file keeps the original stored checksum.",
    },
    {
        "slot": 3,
        "name": "up_floor_0_down_missing_d2",
        "seed_label": "UP-FLOOR-0-DOWN-MISSING-D2",
        "title": "Up floor 0 and missing depth 2",
        "summary": "The save uses a descriptive seed and marks depth 2 as generated; the upstairs ladder targets floor 0 while the depth file keeps the original stored checksum.",
    },
]


STALE_CHECKSUM_SCENARIOS = {
    "down_to_depth_999",
    "up_floor_0_down_missing_d2",
}


CHECKSUM_KEY = "__checksum"
CHECKSUM_PREFIX = "sha256:"
EMIT_BUNDLE_CHECKSUMS: bool | None = None


def target_has_checksum_code(root: Path | None = None) -> bool:
    root = ROOT if root is None else root
    bundle_java = (
        root
        / "shattered-pixel-dungeon-3.0.2"
        / "SPD-classes"
        / "src"
        / "main"
        / "java"
        / "com"
        / "watabou"
        / "utils"
        / "Bundle.java"
    )
    if not bundle_java.is_file():
        return False
    source = bundle_java.read_text(encoding="utf-8", errors="ignore")
    return (
        'private static final String CHECKSUM = "__checksum"' in source
        and "verifyChecksum(jsonObject)" in source
    )


def emit_bundle_checksums() -> bool:
    if EMIT_BUNDLE_CHECKSUMS is not None:
        return EMIT_BUNDLE_CHECKSUMS
    return target_has_checksum_code()


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
        "generated_levels": [1, 2],
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
    entrance_type: str = "SURFACE",
    entrance_dest_depth: int = 0,
    entrance_dest_type: str | None = None,
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
    if exit_cell is None:
        level_map[13] = empty

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

    if scenario["name"] == "up_floor_0_down_missing_d2":
        # Latent corruption: the current depth remains valid, but the game will
        # later believe depth 2 has already been generated and try to load a
        # missing depth2.dat when the player descends.
        result["generated_levels"] = [1, 2]

    return result


def regular_depth2() -> dict:
    return regular_depth(
        entrance_type="REGULAR_ENTRANCE",
        entrance_dest_depth=1,
        entrance_dest_type="REGULAR_EXIT",
        exit_cell=None,
    )


def quote_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=True, separators=(",", ":"))


def canonical_json(value, *, root: bool = False, checksum: str | None = None) -> str:
    if isinstance(value, dict):
        keys = [
            key
            for key in value.keys()
            if not (root and key == CHECKSUM_KEY)
        ]
        if root and checksum is not None:
            keys.append(CHECKSUM_KEY)
        keys.sort()

        parts = []
        for key in keys:
            item = checksum if root and key == CHECKSUM_KEY and checksum is not None else value[key]
            parts.append(f"{quote_string(key)}:{canonical_json(item)}")
        return "{" + ",".join(parts) + "}"

    if isinstance(value, list):
        return "[" + ",".join(canonical_json(item) for item in value) + "]"
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return str(value)
    return quote_string(str(value))


def bundle_checksum(data: dict) -> str:
    unsigned = canonical_json(data, root=True)
    return CHECKSUM_PREFIX + hashlib.sha256(unsigned.encode("utf-8")).hexdigest()


def signed_json(data: dict, checksum_override: str | None = None) -> str:
    checksum = checksum_override if checksum_override is not None else bundle_checksum(data)
    return canonical_json(data, root=True, checksum=checksum)


def signed_data(data: dict, checksum_override: str | None = None) -> dict:
    result = copy.deepcopy(data)
    result[CHECKSUM_KEY] = checksum_override if checksum_override is not None else bundle_checksum(data)
    return result


def write_bundle(path: Path, data: dict, checksum_override: str | None = None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if emit_bundle_checksums():
        payload = signed_json(data, checksum_override).encode("utf-8")
    else:
        payload = json.dumps(data, separators=(",", ":"), ensure_ascii=True).encode("utf-8")
    with gzip.GzipFile(filename=str(path), mode="wb", mtime=0) as archive:
        archive.write(payload)


def write_json(path: Path, data: dict, checksum_override: str | None = None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    mirror_data = signed_data(data, checksum_override) if emit_bundle_checksums() else data
    path.write_text(
        json.dumps(mirror_data, indent=2, sort_keys=True, ensure_ascii=True) + "\n",
        encoding="utf-8",
    )


def file_info(path: Path) -> dict:
    data = path.read_bytes()
    return {
        "path": str(path.relative_to(ROOT)),
        "bytes": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
    }


def write_slot(
    base: Path,
    slot_number: int,
    game_data: dict,
    depth_data: dict,
    *,
    game_checksum: str | None = None,
    depth_checksum: str | None = None,
    extra_depths: dict[int, dict] | None = None,
) -> None:
    slot = base / f"game{slot_number}"
    write_bundle(slot / "game.dat", game_data, game_checksum)
    write_bundle(slot / "depth1.dat", depth_data, depth_checksum)
    write_json(slot / "game.json", game_data, game_checksum)
    write_json(slot / "depth1.json", depth_data, depth_checksum)
    for depth, depth_bundle in sorted((extra_depths or {}).items()):
        write_bundle(slot / f"depth{depth}.dat", depth_bundle)
        write_json(slot / f"depth{depth}.json", depth_bundle)


def branchless_depth_file_name(depth: int) -> str:
    return f"depth{depth}.dat"


def build_slot_metrics(
    scenario: dict,
    regular: dict,
    modified: dict,
    regular_path: Path,
    copy_path: Path,
    modified_path: Path,
    modified_depth_path: Path,
    modified_depth: dict,
    stored_game_checksum: str | None,
    stored_depth_checksum: str | None,
    checksums_emitted: bool,
) -> dict:
    current_depth_file = f"depth{modified['depth']}.dat"
    modified_slot = modified_path.parent
    missing_generated_files = [
        branchless_depth_file_name(depth)
        for depth in modified["generated_levels"]
        if depth != modified["depth"] and not (modified_slot / branchless_depth_file_name(depth)).exists()
    ]
    transitions = modified_depth["level"]["transitions"]
    actual_game_checksum = bundle_checksum(modified)
    actual_depth_checksum = bundle_checksum(modified_depth)
    return {
        "slot": scenario["slot"],
        "name": scenario["name"],
        "case_label": scenario["seed_label"],
        "custom_seed": modified["custom_seed"],
        "title": scenario["title"],
        "summary": scenario["summary"],
        "regular_game_dat": file_info(regular_path),
        "copied_game_dat": file_info(copy_path),
        "modified_game_dat": file_info(modified_path),
        "modified_depth1_dat": file_info(modified_depth_path),
        "copy_matches_regular": hashlib.sha256(regular_path.read_bytes()).hexdigest()
        == hashlib.sha256(copy_path.read_bytes()).hexdigest(),
        "bundle_checksum_state": {
            "checksums_emitted": checksums_emitted,
            "stale_checksum_from_original_save": checksums_emitted and scenario["name"] in STALE_CHECKSUM_SCENARIOS,
            "game": {
                "stored": stored_game_checksum,
                "actual": actual_game_checksum,
                "matches": stored_game_checksum == actual_game_checksum if checksums_emitted else None,
            },
            "depth1": {
                "stored": stored_depth_checksum,
                "actual": actual_depth_checksum,
                "matches": stored_depth_checksum == actual_depth_checksum if checksums_emitted else None,
            },
        },
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


def build_metrics(slot_metrics: list[dict], checksums_emitted: bool) -> dict:
    return {
        "fixture_mode": "checksum" if checksums_emitted else "original",
        "checksums_emitted": checksums_emitted,
        "slots": {
            f"game{metrics['slot']}": metrics
            for metrics in slot_metrics
        },
    }


def depth_for_scenario(scenario: dict) -> dict:
    name = scenario["name"]
    if name == "original_game":
        return regular_depth()
    if name == "down_to_depth_999":
        return regular_depth(
            exit_dest_depth=999,
            exit_dest_type="REGULAR_ENTRANCE",
        )
    if name == "up_floor_0_down_missing_d2":
        return regular_depth(
            entrance_type="REGULAR_ENTRANCE",
            entrance_dest_depth=0,
            entrance_dest_type="REGULAR_EXIT",
        )
    raise ValueError(f"Unknown scenario: {name}")


def write_readme(path: Path, checksums_emitted: bool) -> None:
    checksum_lines = [
        "The `.dat` files are gzipped JSON bundles with root `__checksum` fields matching the current",
        "save-integrity implementation.",
        "The adjacent `.json` files are readable mirrors for inspection and diffing.",
        "",
        "`modified/game2` and `modified/game3` intentionally keep the `depth1.dat` stored `__checksum`",
        "from `modified/game1` after their depth payloads are edited. Their `game.dat` files are",
        "re-signed so all three save slots remain visible; the current build rejects slots 2 and 3",
        "when it tries to load the modified depth bundle.",
        "The checksum state and behavior deltas are captured in `metrics.json`.",
    ]
    legacy_lines = [
        "The `.dat` files are gzipped JSON bundles without root `__checksum` fields, matching the",
        "original benchmark save format.",
        "The adjacent `.json` files are readable mirrors for inspection and diffing.",
        "",
        "`modified/game2` and `modified/game3` contain the same payload-level faults as the checksum",
        "fixtures, but no checksum metadata. Original benchmark builds can open them and expose the",
        "latent save-quality issues that the checksum benchmark is expected to block.",
        "The fixture mode and behavior deltas are captured in `metrics.json`.",
    ]
    modified_summary_lines = (
        [
            "- `modified/game1`: the signed original save.",
            "- `modified/game2`: the `DOWN-TO-DEPTH-999` save with a valid slot header and stale depth checksum.",
            "- `modified/game3`: the `UP-FLOOR-0-DOWN-MISSING-D2` save with a valid slot header and stale depth checksum.",
        ]
        if checksums_emitted
        else [
            "- `modified/game1`: the original save in original benchmark format.",
            "- `modified/game2`: the `DOWN-TO-DEPTH-999` save in original benchmark format.",
            "- `modified/game3`: the `UP-FLOOR-0-DOWN-MISSING-D2` save in original benchmark format.",
        ]
    )

    path.write_text(
        "\n".join(
            [
                "# Game Save Quality Fixture",
                "",
                "This directory contains baseline, copied, and modified save states for slots `game1` through `game3`:",
                "",
                "- `regular/gameN`: clean original starter save.",
                "- `copy/gameN`: exact copy of that clean save.",
                *modified_summary_lines,
                "",
                *(checksum_lines if checksums_emitted else legacy_lines),
                "",
                "## Slot Cases",
                "",
                *[
                    f"- `modified/game{scenario['slot']}` label `{scenario['seed_label']}`: "
                    f"{scenario['title']} - {scenario['summary']}"
                    for scenario in SCENARIOS
                ],
                "",
                "## Case Design",
                "",
                "- `modified/game1` is a clean two-floor save. Floor 1 has a `SURFACE` upstairs transition",
                "  and a downstairs transition to depth 2. `depth2.dat` is present and has an upstairs",
                "  transition back to floor 1, so using the stairs does not require procedural generation.",
                "  `game.dat`, `depth1.dat`, and `depth2.dat` all have matching bundle checksums.",
                "",
                "- `modified/game2` uses custom seed `DOWN-TO-DEPTH-999` and changes the down-ladder",
                "  destination to depth 999. Its `game.dat` checksum is valid so the slot is visible,",
                "  but `depth1.dat` keeps the original checksum and is rejected by the current benchmark.",
                "",
                "- `modified/game3` uses custom seed `UP-FLOOR-0-DOWN-MISSING-D2`, marks depth 2 as",
                "  generated without including `depth2.dat`, and changes the upstairs transition from",
                "  `SURFACE` to `REGULAR_ENTRANCE`, which targets floor 0 from depth 1. Its `game.dat`",
                "  checksum is valid, but `depth1.dat` keeps the original checksum and is rejected.",
                "",
                "## Checksum Behavior",
                "",
                "The current implementation validates `__checksum` against the canonical JSON payload before",
                "restoring a bundle. Slots 2 and 3 keep slot 1's stored depth checksum after their depth",
                "contents change, so their expected depth checksum no longer matches their actual payload.",
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
    checksums_emitted = emit_bundle_checksums()

    original_game_data = regular_game(1, "ORIGINAL-GAME")
    original_depth_data = regular_depth()
    original_depth2_data = regular_depth2()
    original_game_checksum = bundle_checksum(original_game_data)
    original_depth_checksum = bundle_checksum(original_depth_data)

    slot_metrics = []
    for scenario in SCENARIOS:
        slot_number = scenario["slot"]
        regular = copy.deepcopy(original_game_data)
        regular_depth_data = copy.deepcopy(original_depth_data)
        modified = modified_game(original_game_data, scenario)
        modified_depth_data = depth_for_scenario(scenario)
        use_stale_checksum = scenario["name"] in STALE_CHECKSUM_SCENARIOS
        stored_game_checksum = bundle_checksum(modified) if checksums_emitted else None
        stored_depth_checksum = (
            original_depth_checksum if use_stale_checksum else bundle_checksum(modified_depth_data)
        ) if checksums_emitted else None
        extra_depths = {}
        if scenario["name"] != "up_floor_0_down_missing_d2":
            extra_depths[2] = copy.deepcopy(original_depth2_data)

        write_slot(
            regular_dir,
            slot_number,
            regular,
            regular_depth_data,
            extra_depths={2: copy.deepcopy(original_depth2_data)},
        )
        write_slot(
            copy_dir,
            slot_number,
            copy.deepcopy(regular),
            copy.deepcopy(regular_depth_data),
            extra_depths={2: copy.deepcopy(original_depth2_data)},
        )
        write_slot(
            modified_dir,
            slot_number,
            modified,
            modified_depth_data,
            game_checksum=stored_game_checksum if use_stale_checksum else None,
            depth_checksum=stored_depth_checksum if use_stale_checksum else None,
            extra_depths=extra_depths,
        )

        slot_metrics.append(
            build_slot_metrics(
                scenario,
                regular,
                modified,
                regular_dir / f"game{slot_number}" / "game.dat",
                copy_dir / f"game{slot_number}" / "game.dat",
                modified_dir / f"game{slot_number}" / "game.dat",
                modified_dir / f"game{slot_number}" / "depth1.dat",
                modified_depth_data,
                stored_game_checksum,
                stored_depth_checksum,
                checksums_emitted,
            )
        )

    metrics = build_metrics(slot_metrics, checksums_emitted)
    write_json(OUT_DIR / "metrics.json", metrics)
    write_readme(OUT_DIR / "README.md", checksums_emitted)
    print(f"Created save fixtures in {OUT_DIR.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
