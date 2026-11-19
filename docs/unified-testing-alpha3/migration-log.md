# Migration to Unified Testing 0.1.0-alpha3: process log

Branch: `unified-testing-alpha3` (from `dev-main` @ `0193862`)
Library: `org.vaadin.addons:unified-test-*` 0.1.0-alpha3 ([parttio/unified-testing-tool](https://github.com/parttio/unified-testing-tool))
Issues found along the way: [`findings.md`](findings.md)

## Status

| Step | What | Status |
|---|---|---|
| 0 | Research, inventory, plan | **Done** (this commit) |
| 1 | Bump Vaadin 25.2.6 → 25.3.5 (+ Spring AI realign) | **Done**: unit 193 ✓, IT 74 ✓, AIIT not run (no LLM endpoint) |
| 2 | Bump DramaFinder 1.2.0 → 2.2.0 | Not started |
| 3 | Baseline commit before the recipe | Not started |
| 4 | Run the OpenRewrite migration recipes | Not started |
| 5 | Hand-convert the `MisePlaywrightIT` base class, re-run the recipe | Not started |
| 6 | Configure the test layers (Playwright + Browserless) | Not started |
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
