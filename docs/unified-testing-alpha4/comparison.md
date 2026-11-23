# Unified Testing alpha3 vs alpha4: the same migration, twice

Both runs migrated the same suite of Mise UI tests from the same `dev-main` commit (`0193862`) to the Unified API:

- 12 DramaFinder/Playwright ITs on a shared `MisePlaywrightIT` base class
- 1 `SpringBrowserlessTest`

They followed the same step plan, with the same agent, one commit per step.

| | alpha3 | alpha4 |
|---|---|---|
| Branch | `unified-testing-alpha3` | `unified-testing-alpha4` |
| Process log | `docs/unified-testing-alpha3/migration-log.md` | [`migration-log.md`](migration-log.md) |
| Findings | F1–F27 | [G1–G14](findings.md) |

**How to read the numbers:**
- Durations come from commit timestamps. They measure an agent's wall clock, dominated by Maven runs, not a person's effort.
- The same agent did both runs and had read alpha3's log before starting alpha4. Where alpha4 benefited from that knowledge rather than from the library, the table says so (see **Learning effects**).

## The process

| Step | alpha3 | alpha4 | Why it changed |
|---|---|---|---|
| Research | The best docs were inside the jars; the guides at the tag gave alpha2 coordinates (F1) and a stale "not published" warning (F2) | Release notes answered nearly every question, including which alpha3 findings were fixed; the API reference ships in the jar (`api-reference.md`). The guides at the tag still give the **previous** version (G2) | Library + docs; F1 repeats |
| Vaadin 25.2 → 25.3 | Same 4-line `AIController` fix, unit 193 ✓, IT 74 ✓ | Same | Not version-related |
| Finding what blocks migration | Not available: 11 of 12 ITs skipped silently (F6) | `FindTestClassesToMigrate` dry run, 10 s: one CSV naming **one** blocker for 10 classes (the screenshot `TestWatcher`) | **Library** (#352) |
| Hand prep before the recipe | A script over 11 classes: base class swapped, annotations, `chatModel` and `@BeforeEach` copied in, `setupTest()` rewritten, `baseUrl()` helpers, `HomeViewIT` annotations restored (F10) | **Deleted one field** (the `TestWatcher`) | **Library** (#349, #350, #351) |
| Recipe runs until all classes convert | **3** (2/13 → 8/13 → 13/13) | **1** (13/13), plus 2 dry runs | **Library** |
| Library workarounds | `spring-security-core` added (F13); `browserless-test-spring:1.1.2` pin fixed by hand (F14); skill copied by hand (F11); `AGENTS.md` pointer not read (F15); `IgnoreToDisabled` error (F12) | **None** | **Library** |
| Markers after the recipe | 285 | **228** | **Library**: 39 raw locators → `withId` / `withTestId`, chat calls, `getUrl()` |
| Green on Playwright right after the recipe | Yes, after the hand prep | **Yes, straight from the recipe** (74 ✓) | **Library** |
| Running on two tools in one build | Worked out by hand, including the parent POM's `default` execution "trap" | Copy-paste from the guide's *Both Testing Tools in one build*, with `compare-dir` | **Docs** (F18) |
| Wrappers the project had to write | 4 files (`UnifiedMessageInput` / `UnifiedMessageList` + Browserless implementations) | **0** | **Library** (F7, #357) |
| Escape Hatches kept for page titles | 4 tests (Home, Reports, Plan, Shopping) | **0**: `assertThat(page).hasTitle(..)` | **Library** (F21) |
| Grid column-header checks | `page.waitUntil(..)`, a gap in the reports | `hasColumn` / `doesNotHaveColumn` | **Library** (F22) |
| Week navigation with `?week=` | Path strings (F25) | `navigate(PlanView.class, QueryParameters)` | **Library** (F25) |
| Clicking `Div` tabs | `element().click()` (F23) | `UnifiedDiv.click()` | **Library** (F23) |
| Same OnboardingView re-entry bug | Found in its 7a, fixed | Found in step 6; the cause was printed **under the User Step** in `user-steps.txt` | **Library** (server-log lines, #314) |
| CSS-hidden date picker | Kept as 2 Playwright-only tests | First attempt passed on Browserless and failed on Playwright; **"Compared with Browserless"** named both tests and the step where they parted. Fixed with `element().setProperty("value", ..)`; both tests now run on both tools | Detection: **library** (G12). The fix: my choice, which alpha3 could have made too (no picker API yet, G11) |
| Style rules | 1 pass, green after exclusions | 1 pass, green after the same exclusion (live-LLM ITs) | Same |
| Follow-up commits after "done" | 3 (navigate by class; ChatDock on both tools; a flaky persistence race found the next day) | 0 | Library (F25), plus learning (see below) |

### Time (commit timestamps)

| | alpha3 | alpha4 |
|---|---|---|
| Step 0 → step 10 commit | 12:51 → 13:48, **57 min** | 10:05 → 10:50, **45 min** |
| …of which marker resolution (step 7) | 13:25 → 13:44, 19 min | 10:18 → 10:37, 19 min |
| Follow-ups after the wrap-up | +3 commits over the afternoon and next morning | none |

The wall time differs little, because it's dominated by Maven runs. The real difference is **which** work filled it:
- **alpha3** spent its pre-marker time on hand prep, recipe re-runs and library workarounds.
- **alpha4** spent the same window on the dry run (step 3) and on trying the new tooling (step 9).

Each step 7 took 19 minutes: alpha4's cleared 219 markers, alpha3's 276.

## The result

| | DramaFinder baseline | alpha3 final | alpha4 final |
|---|---|---|---|
| Versions | Vaadin 25.2.6, DramaFinder 1.2.0 | Vaadin 25.3.5, unified-test 0.1.0-alpha3 (DramaFinder 2.2.0) | Vaadin 25.3.5, unified-test 0.1.0-alpha4 (DramaFinder 2.2.1) |
| UI tests runnable on Browserless | 1 | 68 + 6 skipped (after its ChatDock follow-up) | **71 + 2 skipped** (70 + 1 new regression test) |
| UI tests runnable on Playwright | 74 | 74 | 75 (incl. the regression test) |
| Tests pinned to Playwright (excl. live-LLM) | all | 6 (4 title, 2 date picker) | **1** (scroll-position JavaScript) |
| Tests needing a real browser (`@RequiresBrowser`) | n/a | 0 (ChatDock uses `tryUnwrap` branches) | 1 (ChatDock computed CSS) |
| `// Escape Hatch kept` comments | n/a | 8 | **2** (in 1 test) |
| Project Wrappers | n/a | 4 files | **0** |
| Migration Markers left | n/a | 9 (live-LLM ITs, not runnable here) | 9 (the same) |
| `src/test` diff vs `dev-main` | | 19 files, +1355 / −1153 | 15 files, +1256 / −1126 |
| App code changed | | `ReportsAIController` (Vaadin 25.3), `OnboardingView` (bug) | the same two |
| Full `-Pit verify` (unit + UI ITs) | 1 min 23 s, Playwright only | 1 min 06 s, both tools | **0 min 59 s, both tools** (Browserless 73 scenarios in 6.7 s, Playwright in 28.9 s) |
| IT class time (sum) | 67 s | n/a | 42 s for both tools |
| Reports | JUnit XML + a screenshot on failure | User Steps, `summary.md`, `report.html`, trace | + server/console lines under steps, `summary.json`, "Compared with Browserless", "Scenarios without a check", Findable View, `hasNoErrors()` |

One trade-off went the other way. alpha3's ChatDock follow-up ran both ChatDock tests on Browserless too, with `tryUnwrap` branches that check the server-side `MessageList` there. alpha4 marks one `@RequiresBrowser` and keeps one Playwright-only, so it checks a little less on Browserless for those two tests. It's simpler code, and alpha3's approach would work in alpha4 as well (F26 is fixed).

## Learning effects (not the library)

Four alpha4 choices came from having done alpha3. They would have helped alpha3 too:

- **The `ReportsAIController` fix** for the Vaadin 25.3 callbacks. I redid it from the compiler errors; it was 4 lines either way.
- **Waiting for persisted chat rows** (`page.waitUntil(..)`), not reading the repository once. alpha3 found this race the next day.
- **`element().setProperty("value", ..)` for the CSS-hidden date picker.** alpha3 kept those 2 tests Playwright-only. The library contributed the cross-tool comparison that flagged the problem at once (G12), but not the fix (G11).
- **Accessible names** (`withAccessibleName("Previous week")`) over ids, wherever the view sets an `aria-label`.

## Findings

| | alpha3 | alpha4 |
|---|---|---|
| New findings | 27 (F1–F27) | 14 (G1–G14), of which 3 positive, 2 repeats |
| alpha3 findings now fixed (verified here) | | **20 of 27**; 2 more reported fixed but not exercised; F3, F9 open; F20, F24 are properties of Browserless; F1 repeats |
| Character of the new findings | Blockers and silent failures: whole classes skipped, options ignored, contexts not starting, missing Wrappers | Recipe coverage gaps (G4, G5, G7), small API gaps (G10, G11), report polish (G8, G13, G14), and two repeats (G1 skill user-only, G2 docs a version behind) |

## Verdict

alpha4's migration was **qualitatively** easier, not just shorter:
- The recipe converted the whole suite in one run, after one dry run that named the single blocker.
- It needed no library workarounds.
- It came out green on Playwright with no hand edits.

The marker work that followed was mostly mapping, not inventing: the chat Wrappers, `hasTitle`, `hasColumn`, `navigate(View, QueryParameters)` and `UnifiedDiv.click()` now exist. The result also runs more of the suite on Browserless (71 vs 68) with fewer Escape Hatches (2 vs 8) and no project Wrappers (0 vs 4).

The new run tooling paid off during the migration itself, not only after it. Server-log lines under steps explained the OnboardingView bug on the first failure, and the cross-tool comparison caught a Browserless-only pass on the first run. `hasNoErrors()` turned the bug into a regression test, and `ApplyDidYouMean` repaired a renamed label in one line.

What still costs time:
- markers the recipe could map but doesn't yet (G4, G5)
- a migration skill an agent can't invoke (G1)
- release-tag docs that lag a version (G2)
