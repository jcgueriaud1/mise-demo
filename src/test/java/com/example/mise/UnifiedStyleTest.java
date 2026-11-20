package com.example.mise;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.vaadin.testing.unified.archunit.UnifiedStyleRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.not;

/**
 * The Unified UI Test style rules (unified-test-archunit): every scenario has a {@code @DisplayName}
 * sentence, no raw waits, and tool-specific code only behind {@code @RequiresTestingTool}.
 */
class UnifiedStyleTest {

    @Test
    @DisplayName("Every Unified UI Test follows the style rules")
    void followsTheUnifiedStyle() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.example.mise")
                // The live-LLM release-gate ITs (-Pmanual-verify) still carry Migration Markers and poll the
                // database for up to 5 minutes per model turn; they are migrated once they can be run against
                // a live endpoint (see docs/unified-testing-alpha3/migration-log.md, step 7).
                .that(not(JavaClass.Predicates.simpleNameStartingWith("ManualExamples")));
        UnifiedStyleRules.ALL.check(classes);
    }
}
