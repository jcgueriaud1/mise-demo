# Findings: Unified Testing 0.1.0-alpha4 in Mise

Issues found while migrating Mise's Browserless and DramaFinder tests to `org.vaadin.addons:unified-test-*` 0.1.0-alpha4. The process is in [`migration-log.md`](migration-log.md). Numbering starts at **G1** so these don't clash with the alpha3 findings (F1–F27, on branch `unified-testing-alpha3`). Each entry says how sure it is:

- **Verified:** reproduced, or checked against the artifact or the docs.
- **Predicted:** read from the docs or the recipe code, not yet run.

The last section tracks each alpha3 finding: fixed, still open, or not exercised.

## New findings

### G1: `resolve-migration-markers` is still user-only. Verified · repeats F9 ([#366](https://github.com/parttio/unified-testing-tool/issues/366))

The alpha4 `SKILL.md` inside `unified-test-rewrite` still has `disable-model-invocation: true`. An agent running the migration end to end can't invoke it. It has to read the file and follow the procedure by hand, as in alpha3.

### G2: The migration guides at the alpha4 tag give alpha3 coordinates. Verified · repeats F1

At tag `unified-test-0.1.0-alpha4`, both `docs/migrating-from-dramafinder.md` and `docs/migrating-from-browserless.md` give `-Drewrite.recipeArtifactCoordinates=org.vaadin.addons:unified-test-rewrite:0.1.0-alpha3`, three times each (Maven run, `rewrite.yml` run, Gradle). The live site (built from `main` after the release) says alpha4. This is the same off-by-one as F1: the guide is bumped after the tag, not before. Anyone reading the docs pinned to a release, as F3 asks for, copies the previous alpha. Those recipes ignore `rewrite.yml` options (#353) and emit the old API.
**Suggestion:** substitute the version at build time, or add "docs say the version being released" to the release checklist. The `FindTestClassesToMigrate` snippet sidesteps it with `<version>`, but the other commands don't.

### G3: `FindTestClassesToMigrate` names the blocker, but not what to do about it. Verified · positive, with a small suggestion

The dry run took 10 s and named exactly one blocker for 10 classes: `uses screenshotOnFailure of MisePlaywrightIT, which uses the test base`. Removing it took the suite from 3/13 to 13/13 on the second pass. Compare F6/F16/F17 in alpha3: silent skips, and two elimination passes. The reason doesn't say what to replace the member with. A screenshot-on-failure watcher is a very common base-class member, and Unified already writes failure screenshots.
**Suggestion:** for an extension or `TestWatcher` that only reads `page` for a screenshot or trace, the reason (or the guide's "Which classes it skips") could add "Unified writes failure evidence itself: remove it".

### G4: The raw-locator conversion skips the most common assertions: `containsText`, `hasText`, `hasAttribute`, `hasClass`. Verified · narrows F8

The recipe converts `assertThat(locator)`'s `isVisible`, `isEnabled`, `isDisabled`, `hasValue` and `isChecked`, and the `fill`, `inputValue`, `check` and `click` actions. In Mise it wrote 39 `withId` / `withTestId` lookups. But 46 locator markers remain. The most used locator, `#mise-week-badge` (a `Span`, 21 uses), is always checked with `containsText(..)`, and the week buttons with `hasAttribute("disabled", "")` / `not().hasAttribute("aria-disabled", "true")`. The Unified API has the equivalents (`assertThat(span).containsText(..)`, `isDisabled()` / `isEnabled()`), so the recipe could map them.
**Suggestion:** add `containsText` / `hasText` → the Wrapper's text check, `hasAttribute("disabled", "")` → `isDisabled()` and its negation → `isEnabled()`. `hasClass(..)` could at least get a marker that names the Unified alternative, if there is one.

### G5: Scoped or focused chat elements aren't converted to the new chat Wrappers. Verified

`UnifiedMessageInput` / `UnifiedMessageList` (F7) are used only where a test called `MessageInputElement.get(page).typeAndSubmit(..)` directly. Three common shapes stay behind the Escape Hatch, about 40 markers:
- `MessageInputElement.get(playwright.getByTestId("chat-dock"))`, scoped to a locator
- `input.focus()`
- `MessageListElement.get(page)` followed by `getMessage(i)` with `containsText(..)`. Even `assertMessageCount(1)` on the same variable stays, because the variable as a whole isn't converted.

**Suggestion:** convert a scope given by test id to `page.find(UnifiedContainer.class).withTestId(..).find(UnifiedMessageInput.class)`, `focus()` to the Wrapper's `focus()`, and `getMessage(i)` + `containsText` to `getMessages().get(i)` or `hasMessageContaining`.

### G6: Surefire and failsafe write to one evidence directory. Verified · related to F18

With the recipe's build changes and no extra configuration, `./mvnw -Pit verify` writes both phases' evidence to `target/surefire-reports/unified-evidence/`. Failsafe's report goes over surefire's, so `summary.md` reads "Playwright · 74 passed, 0 failed, **1 skipped**": the skipped test is surefire's `PlanViewBrowserlessTest`. That is a merge of two runs on (as step 6 shows) two Testing Tools. alpha3's F18 asked for docs on running both tools in one build. This is the default case before that, with one tool per phase.
**Suggestion:** default the evidence dir from the running plugin (`failsafe-reports` vs `surefire-reports`), or document setting `vaadin.testing.evidence-dir` per execution.

### G7: `getUrl() + "/plan"` becomes a path, though `getView()` → route class works. Verified · minor

The recipe now turns `getView()` returning `"/plan"` into `page.navigate(PlanView.class)` (F25). The same class's `page.navigate(getUrl() + "/plan")` becomes `page.navigate("plan")`, a path, so it isn't checked at compile time and its User Step reads `open “plan”`. In Mise, 9 navigations in `PlanViewIT` (and some in other classes) re-open the view mid-test to reload it.
**Suggestion:** apply the same "exactly one `@Route`" rule to the `getUrl()` conversion, including `+ "?week=" + x` → `navigate(View.class, QueryParameters.of(..))`.

## alpha3 findings re-checked

| alpha3 | Status in alpha4 | Evidence |
|---|---|---|
| F1 | **Repeats** at the alpha4 tag | G2 |
| F6, F16, F17 | **Fixed**: the dry run lists every skipped class and why, in one pass | step 3 |
| F7 | **Fixed** (the Wrappers exist); recipe coverage partial | G5 |
| F8 | **Mostly fixed**: 39 locators converted; assertion coverage partial | G4 |
| F9 | Open | G1 |
| F10 | **Fixed**: base-class annotations, fields, `@BeforeEach` copied | step 4 |
| F11 | **Fixed**: both skills written | step 4 |
| F12 | **Fixed**: no validation error | step 4 |
| F13 | **Fixed**: no `spring-security-core` needed | step 4 |
| F14 | **Fixed**: `browserless-test-spring` version removed | step 4 |
| F15 | **Fixed**: pointer in `CLAUDE.md` | step 4 |
| F19 | **Fixed**: comments stay above the statement | step 4 |
| F25 | **Fixed** for `getView()`; `getUrl()` paths stay strings | G7 |
