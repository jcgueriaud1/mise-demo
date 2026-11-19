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
