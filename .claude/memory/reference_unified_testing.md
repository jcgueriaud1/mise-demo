---
name: reference-unified-testing
description: Mise's UI tests are Unified UI Tests (org.vaadin.addons:unified-test-* 0.1.0-alpha4), run on Browserless and Playwright; where the docs live and the gotchas found migrating
metadata:
  type: reference
---

Mise's `*IT.java` UI tests (and `browserless/PlanViewBrowserlessTest`) are **Unified UI Tests**: `org.vaadin.addons:unified-test-*` **0.1.0-alpha4** ([parttio/unified-testing-tool](https://github.com/parttio/unified-testing-tool), docs at https://parttio.github.io/unified-testing-tool/). Migrated from DramaFinder + Browserless on branch `unified-testing-alpha4` (2026-11-23); the process log and findings are in `docs/unified-testing-alpha4/`.

- **Requires Vaadin 25.3+** (hence the 25.3.5 bump) and Spring Boot 4.1 for `@UnifiedSpringBootTest`.
- **Run:** `./mvnw -Pit verify` runs every IT twice — Failsafe executions `it-browserless` then `it-playwright` (the parent's `default` execution is skipped). Reports per tool under `target/failsafe-reports/<tool>/unified-evidence/`; Playwright's `summary.md` has "Compared with Browserless".
- **API reference** for every Wrapper: `META-INF/unified/api-reference.md` inside the `unified-test-api` jar (also `llms.txt` on the site). Skill: `.claude/skills/write-unified-ui-tests/SKILL.md`. Style rules: `UnifiedStyleTest` (ArchUnit).
- **Docs at a release tag lag one version** (alpha4 tag's migration guides say alpha3) — use the live site or `<version>` yourself.

Gotchas found in this codebase:
- **Browserless keeps one UI across `page.navigate(..)`** — re-entering the same route reuses the view instance and fires `beforeEnter` again. `OnboardingView` built a second `AIOrchestrator` on its one provider and threw; a browser hides this (full page load). Guard one-time UI building.
- **Browserless has no CSS** — it acts on fields a stylesheet hides. The week `DatePicker`'s field is CSS-hidden (only its overlay shows): `setValue(..)` passes on Browserless, fails on Playwright ("found 1, but hidden"). Pick via `picker.element().setProperty("value", iso)`.
- **Persisted chat rows land after the reply renders** — wait with `page.waitUntil(..)` (inside `page.step(..)`), don't read the repository once.
- `UnifiedMessageList` has no positional check: wait with `hasMessageCount(n)`, then read `getMessageTexts().get(i)`.
- `assertThat(page).hasNoErrors()` fails on any server ERROR log line / browser console error during the test — used as the regression test for the `OnboardingView` bug.

Related: [[reference-dramafinder]] (the previous IT stack, now legacy), [[project-vaadin-ai-versions]].
