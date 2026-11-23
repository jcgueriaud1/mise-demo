package com.example.mise;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.vaadin.testing.unified.archunit.UnifiedStyleRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameStartingWith;

class UnifiedStyleTest {

    @Test
    @DisplayName("Every Unified UI Test follows the style rules")
    void followsTheUnifiedStyle() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.example.mise")
                // The live-LLM release-gate ITs still carry their Migration Markers: they can only
                // be resolved and proven against a live model (-Pmanual-verify). Drop this filter
                // once they are migrated.
                .that(not(simpleNameStartingWith("ManualExamples")));
        UnifiedStyleRules.ALL.check(classes);
    }
}
