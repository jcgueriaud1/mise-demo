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

## alpha3 findings re-checked

| alpha3 | Status in alpha4 | Evidence |
|---|---|---|
| F1 | **Repeats** at the alpha4 tag | G2 |
| F6, F16, F17 | **Fixed**: the dry run lists every skipped class and why, in one pass | step 3 |
| F9 | Open | G1 |
