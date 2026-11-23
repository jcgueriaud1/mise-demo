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

### G8: The Findable View is written outside the test's evidence directory, and the failure doesn't name it. Verified · minor

The release notes say a failed test writes `<test>.view.txt` and that the failure's evidence line names it. Here it was written as `unified-evidence/com.example.mise.it.OnboardingViewIT.messagesPersistedWithOnboardingViewContext.view.txt`, at the root. The test's own directory, `unified-evidence/OnboardingViewIT/messagesPersistedWithOnboardingViewContext/`, holds only `user-steps.txt` / `.json`. The `UnifiedAssertionError`'s `Evidence` line names that directory, not the view file. On Browserless no screenshot is written, so a reader who opens the named directory doesn't find the view.
**Suggestion:** write the view into the test's evidence directory, or name it in `Evidence` / `user-steps.json`'s `failure`.

### G9: Server-log lines under the User Step found an app bug in one read. Verified · positive

On the first Browserless run, `OnboardingViewIT` failed with `Expected exactly one message input, found none`. The cause was printed under User Step 1 in `user-steps.txt`: `⚠ server ERROR … CancellableLLMProvider is already in use by another AIOrchestrator`. It came from a view that rebuilds its orchestrator when `beforeEnter` fires again on the same instance, which Browserless does on a same-route `navigate` and a browser hides behind a full page load. Without the log line, "found none" would have meant a debugging session. This is the same class of finding as alpha3's F20, which took longer to diagnose.

### G10: `UnifiedMessageList` has no positional or per-sender check. Verified · minor

The checks are `hasMessageCount`, `hasMessageContaining` (any message), `hasMessageTexts` (all texts, exactly) and `isEmpty`. A chat test usually means "the user's message, then the assistant's reply containing X". Mise's ITs checked `getMessage(1)` and `getMessage(2)` with `containsText(..)`. `hasMessageTexts` needs every full text, including a long seeded greeting, and `hasMessageContaining` loses the order and who said it. So the test waits on `hasMessageCount(3)` and then reads `getMessageTexts().get(i)` once. That works, but the reads leave no `see` line in `user-steps.txt`.
**Suggestion:** `hasMessage(int index, String containing)` or `hasLastMessageContaining(..)`, and/or a check on `Message.userName()`, like `hasMessageFrom("Mise", containing)`.

### G11: There's no way to pick a date in a date picker's overlay when its field is hidden. Verified

Mise's week picker is a `DatePicker` whose text field is hidden by CSS; the badge opens its calendar overlay, and the user picks a day there. `UnifiedDatePicker.setValue(..)` / `typeText(..)` act on the field. That is correct on Playwright, which refuses (`found 1, but hidden`), and wrong on Browserless, which passes (F24). No Unified call picks a day in the overlay. What works on both tools is `picker.element().setProperty("value", "2026-11-11")`, which is what the pick does underneath, wrapped in a named `page.step(..)`.
**Suggestion:** `UnifiedDatePicker.pick(LocalDate)` (or `selectDate`), which opens the overlay if needed and clicks the day as a user would on browser tools. On Browserless it could set the value and check the picker is `opened`.

### G12: "Compared with Browserless" names a Browserless-only pass at once. Verified · positive

The first version of the picker test passed on Browserless and failed on Playwright. The Playwright run's `summary.md` (from `compare-dir`, set up in step 6) gave the two scenarios that "end differently", the step where the runs part, and the Playwright error. alpha3's F24 found the same class of problem by reasoning after the fact. Here it was the first thing the run reported. The two runs in one `mvn verify`, with the comparison, are worth their setup.

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
| F18 | **Fixed** (documented, copy-paste snippet with compare-dir) | step 6 |
| F19 | **Fixed**: comments stay above the statement | step 4 |
| F21 | **Fixed**: `assertThat(page).hasTitle(..)` on every tool | 7a, 7d, 7f, 7g |
| F22 | **Fixed**: `hasColumn` / `doesNotHaveColumn` | 7d |
| F23 | **Fixed**: `UnifiedDiv.click()` | 7h |
| F24 | Still true (by design); now flagged by the cross-tool comparison | G11, G12 |
| F20 | Still true (positive), and alpha4's server-log lines make it fast to diagnose | G9 |
| F25 | **Fixed** for `getView()`; `getUrl()` paths stay strings | G7 |
