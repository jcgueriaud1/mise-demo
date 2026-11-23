# Migration to Unified Testing 0.1.0-alpha4: process log

Branch: `unified-testing-alpha4` (from `dev-main` @ `0193862`). This is a fresh migration from `dev-main` and does **not** build on `unified-testing-alpha3`. That branch is only read, as the baseline for [`comparison.md`](comparison.md).
Library: `org.vaadin.addons:unified-test-*` 0.1.0-alpha4 ([parttio/unified-testing-tool](https://github.com/parttio/unified-testing-tool), released 2026-11-20).
Issues found along the way: [`findings.md`](findings.md)

## Status

| Step | What | Status |
|---|---|---|
| 0 | Research, inventory, plan | **Done** |
| 1 | Bump Vaadin 25.2.6 → 25.3.5 | |
| 2 | Bump DramaFinder 1.2.0 → 2.2.1 (baseline) | |
| 3 | Dry run `FindTestClassesToMigrate` | |
| 4 | Run the Migration Recipes, unassisted | |
| 5 | Unblock skipped classes (if any), re-run | |
| 6 | Configure the test layers (Playwright + Browserless) | |
| 7 | Resolve the Migration Markers, class by class | |
| 8 | `UnifiedStyleTest` (ArchUnit style rules) | |
| 9 | Try alpha4's new tooling on the migrated suite | |
| 10 | Docs, skills, memory | |
| 11 | Full verification + comparison with alpha3 | |

Convention: **one commit per step**, message `unified-testing-alpha4: step N, <what>`. Each commit appends its entry below and any new issue to `findings.md`.

---

## Step 0: Research and plan (2026-11-23)

### Sources read

- The alpha4 release notes (`gh release view unified-test-0.1.0-alpha4`).
- The alpha4 jars already in `~/.m2`: `unified-test-rewrite` holds the recipe YAMLs and both bundled skills. `unified-test-rewrite-0.1.0-alpha4.pom` returns HTTP 200 on Central.
- The alpha3 branch's `migration-log.md` and `findings.md` (F1–F27), as the baseline.

The research took about 5 minutes. Last time most of the effort went into finding the docs. This time the release notes alone answer almost every planning question, and they list which alpha3 findings are fixed.

### What alpha4 promises against the alpha3 findings

| alpha3 finding | alpha4 release-note claim | To verify in step |
|---|---|---|
| F4: the Vaadin 25.3 requirement only appears in the release notes | Tests fail fast with the version; the recipe leaves a `pom.xml` marker | (bumping first, so not exercised) |
| F6/F16/F17: classes skipped silently; repeated passes needed | `FindTestClassesToMigrate` dry run; skipped classes get a marker naming why; `getUrl()` converted; base-class members copied | 3, 4 |
| F7: no MessageInput/MessageList Wrappers | `UnifiedMessageInput` / `UnifiedMessageList`, converted by the recipe | 4, 7 |
| F8: raw `locator("#id")` left behind the Escape Hatch | Converted to `find(..).withId(..)` with the Wrapper of the view's component | 4 |
| F9: `resolve-migration-markers` is user-only | **Not changed**: still `disable-model-invocation: true` (checked in the jar) | 7 |
| F10: base-class annotations dropped | Copied to each migrated test | 4 |
| F11: `addAgentSkill` ignored | Fixed (`-parameters`) | 4 |
| F12: `IgnoreToDisabled` validation error | Fixed | 4 |
| F13: `@UnifiedSpringBootTest` needs Spring Security | Fixed | 4 |
| F14: `browserless-test-spring:1.1.2` kept | Removed when the Vaadin BOM manages it | 4 |
| F15: pointer written to `AGENTS.md`, which Claude Code doesn't read | Written to `CLAUDE.md` when there is no `AGENTS.md` | 4 |
| F18: running both tools in one build undocumented | Not mentioned | 6 |
| F19: markers swallow the comment above | Fixed (#326) | 4 |
| F21: no page-title assertion | `hasTitle(..)` | 7 |
| F22: no retrying grid column assertion | `hasColumn(..)` / `hasColumnHeaders(..)` | 7 |
| F23: no `click()` on `UnifiedDiv` / `UnifiedSpan` | Added | 7 |
| F25: navigate by class with a query | `navigate(view, QueryParameters)`; recipe emits `navigate(View.class)` | 4, 7 |
| F26: `tryUnwrap` exemption doesn't reach helpers | Fixed (#449) | 8 |
| F27: `findDescendants` elements lose focus handling | Fixed (#450) | 7 |

### Inventory: the same suite as alpha3's starting point

| Layer | Classes | Tests |
|---|---|---|
| Browserless | `browserless/PlanViewBrowserlessTest` | 1 |
| DramaFinder / Playwright | 12 `*IT`, all extending `it/MisePlaywrightIT` (`AbstractBasePlaywrightIT`) | 74 run by `-Pit` (+4 `ManualExamples*` tagged live-LLM) |
| Out of scope | `aiit/*AIIT` (ChatClient, no UI), unit tests | |

`MisePlaywrightIT` holds the following. Each item is one blocker alpha3's recipe couldn't handle:

- `@SpringBootTest(RANDOM_PORT) @ActiveProfiles("it") @Import(TestAiConfig.class)`
- `@LocalServerPort port`
- the `getUrl()` override
- an autowired `TestChatModel chatModel` and a `@BeforeEach` that resets it
- a `@RegisterExtension TestWatcher` that **reads `page`** to take a screenshot on failure

The release notes say an extension that reads `page` still blocks migration. Unlike alpha3, I don't remove it by hand first. The aim is to see what alpha4 reports.

Playwright surface in the ITs (grep counts):

| Pattern | Count |
|---|---|
| `locator(` | 71 |
| `getUrl()` | 26 |
| `MessageInputElement` | 22 |
| `waitForFunction` | 20 |
| `evaluate(` | 14 |
| `getView()` | 14 |
| `MessageListElement` | 11 |
| `getByRole` | 1 |

### Environment

- There is no LLM endpoint: no `application-local.properties` and no `MISE_MODEL_*`. The AIIT layer and the live-LLM `ManualExamples*` ITs can't run, the same as for alpha3.

### Plan

The plan is the step table above. The user approved it, including the `pom.xml` changes. Two differences from alpha3:

- **Step 3** is new (the dry run).
- **Step 4** runs on the untouched suite. alpha3 had to remove base-class members by hand first (its step 5), and those passes are the main effort alpha4 says it removes.
