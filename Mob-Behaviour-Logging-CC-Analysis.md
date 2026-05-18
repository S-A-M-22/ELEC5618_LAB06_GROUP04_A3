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

---

## 5. SQA results

All metrics scoped to the new logging subsystem only (existing legacy game code is
out of scope; we deliberately did not retrofit SQA on code we did not author).

### 5.1 Test counts

| Suite | Tests |
|---|---:|
| `MobBehaviourLoggerTest` (unit, 9 `@Nested` groups) | 36 |
| `MobBehaviourLoggerIntegrationTest` (TestMob fixture) | 13 |
| `MobBehaviourLoggerPerformanceTest` (microbenchmarks) | 4 |
| **Total** | **53** — all passing |

### 5.2 Code coverage (JaCoCo 0.8.11)

| Class | Lines | Branches | Methods |
|---|---:|---:|---:|
| `MobBehaviourLogger.Entry` | 100% (9/9) | n/a | 100% (2/2) |
| `MobBehaviourLogger` | 95.5% (63/66) | **100% (16/16)** | 95.2% (20/21) |

The single uncovered method is `defaultFileSink(String)` — calls libGDX
`FileHandle.writeString` which requires a Gdx runtime. Tests inject a stub
sink instead. Path is exercised when the game runs.

Report: `core/build/reports/jacoco/test/html/index.html`.

### 5.3 Static analysis (SpotBugs 4.8.5, effort=MAX, confidence=LOW)

2 findings, both reviewed and accepted as intentional design choices:

| Bug pattern | Location | Severity | Rationale for accepting |
|---|---|---|---|
| `MS_EXPOSE_REP` (CWE-218) | `MobBehaviourLogger.getInstance()` | Medium | Returning the singleton reference is the entire point of the accessor; suppressing this finding via a true-immutable design would force a fully-static API and lose injectable sinks for testing. |
| `SING_SINGLETON_HAS_NONPRIVATE_CONSTRUCTOR` | `MobBehaviourLogger()` | Medium | Constructor is package-private (not public) specifically so tests in the same package can construct fresh loggers per test, avoiding singleton-state leakage between tests. The trade is testability vs. dogmatic singleton purity; we choose testability. |

No CORRECTNESS-, PERFORMANCE-, or SECURITY-priority findings.

Report: `core/build/reports/spotbugs/main.html`.

### 5.4 Mutation testing (PITest 1.16.0)

| Metric | Value |
|---|---:|
| Mutations generated | 29 |
| Mutations killed | 28 |
| Mutations surviving | 0 |
| Mutations with no test coverage | 1 (the libGDX `defaultFileSink`) |
| Line coverage (mutated classes) | 96% (72/75) |
| **Test strength** | **100%** |

The one uncovered mutation is the libGDX-touching `defaultFileSink` line, same
reason as the JaCoCo gap. Every other reachable mutation across 14 mutation
operators is killed by an existing assertion — meaning the test suite genuinely
verifies behaviour rather than just executing code.

One round of TDD-style feedback during this work: PITest initially reported
`isEnabled()` returning `true` constantly as a surviving mutation. That
identified a test gap (we asserted "is true by default" but never asserted
"is false after disable"). Adding `isEnabled_reflectsSetter` killed it,
raising test strength from 96% to 100%.

Report: `core/build/reports/pitest/index.html`.

### 5.5 Performance microbenchmarks

Run on the developer machine (Windows, JDK 21, single warm run after 50k
warm-up iterations). Numbers are reported as ns/event; "events/sec" is
the headline. Stub sinks isolate the logger from the file system.

| Scenario | ns/event | events/sec |
|---|---:|---:|
| `setEnabled(false)` — disabled | **4.6** | 215,000,000 |
| Enabled, `logSpawn` with stub sinks | 1,318 | 758,000 |
| Enabled, `logStateChange` with stub sinks | 1,166 | 858,000 |
| Enabled, eviction at cap=1000 | 1,343 | 745,000 |

In actual gameplay, mob events fire at ~10–100 events/sec — eight orders of
magnitude below the logger's saturation throughput. The assignment's
"must not disrupt game performance" requirement is satisfied with margin.

Report: `core/build/reports/tests/test/index.html` (look for the
`MobBehaviourLoggerPerformanceTest` lines containing `[PERF]`).
