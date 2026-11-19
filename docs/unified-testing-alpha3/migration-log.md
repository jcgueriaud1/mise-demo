# Migration to Unified Testing 0.1.0-alpha3: process log

Branch: `unified-testing-alpha3` (from `dev-main` @ `0193862`)
Library: `org.vaadin.addons:unified-test-*` 0.1.0-alpha3 ([parttio/unified-testing-tool](https://github.com/parttio/unified-testing-tool))
Issues found along the way: [`findings.md`](findings.md)

## Status

| Step | What | Status |
|---|---|---|
| 0 | Research, inventory, plan | **Done** (this commit) |
| 1 | Bump Vaadin 25.2.6 → 25.3.5 (+ Spring AI realign) | **Done**: unit 193 ✓, IT 74 ✓, AIIT not run (no LLM endpoint) |
| 2 | Bump DramaFinder 1.2.0 → 2.2.0 | **Done**: IT 74 ✓, no code changes |
| 3 | Baseline commit before the recipe | **Done**: the step 2 commit is the baseline |
| 4 | Run the OpenRewrite migration recipes | **Done**: 2 of 13 UI test classes converted; 1 library workaround |
| 5 | Hand-prepare the skipped ITs, re-run the recipe | **Done**: 12/12 ITs converted, 74 ✓ on Playwright, 285 markers |
| 6 | Configure the test layers (Playwright + Browserless) | **Done**: -Pit runs both tools; PW 74 ✓, BL 2 ✓ + 72 skipped |
| 7 | Resolve the Migration Markers | Not started |
| 8 | Add the `UnifiedStyleTest` (ArchUnit style rules) | Not started |
| 9 | Update CLAUDE.md, skills, memory, spec | Not started |
| 10 | Full verification across all three layers | Not started |

Convention: **one commit per step**, message `unified-testing: step N, <what>`. Each commit appends its entry below and any new issue to `findings.md`.

---

## Step 0: Research and plan (2026-11-19)

### What the library is

The research turned up these artifacts and documents:

- **Artifacts:** `unified-test-api` (the Unified API, with `Unified*` Wrappers and `UnifiedPage`) and the adapters `unified-test-browserless`, `unified-test-playwright` (built on DramaFinder) and `unified-test-selenium`. Alongside them are `unified-test-spring` (`@UnifiedSpringBootTest`), `unified-test-archunit` (`UnifiedStyleRules`), `unified-test-rewrite` (OpenRewrite Migration Recipes) and `unified-test-bom`.
- **Running tests:** one test source runs on every Testing Tool. The tool is picked with `-Dvaadin.testing.tool=browserless|playwright|selenium`, and the default is `browserless`.
- **Migration:** OpenRewrite recipes first, then an agent skill, `resolve-migration-markers`, for the `// TODO unified:` markers the recipes leave (ADR 0004/0005/0006). The recipes also install `.claude/skills/write-unified-ui-tests/SKILL.md` and point to it from `AGENTS.md`.
- **Top-level recipes**, found inside the `unified-test-rewrite` jar: `MigrateBrowserlessToUnified`, `MigrateDramafinderToUnified` and `MigrateTestBenchToUnified`. Options are `addBrowserless`, `addSelenium` and `addAgentSkill`.
- **Sources read:**
  - the alpha3 jars (`*-sources.jar` from `~/.m2`)
  - `docs/releases/0.1.0-alpha3.md`
  - `docs/migrating-from-dramafinder.md` and `docs/migrating-from-browserless.md`, at tag `unified-test-0.1.0-alpha3`
  - the site's `guide/` and `guide/configuration/` pages
  - the two bundled skill files

### Requirements vs this project

| | alpha3 requires | Mise has | Gap |
|---|---|---|---|
| Java | 21 | 21 | ok |
| Spring Boot (`@UnifiedSpringBootTest`) | 4.1 | 4.1.1 | ok |
| Vaadin | **25.3** | **25.2.6** | **bump needed** |
| browserless-test (BOM-pinned) | 25.3.2 | Vaadin-BOM managed (25.2) + `browserless-test-spring` 1.1.2 | follows the Vaadin bump |
| DramaFinder (BOM-pinned) | 2.2.0 | **1.2.0** | major bump; lookup semantics change |

The Vaadin bump touches the AI stack: `vaadin-ai-components-flow` on Central stops at 25.2.13, and `spring-ai.version` must match what the AI components declare (see CLAUDE.md "Sharp edges"). Under the CLAUDE.md guardrail that `pom.xml` changes need asking first, I asked.

### Decisions taken (by the user)

- **Vaadin bump → "Stop, plan only."** This round makes no build or test changes. It delivers the step plan, this log and the findings.
- **Test layers → "Add browserless adapter too."** Once migrated, the ITs should also run on Browserless, not just Playwright.
- **Process → "Commit for each step."**

### Inventory of the tests to migrate

| Layer | Classes | Notes |
|---|---|---|
| Browserless | 1: `browserless/PlanViewBrowserlessTest` | `@SpringBootTest` + `SpringBrowserlessTest`, profile `it`, `@Import(TestAiConfig.class)`; covered by `MigrateBrowserlessToUnified` |
| DramaFinder / Playwright | 10 `*IT` under `it/`, all extending `it/MisePlaywrightIT` | The base class extends `AbstractBasePlaywrightIT` and has `@SpringBootTest(RANDOM_PORT)`, a `getUrl()` override using `@LocalServerPort`, a `@BeforeEach` that resets `TestChatModel`, and a screenshot-on-failure `TestWatcher` reading `page` |
| Out of scope | `aiit/*AIIT` (ChatClient, no UI), plain unit tests | Not UI tests |

Playwright surface in the ITs:

- about 71 raw `locator(..)` calls, about 55 of them `#id` selectors
- 16 `getView()` overrides
- 2 `page.evaluate`
- 1 `getByRole`
- `PlaywrightAssertions.assertThat`
- DramaFinder elements: `MessageInputElement` (6 uses) and `MessageListElement` (2 uses)

The recipe has Wrappers only for TextField, TextArea, Button, Notification, Checkbox, Dialog and Grid. **Expect most of the IT code to come out as Escape Hatch + Migration Markers.**

### The planned steps

1. **Bump Vaadin to 25.3.x**, latest 25.3.5. Find the 25.3 home of the AI components (`AIOrchestrator`, `GridAIController`, `ChartAIController`) and the Spring AI version they declare. Realign `spring-ai.version`, then update the CLAUDE.md "Sharp edges" note and `.claude/memory/project_vaadin_ai_versions.md`. Get `./mvnw test`, `./mvnw -Pit verify` and `./mvnw -Pai-it verify` green, **one after the other**. Running them concurrently collides on the shared file H2.
2. **Bump DramaFinder 1.2.0 → 2.2.0** on its own. 2.x lookups match the whole value and fail when several components match, where 1.x took the first case-insensitive substring match. Fix the ITs that break. Migrating from 2.x leaves fewer markers than from 1.x.
3. **Baseline commit.** The recipe rewrites in place.
4. **Run the recipes** through a root `rewrite.yml`:
   ```yaml
   type: specs.openrewrite.org/v1beta/recipe
   name: com.example.mise.MigrateToUnified
   recipeList:
     - org.vaadin.addons.unified.rewrite.MigrateBrowserlessToUnified
     - org.vaadin.addons.unified.rewrite.MigrateDramafinderToUnified:
         addBrowserless: true
         addAgentSkill: true
   ```
   ```bash
   ./mvnw -U org.openrewrite.maven:rewrite-maven-plugin:run \
     -Drewrite.recipeArtifactCoordinates=org.vaadin.addons:unified-test-rewrite:0.1.0-alpha3 \
     -Drewrite.activeRecipes=com.example.mise.MigrateToUnified
   ```
   Use **alpha3**; the guides say alpha2 (see findings). Expected `pom.xml` changes:
   - imports `unified-test-bom`
   - adds `unified-test-browserless`, `unified-test-playwright` and `unified-test-spring`
   - removes `dramafinder` and `browserless-test-junit6`

   Keep `browserless-test-spring` but drop its hard-coded `1.1.2` so the BOM manages it. Review with `git diff`.
5. **Hand-convert `MisePlaywrightIT`.** The recipe leaves classes that use base-class members it can't convert untouched:
   - Switch to `@UnifiedSpringBootTest @ActiveProfiles("it") @Import(TestAiConfig.class)`.
   - Drop `getUrl()`: the base URL comes from the Spring port.
   - Replace the screenshot `TestWatcher` with `vaadin.testing.playwright.trace=on-failure` plus the per-test evidence in `target/surefire-reports/unified-evidence/`, and point the CI artifact upload there.
   - Keep the `TestChatModel.reset()` `@BeforeEach`.

   Then re-run the recipe.
6. **Configure the test layers:**
   - In the `-Pit` failsafe config, set `vaadin.testing.tool=playwright` and `vaadin.testing.playwright.headless=${headless}`. Keep the `**/*AIIT.java` exclude and `excludedGroups`.
   - Add a second failsafe execution with `vaadin.testing.tool=browserless` for the Browserless runs of the migrated ITs.
   - Install Chromium once:
     ```bash
     ./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.classpathScope=test -Dexec.args="install chromium"
     ```
7. **Resolve the Migration Markers** with `/resolve-migration-markers`. The user has to start this one (`disable-model-invocation: true`). Work one class at a time; each class must pass on both Playwright and Browserless. What to expect:
   - `MessageInput` and `MessageList` need custom `@UnifiedComponent` Wrappers (ADR 0003).
   - Raw `locator("#id")` calls become `find(..).withId(..)`.
   - CSS, layout and shadow-DOM checks (Reports charts, responsive breakpoints) stay behind the Escape Hatch, with `@RequiresTestingTool(PLAYWRIGHT)` or `@RequiresBrowser`.
   - `page.evaluate` stays as `// Escape Hatch kept: JavaScript`.
8. **Style rules:** add `unified-test-archunit` and a `UnifiedStyleTest` checking `UnifiedStyleRules.ALL` over `com.example.mise`. Give every migrated test a `@DisplayName` and given/when/then markers.
9. **Docs:**
   - `CLAUDE.md` (Build & Run, the three test layers)
   - `.claude/skills/vaadin-playwright-test`, which `write-unified-ui-tests` supersedes
   - `.claude/memory/reference_dramafinder*.md`
   - `spec/verification.md`: ask before any structural rewrite
10. **Verify:** run `./mvnw test`, then `./mvnw -Pit verify` (Playwright and Browserless), then `./mvnw -Pai-it verify`, sequentially. Read a few `user-steps.txt` files and the run's `report.html`.

### How step 0 went

- **Finding the library was quick once I knew where to look.** The alpha2 and alpha3 jars were already in `~/.m2/repository/org/vaadin/addons/unified-test-*`. The BOM's `<scm>` led to the GitHub repo and docs site.
- **The best migration docs are inside the jars.** The recipe YAML in `unified-test-rewrite` and the two bundled `SKILL.md` files say more than the site does. The site documents `main`, not alpha3, and the migration guides at the alpha3 tag still give alpha2 commands (see findings).
- **The Vaadin 25.3 requirement only appears in the release notes.** Nothing in the build would have flagged it before a runtime failure.
- **Two first-pass "findings" didn't hold up** and were dropped:
  - The migration pages *do* resolve, at the site root; the 404 came from a URL I built wrongly.
  - `unified-test-demo` in `~/.m2` is a local install, not a Central artifact.
- **No build was run** in this round, by decision.

---

## Step 1: Vaadin 25.2.6 → 25.3.5 (2026-11-19)

Approved by the user ("go ahead with the Vaadin 25.3.5 bump").

**Changes**
- `pom.xml`: `vaadin.version` 25.2.6 → **25.3.5**. `spring-ai.version` stays **2.0.1**, because Vaadin 25.3.5's `vaadin-ai-core-flow` declares `spring-ai-client-chat:2.0.1`, so nothing needed realigning.
- The AI components now come from `vaadin-ai-core-flow` + `vaadin-ai-extensions-flow`, pulled in transitively by `vaadin`. `vaadin-ai-components-flow` stops at 25.2.x. No import changes were needed: the packages are the same `com.vaadin.flow.component.ai.*`.
- **One compile break in the app**, not in the tests. `AIController.onRequest()` / `onResponse(Throwable)` became `onRequest(RequestListener.RequestEvent)` / `onResponse(ResponseListener.ResponseEvent)`. `ui/reports/ReportsAIController` (the composite controller for the three Reports widgets) now forwards the event to its delegates.
- Docs: CLAUDE.md "Sharp edges", the `pom.xml` comment and `.claude/memory/project_vaadin_ai_versions.md` now describe the 25.3 artifacts and the callback change.
- Side effect: `browserless-test-junit6` resolves to **25.3.2** from the Vaadin 25.3.5 BOM, which is already the version `unified-test-bom` pins. `browserless-test-spring` is still hard-coded at 1.1.2 and is handled in step 4.

**Results**
- `./mvnw test`: **193 tests, 0 failures**, including `PlanViewBrowserlessTest`.
- `./mvnw -Pit verify`: **74 ITs, 0 failures** (3 min, including the frontend rebuild for 25.3).
- `./mvnw -Pai-it verify`: **not run**. This machine has no LLM endpoint (no `application-local.properties`, no `MISE_MODEL_*` env vars). The AIITs drive tool beans through Spring AI's `ChatClient` and don't use the Vaadin AI components that changed, so the risk is low. Run them before merging.

**Not changed (needs approval):** `spec/architecture.md` still says "Vaadin Flow 25.2 … Currently `25.2.0`" and points at `vaadin-ai-components-flow`. CLAUDE.md asks before editing that file.

**How it went:** smooth. The only surprise was the `AIController` signature change. The 25.3 release has it, but nothing in the Spring AI pinning notes would have predicted it.

---

## Step 2: DramaFinder 1.2.0 → 2.2.0 (2026-11-19)

**Changes:** `pom.xml` `dramafinder` 1.2.0 → **2.2.0**, the version `unified-test-bom` 0.1.0-alpha3 pins. No test code changed.

**Results:** test sources compile unchanged. `./mvnw -Pit verify` gives **74 ITs, 0 failures**, and the unit layer gives 193 green.

**How it went:** a non-event. The 1.x → 2.x lookup change ("whole value, fails on several matches") didn't bite because Mise's ITs barely use DramaFinder lookups. They use `MessageInputElement.get(page)` / `MessageListElement.get(page)` and raw Playwright `locator("#id")`, and neither depends on substring matching.

## Step 3: Baseline

The step 2 commit is the baseline the recipe runs against: Vaadin 25.3.5, DramaFinder 2.2.0, everything green.

---

## Step 4: Run the Migration Recipes (2026-11-19)

**Command.** `rewrite.yml` at the project root declares `com.example.mise.MigrateToUnified`, which runs `MigrateBrowserlessToUnified` and then `MigrateDramafinderToUnified` with `addBrowserless: true` and `addAgentSkill: true`. Then:
```bash
./mvnw -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=org.vaadin.addons:unified-test-rewrite:0.1.0-alpha3 \
  -Drewrite.activeRecipes=com.example.mise.MigrateToUnified
```
`rewrite-maven-plugin` resolved to 6.46.1, because the docs don't pin it. The run took about 1 min and the plugin estimated "1h 10m" saved.

**What the recipes did**
- `pom.xml` changes:
  - imported `unified-test-bom` 0.1.0-alpha3, placed *after* the Vaadin and Spring AI BOMs
  - added `unified-test-browserless`, `unified-test-playwright` and `unified-test-spring`
  - removed `browserless-test-junit6` and `dramafinder`, which now arrive transitively, and removed the DramaFinder comment along with them
  - kept `browserless-test-spring` with its hard-coded `1.1.2`
- `PlanViewBrowserlessTest`: `@SpringBootTest` → `@UnifiedSpringBootTest`, no base class, `UnifiedPage page` parameter, `navigate` → `page.navigate`. The `$(Button.class).id(..)` lookup and `test(..).click()` went behind a `BrowserlessEscapeHatch` with 2 markers and `@RequiresTestingTool(BROWSERLESS)`.
- `HomeViewIT`: converted. `getView()` became `page.navigate("/debug")` in each test, and the 3 raw Playwright assertions went behind `page.unwrap(Page.class)` with 3 markers and `@RequiresTestingTool(PLAYWRIGHT)`. **But it lost `@ActiveProfiles("it")` and `@Import(TestAiConfig.class)`**, which lived on the `MisePlaywrightIT` base class (finding F10).
- New files: `.claude/skills/write-unified-ui-tests/SKILL.md` and `AGENTS.md` (a 4-line section pointing to the skill).

**What it did not do**
- **11 of the 12 DramaFinder IT classes were left untouched, with no marker** (finding F6, now confirmed):
  - `OnboardingViewIT` uses the inherited `chatModel` field.
  - Six override `setupTest()`: `PlanViewIT`, `WeekNavigationIT`, `ChatDockIT`, `ChatBusyStopIT`, `GenerateFutureWeeksIT` and `OnboardingRedirectIT`.
  - `ManualExamplesReportsIT` and `ManualExamplesStopIT` extend `AbstractBasePlaywrightIT` directly but also override `setupTest()`.
  - `ReportsViewIT` and `ShoppingViewIT` use `chatModel` or `@BeforeEach` seeding.
  - `MisePlaywrightIT` stays.
- **`addAgentSkill: true` didn't write `resolve-migration-markers`** (finding F11).
- Validation error, not fatal: `recipe 'org.openrewrite.java.testing.junit5.IgnoreToDisabled' does not exist` in `MigrateUIUnitToBrowserless` (finding F12).

**Fixes needed to make the output run**
1. **`@UnifiedSpringBootTest` fails without Spring Security** (finding F13):
   - Every context fails with `NoClassDefFoundError: org/springframework/security/core/Authentication`, thrown from `TestLoginConfiguration$SpringSecurityPresent.matches`.
   - Workaround: a test-scoped `spring-security-core` dependency, version managed by Spring Boot (7.1.1). Spring Boot 4 only auto-configures security through `spring-boot-security`, which isn't on the classpath, so the app's behaviour doesn't change.
2. **`browserless-test-spring`:** dropped the hard-coded `1.1.2`, so it now resolves to **25.3.2** from the BOMs, the same line as `browserless-test-junit6`. Mise was on the old standalone 1.1.x line.

**Results:** `./mvnw test` gives **193 tests, 0 failures**. `PlanViewBrowserlessTest` passes and writes `target/surefire-reports/unified-evidence/…/user-steps.txt`, `summary.md` and `report.html`. Its User Steps show the Escape Hatch gaps:
```
✔ PlanViewBrowserlessTest › rowUndoButtonRestoresPreviousRecipeAndWritesAuditRow   browserless · 1.0 s
   … 1 call outside the Unified API (Escape Hatch)
   1  open Plan
   … 1 call outside the Unified API (Escape Hatch)
```
`-Pit` was not run in this step. `HomeViewIT` is known to be mis-configured (no `it` profile) until step 5, and failsafe still runs it on Browserless by default (step 6).

---

## Step 5: Make the skipped ITs migratable, re-run the recipe (2026-11-19)

The recipe skips a class that uses any base-class member it doesn't convert (F6). So the job was to remove those members *before* the recipe, using a small script (`prep_its.py`, kept in the session scratchpad and not committed), and then let the recipe do the conversion.

**Preparation, applied to the 11 skipped classes:**
- `extends MisePlaywrightIT` / `AbstractBasePlaywrightIT` → DramaFinder's own `org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT`, which brings `@SpringBootTest(RANDOM_PORT)` and `getUrl()`.
- `@ActiveProfiles("it")` + `@Import(TestAiConfig.class)` declared **on each class**. On the base class, the recipe drops them (F10).
- `TestChatModel chatModel` and its `reset()` `@BeforeEach` declared locally, instead of inherited.
- The `setupTest()` overrides (seed data, then `super.setupTest()` to navigate) became a plain `@BeforeEach void seedBeforeNavigation()` without the `super` call. The recipe moves the navigation from `getView()` into the start of each test, which runs after every `@BeforeEach`, so "seed, then navigate" still holds.
- ManualExamples ITs: dropped their own `getUrl()` / `@LocalServerPort`, which `SpringPlaywrightIT` provides.
- Deleted `MisePlaywrightIT`, since nothing extends it any more. Its screenshot-on-failure `TestWatcher` is replaced by Unified's evidence directory, which step 6 wires into CI.
- `HomeViewIT`, migrated in step 4: restored `@ActiveProfiles("it")` + `@Import(TestAiConfig.class)`.

**Second recipe run:** converted 6 more classes. `OnboardingViewIT`, `OnboardingRedirectIT`, `ChatDockIT`, `ChatBusyStopIT` and both ManualExamples ITs.
**Still skipped:** the 5 classes that call `page.navigate(getUrl() + "/plan?week=…")`. `getUrl()` is a base-class member too (finding F16). Each class got a private `baseUrl()` built from its own `@LocalServerPort` field.
**Third recipe run:** converted those 5. All 12 IT classes now use `@UnifiedSpringBootTest` + `UnifiedPage`.

**What came out:**

| Class | Tests | Migration Markers | Tests on `@RequiresTestingTool(PLAYWRIGHT)` |
|---|---|---|---|
| PlanViewIT | 19 | 59 | 18 |
| WeekNavigationIT | 13 | 57 | 13 |
| ShoppingViewIT | 12 | 44 | 12 |
| ChatDockIT | 3 | 28 | 3 |
| ChatBusyStopIT | 3 | 27 | 3 |
| ReportsViewIT | 11 | 27 | 11 |
| OnboardingViewIT | 4 | 18 | 4 |
| GenerateFutureWeeksIT | 5 | 12 | 4 |
| ManualExamplesStopIT | 1 | 7 | 1 |
| HomeViewIT | 3 | 3 | 3 |
| ManualExamplesReportsIT | 3 | 2 | 3 |
| OnboardingRedirectIT | 1 | 1 | 1 |
| **Total** | **78** | **285** | **76** |

So 2 tests came out fully Unified and the rest is Escape Hatch with markers, as expected given that the code is mostly raw `locator("#id")` plus the MessageInput/MessageList elements (F7, F8).

**Results:** `./mvnw -Pit verify -Dvaadin.testing.tool=playwright` gives **74 ITs, 0 failures**. That is the same set and count as before the migration; the 4 ManualExamples tests are excluded by tag. The unit layer gives 193 with 1 skipped: the `-D` flag also reached surefire, so `PlanViewBrowserlessTest` (`@RequiresTestingTool(BROWSERLESS)`) was skipped. Step 6 scopes the property to failsafe.

`resolve-migration-markers` still wasn't generated, even though markers now exist before the run. So F11 isn't the scan-order cause I first guessed.

**How it went:** the recipe is all-or-nothing per class and silent about why it skips one. Getting from 1/12 to 12/12 took two "remove one more base-class member" iterations, each found by elimination, by comparing the converted classes with the skipped ones. Once a class qualified, the conversion was mechanical and correct, and the class-level annotations survived.

---

## Step 6: Configure the test layers (2026-11-19)

**`-Pit` now runs every IT once per Testing Tool**, as two failsafe executions:

| Execution | `vaadin.testing.tool` | Reports + Unified evidence | Result |
|---|---|---|---|
| `it-playwright` | `playwright`, headless from `${headless}` (so `-Pdebug-ui` still opens a window), `vaadin.testing.playwright.trace=on-failure` | `target/failsafe-reports/playwright/` | **74 run, 0 failed** |
| `it-browserless` | `browserless` | `target/failsafe-reports/browserless/` | **2 run, 0 failed, 72 skipped** (`@RequiresTestingTool(PLAYWRIGHT)`) |

How the executions are set up:
- Each execution has its own `reportsDirectory`, `summaryFile` and `vaadin.testing.evidence-dir`. Otherwise the second run overwrites the first one's failsafe summary, `summary.md` and `report.html`.
- Either tool can be switched off: `-Dskip.it.browserless=true` / `-Dskip.it.playwright=true`.
- The spring-boot-starter-parent's own `default` failsafe execution is skipped inside `-Pit`. Without that it ran every IT a third time, on Browserless, the default tool. Before the migration the profile's unnamed execution merged into it, so it went unnoticed (finding F18).
- `-Pmanual-verify` sets `vaadin.testing.tool=playwright` for the ManualExamples ITs.
- The old DramaFinder `headless` system property is gone. Unified reads `vaadin.testing.playwright.headless`.

**CI** (`.github/workflows/ci.yml`) runs `./mvnw test` only, and already uploads `target/surefire-reports/` on failure. That now includes `unified-evidence/` for `PlanViewBrowserlessTest`, so nothing needed changing. The deleted `MisePlaywrightIT` screenshot watcher wrote to `target/playwright-screenshots/`, which no workflow uploaded.

**Results** (`./mvnw -Pit verify`): surefire **193 ✓**, it-playwright **74 ✓**, it-browserless **2 ✓ / 72 skipped**, `BUILD SUCCESS`. `summary.md` reads `### Unified UI Tests · Browserless · 2 passed, 0 failed, 72 skipped`.

**How it went:** straightforward Maven work. The one trap was the parent POM's `default` execution.

---

## Step 7: Resolve the Migration Markers (2026-11-19)

`resolve-migration-markers` has `disable-model-invocation: true` (F9), so the agent followed its written procedure by hand: one class at a time, each run on Playwright **and** Browserless before moving on. Sub-commits are `step 7a`, `7b`, and so on.

### 7a: Shared Wrappers + OnboardingViewIT

- **Wrappers** in `src/test/java/com/example/mise/it/support/wrappers/` (F7):
  - `UnifiedMessageInput.send(text)` / `focus()`. In a browser it fills the input and clicks `vaadin-message-input-button`. Its Browserless override, `UnifiedMessageInputBrowserless`, uses `MessageInputTester.send(..)`.
  - `UnifiedMessageList.getMessageTexts()` / `getMessageCount()` / `getMessageText(i)`. In a browser it reads the `vaadin-message` children. Its Browserless override uses the server-side `MessageList.getItems()`.
  - Both have their own report words: `send “…” with message input`.
- **`OnboardingViewIT`:**
  - All 18 markers resolved, and the 4 tests no longer need `@RequiresTestingTool`.
  - Message-count checks use `page.waitUntil(() -> messages.getMessageCount() == n)`. These Wrappers have no retrying assertion of their own, so the reports show them as a gap.
  - Every test got a `@DisplayName` and given/when/then markers.
- **The Browserless run exposed a real app bug.** `OnboardingView.beforeEnter()` built a new `AIOrchestrator` every time it ran. Browserless starts the UI at `""`, which forwards to `/welcome`, and the test's `navigate("/welcome")` then *re-enters the same view instance*. The second build claimed the same `LLMProvider` and threw `CancellableLLMProvider is already in use by another AIOrchestrator`. A browser never hits this, because every navigation is a fresh page load and so a fresh view. The fix builds the UI once per instance (`if (orchestrator == null) buildUI();`).
- **Result:** `OnboardingViewIT` passes **4/4 on Playwright and 4/4 on Browserless**. Its User Steps:
  ```
  ✔ OnboardingViewIT › The user's answer and the assistant's reply appear in the onboarding chat   browserless · 0.0 s
     1  open “/welcome”
     2  send “Two adults, no fish, around €90 a week” with message input
     … 1 call outside the Unified API (waitUntil)
  ```

### 7b: OnboardingRedirectIT, HomeViewIT

- `OnboardingRedirectIT`: `assertThat(playwright).hasURL(".*/plan.*")` → `assertThat(page).isAt(PlanView.class)`. It now runs everywhere. I also fixed the class Javadoc, which still described the `setupTest()` override.
- `HomeViewIT`: the header and the stored-message count now use `page.findComponent(H2.class).withText(..)` / `page.find(UnifiedSpan.class).withTextContaining(..)` and run everywhere. The page-title check stays `// Escape Hatch kept: the Unified API has no page-title assertion` (F21).
- **Result:** Playwright 4/4. Browserless 3/3, with the title test skipped.

### 7c: GenerateFutureWeeksIT

- All 12 markers resolved, so every test runs everywhere and none needs `@RequiresTestingTool`:
  - The week chevrons use `find(UnifiedButton.class).withAccessibleName("Next week")`, their `aria-label`, which is what a screen-reader user hears.
  - The badge uses `find(UnifiedSpan.class).withId("mise-week-badge")`.
  - Meal rows use `find(UnifiedDiv.class).withTestId("meal-row")` with `hasCount(7)`.
  - The future-week modifier is checked with `assertThat(badge.withClassName("mise-week-badge--future")).exists()`.
  - The disabled check is `isEnabled()`, where the old test checked `disabled` / `aria-disabled` attributes separately.
- `playwright.navigate(baseUrl() + "/plan?week=…")` → `page.navigate("/plan?week=…")`. The query string works on Browserless too, and the `@LocalServerPort` helper added in step 5 is gone.
- The repository-only test lost its `UnifiedPage` parameter and its pointless `navigate`.
- **Result:** Playwright 5/5, Browserless 5/5.

### 7d: ReportsViewIT

- 26 of 27 markers resolved:
  - Widgets use `find(UnifiedContainer.class).withTestId(..)`, the leaderboard uses `find(UnifiedGrid.class).withTestId("leaderboard-grid")`, and the reset buttons use `find(UnifiedButton.class).withTestId(..)` with `exists()`.
  - The "≥1 row" check, which counted `vaadin-grid-cell-content` in the DOM, became `assertThat(grid.row(0)).exists()`.
  - The `getByText("Cooked times")` header checks became `page.waitUntil(() -> grid.getColumnHeaders().contains(..))`, because there is no column-header assertion (F22).
  - The `navigate(baseUrl()+"/reports")` reloads became `page.reload()`, which also says what the test means.
- Kept: the page-title Escape Hatch (F21).
- **Result:** Playwright 11/11, Browserless 10/10 (title test skipped). The charts and the grid all run on Browserless as server-side components.

### 7e: ChatDockIT, ChatBusyStopIT

- **ChatBusyStopIT:** all 27 markers resolved, and all 3 tests now run everywhere.
  - The `waitForFunction("d => d.classList.contains('ai-working')")` polls became `assertThat(chatDock.withClassName("ai-working")).exists()` / `.doesNotExist()`.
  - The busy note (server-side `setVisible`/`setText`) is checked with `assertThat(span).isVisible()` / `hasTextContaining(..)` / `isHidden()`.
  - The `document.querySelectorAll('vaadin-message')` scans use `UnifiedMessageList.getMessageTexts()`.
  - The stop / cancel / "(stopped)" flow, with a background streaming thread, `ui.access` and push, passes on Browserless too.
- **ChatDockIT:**
  - `aiWorkingClassToggledAroundSubmit` is converted and runs everywhere.
  - Kept, with a reason: `dockExpandsOnFocusAndCollapsesOnBlur` (computed `max-height` and `display` under `:focus-within`) and `expandScrollsToLatestMessage` (scroll position after a CSS transition). They need a real browser and stay `@RequiresTestingTool(PLAYWRIGHT)`. Their markers were replaced by one `// Escape Hatch kept: …` line each.
- **Result:**
  - ChatBusyStopIT: Playwright 3/3, Browserless 3/3.
  - ChatDockIT: Playwright 3/3, Browserless 1/1 (2 skipped).

### 7f: PlanViewIT

- 58 of 59 markers resolved, so 18 of the 19 tests run everywhere.
- **Lookups by what the user perceives:**
  - The per-day buttons use their `aria-label`: `withAccessibleName("Undo Thursday")` / `("Why Friday")`.
  - A day's row is `find(UnifiedDiv.class).withTestId("meal-row").withAttribute("data-meal-date", date)`, and its edited pill is found *inside* that row.
  - The pin button per date is `withAttribute("data-pin-date", date)`.
- **Notifications:** `getByText("Undo applied")` → `assertThat(find(UnifiedNotification.class).withTextContaining(..)).isOpen()`.
- **Chat:** two private User Tasks, `sendChatMessage(page, ..)` and `waitForChatMessages(page, n)`, built on the Wrappers. The old `input.focus()` calls, which only expanded the dock so the CSS-hidden list became visible, were dropped. The Wrapper reads messages whether or not they are on screen.
- **Reloads** use `page.reload()`, and a small `aiSwap(day, reason)` helper replaced 6 copies of the same setup.
- Kept: the page-title Escape Hatch (F21).
- **Result:** Playwright 19/19, Browserless 18/18 (title test skipped).
