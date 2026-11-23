# Findings: Unified Testing 0.1.0-alpha4 in Mise

Issues found while migrating Mise's Browserless and DramaFinder tests to `org.vaadin.addons:unified-test-*` 0.1.0-alpha4. The process is in [`migration-log.md`](migration-log.md). Numbering starts at **G1** so these don't clash with the alpha3 findings (F1–F27, on branch `unified-testing-alpha3`). Each entry says how sure it is:

- **Verified:** reproduced, or checked against the artifact or the docs.
- **Predicted:** read from the docs or the recipe code, not yet run.

The last section tracks each alpha3 finding: fixed, still open, or not exercised.

## New findings

### G1: `resolve-migration-markers` is still user-only. Verified · repeats F9 ([#366](https://github.com/parttio/unified-testing-tool/issues/366))

The alpha4 `SKILL.md` inside `unified-test-rewrite` still has `disable-model-invocation: true`. An agent running the migration end to end can't invoke it. It has to read the file and follow the procedure by hand, as in alpha3.

## alpha3 findings re-checked

| alpha3 | Status in alpha4 | Evidence |
|---|---|---|
| F9 | Open | G1 |
