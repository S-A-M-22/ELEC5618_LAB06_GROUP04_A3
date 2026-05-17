# Mob Behaviour Logging — Cyclomatic Complexity Analysis

Companion document for the predefined Assignment 3 improvement #5 (Mob Behaviour Logging).
Measures cyclomatic complexity V(G) before and after the instrumentation to demonstrate that
the logging is **purely observational**: it adds zero new decision points to any existing
method in `Mob.java`.

V(G) is counted as `V(G) = π + 1` where π is the number of independent predicates
(`if`, `else if`, `case`, `&&`, `||`, `?:`, `for`, `while`, `catch`). Counts are by direct
inspection of the source.

---

## 1. Existing methods in `Mob.java` (instrumented but logic-preserving)

Every substitution is a 1-for-1 statement replacement (`state = X;` → `setState(X, "...");`,
`enemy = X;` → `setEnemy(X, "...");`, `alerted = true;` → `markAlerted("...");`).
No new branches were added inside any of these methods.

| Method | V(G) Before | V(G) After | Delta | Substitution sites |
|---|---:|---:|---:|---|
| `Mob.act()` | 7 | 7 | 0 | `state = FLEEING`, `enemy = chooseEnemy()` |
| `Mob.chooseEnemy()` | 23 (per test plan §3.2.1) | 23 | 0 | 2× `state = HUNTING` (aggression buff paths) |
| `Mob.add(Buff)` | 7 | 7 | 0 | 3× state assignments (Amok/AllyBuff, Terror/Dread, Sleep) |
| `Mob.remove(Buff)` | 8 | 8 | 0 | 2× state assignments (post-rage, no-enemy) |
| `Mob.aggro(Char)` | 2 | 2 | 0 | `enemy = ch`, `state = HUNTING` |
| `Mob.clearEnemy()` | 2 | 2 | 0 | `enemy = null`, conditional `state = WANDERING` |
| `Mob.damage(int, Object)` | 12 | 12 | 0 | `state = WANDERING` (SLEEPING→WANDERING), `alerted = true` |
| `Mob.beckon(int)` | 2 | 2 | 0 | conditional `state = WANDERING` |
| `Mob.onAdd()` | 2 | 2 | 0 | one extra unconditional logger call inside the existing `if (firstAdded)` branch — does not add a predicate |
| `Sleeping.awaken(boolean)` | 8 | 8 | 0 | 2× state assignments (HUNTING / WANDERING) |
| `Wandering.act()` | 4 (per test plan §3.2) | 4 | 0 | none |
| `Wandering.noticeEnemy()` | 7 | 7 | 0 | `alerted = true`, `state = HUNTING` |
| `Hunting.act()` | 14 (per test plan §3.2.2) | 14 | 0 | `enemy = ch` (target switch), 2× `state = WANDERING`, 2× `enemy =` (recurse re-pick) |
| `Fleeing.act()` | 8 | 8 | 0 | none |
| `Fleeing.nowhereToRun()` | 4 | 4 | 0 | 2× state assignments (HUNTING / WANDERING) |
| `Mob.restoreFromBundle(Bundle)` | 6 | 6 | 0 | **deliberately NOT instrumented** — these are deserialisation, not real transitions |

**Result:** ΣΔV(G) over all instrumented methods = **0**. The instrumentation introduces no
additional branching anywhere in the existing AI code path.

---

## 2. New helper methods in `Mob.java`

Three thin wrappers centralise the logging side-effect so each instrumented site reads as a
single statement. All three are package-private (tests in the same package call them directly).

| Method | V(G) | Notes |
|---|---:|---|
| `Mob.setState(AiState, String)` | 4 | `if (same) return;` + two ternaries (`?:` for from/to state names). Logging skipped on no-op self-transitions. |
| `Mob.setEnemy(Char, String)` | 4 | `if (same) return;` + two ternaries for the target name/id when null. |
| `Mob.markAlerted(String)` | 2 | `if (alerted) return;` — single-turn idempotence to match the existing `alerted` flag semantics. |

All three are well below the test plan's target of V(G) ≤ 5 for new logger-touching code.

---

## 3. `MobBehaviourLogger` (new class)

| Method | V(G) | Notes |
|---|---:|---|
| `getInstance()` | 1 | static accessor |
| `setEnabled(boolean)` | 1 | field assignment |
| `isEnabled()` | 1 | field read |
| `setMaxEntries(int)` | 2 | `while (size > max)` trim |
| `getMaxEntries()` | 1 | field read |
| `clear()` | 1 | delegates to LinkedList.clear |
| `getEntries()` | 1 | constructor call |
| `size()` | 1 | field read |
| `setTerminalSink(Consumer)` | 1 | field assignment |
| `setFileSink(Consumer)` | 1 | field assignment |
| `logSpawn(...)` | 2 | `if (!enabled) return;` |
| `logStateChange(...)` | 2 | `if (!enabled) return;` |
| `logAlert(...)` | 2 | `if (!enabled) return;` |
| `logTarget(...)` | 2 | `if (!enabled) return;` |
| `record(...)` (private) | 2 | one `while` for ring-buffer eviction |
| `dispatch(Consumer, String)` (private static) | 3 | null-guard + try/catch |
| `format(Entry)` (private) | 1 | string formatting only |
| `defaultFileSink(String)` (private static) | 1 | delegates to libGDX FileHandle |
| `safe(String)` (private static) | 2 | null guard |
| `Entry.<init>` / `Entry.toString` | 1 each | data class |

**Max V(G) in the new class: 4** (`logXxx` collectively peak at 2; `dispatch` at 3 due to try/catch).
This satisfies the test plan's target V(G) ≤ 5 for new logger code (Section 4.2 of the test plan).

---

## 4. Summary

| Surface | V(G) impact |
|---|---|
| Existing instrumented methods in `Mob.java` | **+0** across 15 methods |
| New helpers in `Mob.java` | 3 small methods, V(G) ∈ {2, 4, 4} |
| New `MobBehaviourLogger` class | 20 methods, all V(G) ≤ 4 |

The logging subsystem is observational by construction and adds zero complexity to the
mob AI itself.

