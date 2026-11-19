# Findings: Unified Testing 0.1.0-alpha3 in Mise

Issues found while preparing the migration of Mise's Browserless and DramaFinder tests to `org.vaadin.addons:unified-test-*` 0.1.0-alpha3. The process is in [`migration-log.md`](migration-log.md). Each entry says how sure it is:

- **Verified:** reproduced or checked against the artifact or the docs.
- **Predicted:** read from the docs or the recipe code, not yet run.

## Documentation

### F1: Migration guides at the alpha3 tag give alpha2 coordinates. Verified

At tag `unified-test-0.1.0-alpha3`, `docs/migrating-from-dramafinder.md` and `docs/migrating-from-browserless.md` both give:

```bash
-Drewrite.recipeArtifactCoordinates=org.vaadin.addons:unified-test-rewrite:0.1.0-alpha2
```

The Gradle snippet (`rewrite("org.vaadin.addons:unified-test-rewrite:0.1.0-alpha2")`) is the same. Copy-pasting it runs the **old** recipes against an alpha3 build. Those recipes emit the pre-ADR-0007 API (`single()`, `rows()`), which alpha3 no longer compiles.
**Suggestion:** generate the version from `${project.version}` (mkdocs macro or snippet), or add a release-checklist item.

### F2: Stale "Not published yet" warning. Verified

Both migration guides open with *"The Migration Recipes are published to Maven Central from `spec-v1`. Until then, build them from this repository with `mvn install`."* The alpha3 release notes say they **are** on Central, and `unified-test-rewrite-0.1.0-alpha3.jar` is on `repo.maven.apache.org` (HTTP 200).

### F3: The docs site documents `main`, not the release, and has no versioned docs. Verified

The guide marks "Running it in a browser" as *"Unreleased: on main, not in 0.1.0-alpha3"*. Yet the section's main point, `-Dvaadin.testing.tool=playwright`, works in alpha3, so it is hard to tell which parts are actually missing. The "For agents" section is mixed in the same way. It says the `write-unified-ui-tests` skill is "installed by the `AddUnifiedTestingSkill` recipe" but gives no command to run that recipe alone. The API reference in `META-INF/unified/api-reference.md` is flagged *unreleased* in the same sentence.
**Suggestion:** publish versioned docs (`mike` for mkdocs-material), or mark unreleased content per item rather than per section.

### F4: The Vaadin 25.3 requirement only appears in the release notes. Verified

*"Requires Java 21, Vaadin 25.3 and, for `@UnifiedSpringBootTest`, Spring Boot 4.1"* is only in `docs/releases/0.1.0-alpha3.md`. It is not in the home page "Try it" section or in the migration guides, and the recipes don't check it. The recipe would happily add the BOM to a 25.2 project like Mise (25.2.6). That project would then get `browserless-test` 25.3.2 on Flow 25.2 and fail at test time, not at build time.
**Suggestion:** state the requirement in "Try it" and in each migration guide. Make the `Migrate*Build` recipes check the Vaadin version, or fail early with an enforcer rule or a startup check in the adapters.

## Build and BOM

### F5: `unified-test-bom` re-pins Vaadin-managed and third-party versions. Verified (BOM content); impact predicted

The BOM manages `com.vaadin:browserless-test-junit6` and `browserless-test-spring` at 25.3.2, and `org.vaadin.addons:dramafinder` at 2.2.0. The Vaadin BOM already manages `browserless-test-*`, and in Maven the first `import` wins. So the result depends on where the recipe inserts the BOM import, and the docs don't mention it. For DramaFinder the BOM also silently changes major version (1.x → 2.x). 2.x lookups have different semantics: whole-value matches that fail when several components match, instead of the first case-insensitive substring match. That can break unmigrated DramaFinder code left behind the Escape Hatch.
**Suggestion:** document the import order, and the DramaFinder 1 → 2 jump, in the migration guides.

## Migration recipes

### F6: A project-specific DramaFinder base class blocks the whole DramaFinder migration, and nothing says why. Predicted

The guide says a class *"that uses a base class member the recipe doesn't convert (… an override of `prepareBrowserContext(..)`, a `getView()` that computes its view) is left exactly as it is"*. The recipe recognises only a plain copy of `SpringPlaywrightIT`.

Mise has a very common pattern: an abstract `MisePlaywrightIT extends AbstractBasePlaywrightIT` with:

- `@SpringBootTest(RANDOM_PORT)`
- a `getUrl()` override using `@LocalServerPort`
- a `@BeforeEach` that resets a stub bean
- a screenshot-on-failure `TestWatcher` that reads the base class's `page`

All 10 ITs extend it. The expected outcome is that none of them migrate, **with no Migration Marker** explaining why.
**Suggestion:** leave a class-level marker (`not migrated: base class X uses Y`), like the Browserless recipe's `not migrated: …` markers. Also recognise `getUrl()` built from `@LocalServerPort` as redundant under `@UnifiedSpringBootTest`.
*To confirm in step 4/5 of the migration.*

### F7: No Wrappers for MessageInput / MessageList. Verified (recipe Wrapper list)

The recipe converts only TextField, TextArea, Button, Notification, Checkbox, Dialog and Grid lookups. DramaFinder already ships `MessageInputElement` and `MessageListElement`, and they are the core of any Vaadin AI chat UI (`AIOrchestrator`). In Mise they are the only DramaFinder elements used (6 and 2 uses), and both will come out as Escape Hatch + "write a Wrapper" markers.
**Suggestion:** ship `UnifiedMessageInput` / `UnifiedMessageList` in `unified-test-api`. This matters especially since Vaadin 25.x pushes the AI components.

### F8: Raw Playwright `locator(..)` is the dominant pattern in real suites, and will stay behind the Escape Hatch. Predicted

Mise's ITs make about 71 raw `page.locator(..)` calls. About 55 are `#id` selectors (`#mise-week-…`, `#mise-meal-…`), a few are `[data-testid=…]`, and the rest are tag, class or attribute selectors (`vaadin-date-picker`, `.mise-shopping-…`, `[data-meal-day]`). The DramaFinder recipe only converts DramaFinder element lookups and a few `getByRole`/`getByLabel` shapes. `locator("#x")` and `locator("[data-testid=x]")` map mechanically to `find(<Wrapper>).withId("x")` / `withTestId("x")` once the Wrapper type is known, but the recipe doesn't attempt it. Expect the marker-resolution step to dominate the effort.
**Suggestion:** a recipe step for `locator("#…")` / `locator("[data-testid=…]")` → `withId(..)` / `withTestId(..)` when the type can be inferred from the following call (`fill` → text field, `click` → button…), with a marker to check the guessed type.

## Agent skills

### F9: Only the user can start `resolve-migration-markers`. Verified

Its frontmatter has `disable-model-invocation: true`. The guide says to type `/resolve-migration-markers`, but not that an agent driving the migration (like this one) can't start it. An end-to-end agent migration then stalls at step 7 until a human steps in.
**Suggestion:** say so in the guide, or drop the flag. The skill only edits test sources and runs tests.

## Process notes (not library issues)

- In this repo, `pom.xml` changes need approval (CLAUDE.md guardrail). The Vaadin 25.3 bump is also an AI-stack change: Spring AI must match what Vaadin's AI components declare, and `vaadin-ai-components-flow` on Central stops at 25.2.13. So the migration can't start until that bump is approved and green.
- Two first-pass findings were wrong and were dropped:
  - The site's migration pages do resolve, at the root (`/migrating-from-dramafinder/`).
  - `unified-test-demo` is not on Central; the copy in `~/.m2` was a local install.

## Found while running the migration (steps 4+)

### F6 update: confirmed

The recipe converted 1 of the 12 DramaFinder IT classes, `HomeViewIT`, the only one that uses nothing from the project base class. The other 11 were left byte-for-byte unchanged and got no Migration Marker. The only way to see that they were skipped is to diff the file list against the recipe's "Changes have been made to …" output.

### F10: The recipe drops the annotations of a copied base class. Verified

`MisePlaywrightIT` is recognised as a project copy of `SpringPlaywrightIT` (abstract, extends `AbstractBasePlaywrightIT`, has `@SpringBootTest`). So the migrated `HomeViewIT` gets a bare `@UnifiedSpringBootTest`, and the base class's `@ActiveProfiles("it")` and `@Import(TestAiConfig.class)` are **silently lost**. In Mise this means the test would boot with the default profile: the file H2 at `./data/mise` and the real LLM config instead of the stubbed `TestChatModel`. It compiles, and it may even pass, against the wrong environment. That makes it the most dangerous finding so far.
**Suggestion:** carry the copy's class-level annotations (`@ActiveProfiles`, `@Import`, `@TestPropertySource`, …) onto each migrated subclass. Or, at minimum, add a class-level marker listing the annotations that weren't carried over.

### F11: `addAgentSkill: true` doesn't write `resolve-migration-markers`. Verified

With `MigrateDramafinderToUnified: { addAgentSkill: true }` in a `rewrite.yml`, the run wrote only `.claude/skills/write-unified-ui-tests/SKILL.md`, although it left 5 `// TODO unified:` markers. `AddMigrationMarkersSkill` is a `ScanningRecipe`. A likely cause is that its scanner looks for markers in the *original* sources, which contain none on a first migration. Not confirmed.
**Workaround:** copy `META-INF/unified/skills/resolve-migration-markers/SKILL.md` out of the `unified-test-rewrite` jar.

### F12: Validation error, "recipe `IgnoreToDisabled` does not exist". Verified

```
Recipe validation error … MigrateUIUnitToBrowserless.recipeList[3] (…browserless.yml):
recipe 'org.openrewrite.java.testing.junit5.IgnoreToDisabled' does not exist.
```
The run continues, and the recipe only matters for TestBench UI Unit (Vaadin 24) tests. The likely cause is version skew. The docs' command uses an unpinned `rewrite-maven-plugin` (6.46.1 here, which brings `rewrite-testing-frameworks` 3.44.0), while `unified-test-rewrite` was built against `rewrite-recipe-bom` 3.37.0. A UI Unit project would silently miss the `@Ignore` → `@Disabled` conversion.
**Suggestion:** pin the plugin version in the documented command, or use the recipe's new name.

### F13: `@UnifiedSpringBootTest` fails in apps without Spring Security. Verified

```
NoClassDefFoundError: org/springframework/security/core/Authentication
  at com.vaadin.testing.unified.spring.TestLoginConfiguration$SpringSecurityPresent.matches(TestLoginConfiguration.java:23)
```
`spring-security-core` is an `<optional>` dependency of `unified-test-spring`, and the condition is meant to switch test login off without it. But the condition calls `TestLogin.isAvailable()`, and `TestLogin` has `Authentication` in its method signatures. Loading the class to run the check fails before `Class.forName(..)` inside it can return `false`. **Every** `@UnifiedSpringBootTest` in an app without Spring Security fails to start, and Mise has no Spring Security.
**Suggestion:** check `ClassUtils.isPresent("org.springframework.security.core.context.SecurityContextHolder", cl)` in the condition itself, and keep `TestLogin` unreferenced until it passes. Add a CI test module without Spring Security.
**Workaround used:** add `spring-security-core` with test scope.

### F14: `browserless-test-spring` is kept with a stale pinned version. Verified

The Browserless build recipe keeps `browserless-test-spring`, which is correct, but leaves its explicit `<version>1.1.2</version>`. That is the old standalone add-on line, now replaced by the 25.x line in the Vaadin and Unified BOMs. The explicit version beats both BOMs, so the build mixes `browserless-test-junit6` 25.3.2 with `browserless-test-spring` 1.1.2.
**Suggestion:** have the recipe remove the explicit version when a BOM manages the artifact.

### F15: Claude Code doesn't read the generated `AGENTS.md` pointer. Verified

`AddUnifiedTestingSkill` writes the skill pointer to `AGENTS.md`. Claude Code reads `CLAUDE.md`, not `AGENTS.md`, and this project has a `CLAUDE.md` and no `AGENTS.md`. The pointer only works because the skill is also under `.claude/skills/`, where Claude Code discovers it.
**Suggestion:** when `CLAUDE.md` exists and `AGENTS.md` doesn't, add the marked section to `CLAUDE.md`, or have it import `AGENTS.md`.

### F16: `getUrl()` in a test body also blocks the whole class. Verified

`page.navigate(getUrl() + "/plan?week=" + monday)` is how DramaFinder tests open a view with query parameters. `getUrl()` is a base-class member the recipe doesn't convert, so the 5 classes that do this were skipped entirely, again without a marker. Since `UnifiedPage.navigate(String)` takes a path, `navigate(getUrl() + "/x")` maps mechanically to `page.navigate("/x")`.
**Suggestion:** convert the `getUrl() + "<path>"` pattern. More generally, convert what can be converted and mark the rest, instead of skipping a class over a single member.

### F17: Getting a real suite migrated takes repeated "find what blocks it" passes. Verified (process)

In Mise the recipe converted 1/12 IT classes on the first run, 7/12 after the base class was removed, and 12/12 after `getUrl()` was removed. Each blocker was found by comparing converted and skipped classes, because the recipe reports neither which classes it skipped nor why. Also note that a class-level `@BeforeEach` override of `setupTest()` (seed, then `super.setupTest()`) is the standard DramaFinder idiom for "seed before navigating", and the recipe could convert it directly: the `@BeforeEach` stays and the navigation moves into the test.
**Suggestion:** a dry-run report recipe (`FindDramafinderTests` with reasons): "skipped `PlanViewIT`: uses `setupTest()` override, `getUrl()`".

### F18: Running both tools in one build isn't documented, and the defaults collide. Verified

Running the same ITs on two Testing Tools in one Maven build means two failsafe executions. Unless each one gets its own `reportsDirectory`, `summaryFile` **and** `vaadin.testing.evidence-dir`, the second execution silently overwrites the first one's `summary.md`, `report.html` and failsafe summary. Failsafe's `verify` would then only check the last tool. Also, with Spring Boot's parent POM, the parent's `default` failsafe execution runs everything a third time on the default tool, Browserless. The "Running in CI" guide shows separate *jobs*, not a single build.
**Suggestion:** document a two-execution Maven snippet (what Mise now uses), or default `evidence-dir` to include the tool name (`unified-evidence/<tool>/`).

### F19: Migration Markers swallow the comment above the statement. Verified

When the converted statement has a comment line above it, the marker repeats the comment glued to the code with no separator:
```java
// Focus the message input to trigger :focus-within on the dock, which CSS
// expands the message-history region from max-height:0 to visible.
// TODO unified: Dramafinder Escape Hatch: // Focus the message input to trigger :focus-within on the dock, which CSS// expands the message-history region from max-height:0 to visible.input.focus()
input.focus();
```
That happens 24 times in Mise. It hurts the markers' readability and breaks a `grep 'TODO unified: .*Escape Hatch: <call>'`.
**Suggestion:** print the statement without its leading comments (OpenRewrite's `withPrefix(Space.EMPTY)` / `withComments(List.of())` before `printTrimmed`).

### F20: Browserless finds app bugs the browser hides; that's a plus, but the docs could say so. Verified (positive)

On its first Browserless run, a migrated test failed with `CancellableLLMProvider is already in use by another AIOrchestrator`. The cause was `OnboardingView.beforeEnter()` building a new `AIOrchestrator` each time the *same* view instance was re-entered. Browserless starts at `""` (which forwards to `/welcome`), and the test's `navigate("/welcome")` re-enters that instance. In a browser every navigation is a page load and a new instance, so the bug never shows there, but a user navigating within the app could hit it. The Unified failure message (`Error text: There was an exception while trying to navigate to 'welcome' …`) plus the server log made it quick to find.
**Suggestion:** the "Running on the other Testing Tools" section could mention that Browserless's first navigation lands on `""`, and that a later `navigate(..)` to the current route reuses the view instance.

### F21: No page-title assertion. Verified

`@PageTitle` is ordinary Flow behaviour (the browser tab title), and it is available on every tool: `document.title` in a browser, `UI.getInternals().getTitle()` on Browserless. But `PageAssert` has only `isAt`, `hasPath` and `hasQueryParameter`, so a title check stays Playwright-only behind the Escape Hatch.
**Suggestion:** `assertThat(page).hasTitle(..)`.

### F22: No retrying assertion for grid column headers. Verified

`UnifiedGrid.getColumnHeaders()` exists, but `GridAssert` has only `hasRowCount` / `isEmpty`, and `GridColumnAssert` has only sort and cell checks. "The leaderboard now has a *Cooked times* column", the main outcome of an AI grid reshape in Mise, therefore needs `page.waitUntil(() -> grid.getColumnHeaders().contains("Cooked times"))`. That shows as a gap in the reports, and its failure message is weaker.
**Suggestion:** `assertThat(grid).hasColumn("Cooked times")` / `hasColumnHeaders(..)`, or `assertThat(grid.column("Cooked times")).exists()`.

### F23: `UnifiedDiv` / `UnifiedSpan` have no `click()`. Verified

A `Div` or `Span` with a server-side click listener (`ClickNotifier`) is a common Flow pattern. In Mise the nav tabs, the brand wordmark and the week badge are all built this way, so a user clicks them. The Wrappers offer `getText()` only, so tests have to write `wrapper.element().click()`, which reads worse and probably reports as a gap rather than a "click …" User Step.
**Suggestion:** give the HTML-container Wrappers (`UnifiedDiv`, `UnifiedSpan`, `UnifiedContainer`, …) a `click()`, perhaps only when the component implements `ClickNotifier`.

### F24: Browserless passes interactions a user can't do when CSS hides the field. Verified (documented limitation, worth a louder note)

`UnifiedDatePicker.setValue(..)` on a picker whose input is hidden by CSS (the week picker opens as a calendar overlay only):
- **Playwright:** `Found 1, but hidden`. Correct: a user can't type there.
- **Browserless:** passes, because server-side the field is visible.

The "Running on the other Testing Tools" section of the migration guide mentions that "Visible" means `setVisible(true)` on the server. This is the practical consequence: a test can be green on Browserless and red on Playwright for a real reason. Running both tools in CI (step 6) is what caught it.
