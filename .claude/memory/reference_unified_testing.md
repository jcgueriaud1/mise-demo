---
name: reference-unified-testing
description: Mise's UI tests are Unified UI Tests (org.vaadin.addons:unified-test-* 0.1.0-alpha3) running on Playwright and Browserless; gotchas found while migrating
metadata:
  type: reference
---

**Unified Testing Tool** — one UI test source on Browserless or Playwright (`vaadin.testing.tool`), by parttio.
Repo https://github.com/parttio/unified-testing-tool · docs https://parttio.github.io/unified-testing-tool/ (documents `main`, not the release).
Migrated 2026-11-19 on branch `unified-testing-alpha3`; full log and the library findings in `docs/unified-testing-alpha3/`.

Gotchas (alpha3):
- `@UnifiedSpringBootTest` needs `spring-security-core` on the test classpath even without Spring Security (class-loading bug) — test-scoped dep in pom.xml.
- Requires Vaadin 25.3; `unified-test-bom` pins `browserless-test-*` 25.3.2 and DramaFinder 2.2.0.
- No Wrappers for MessageInput/MessageList → project Wrappers in `src/test/java/com/example/mise/it/support/wrappers/` (Browserless overrides via `MessageInputTester` / `MessageList.getItems()`).
- `UnifiedDiv`/`UnifiedSpan` have no `click()` → `wrapper.element().click()`.
- No page-title or grid-column-header assertion → title stays a Playwright Escape Hatch; headers via `page.waitUntil(() -> grid.getColumnHeaders().contains(..))`.
- Browserless "visible" ignores CSS: a CSS-hidden input passes on Browserless and fails (rightly) on Playwright.
- Browserless starts the UI at `""`; a later `navigate(..)` to the current route re-enters the same view instance (caught an OnboardingView double-orchestrator bug).
- `-Pit` runs two failsafe executions (it-playwright, it-browserless) with separate evidence dirs; the Spring Boot parent's `default` failsafe execution is skipped there.

Related: [[reference-dramafinder]] (now transitive, pre-migration style), [[project-vaadin-ai-versions]].
