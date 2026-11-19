package com.example.mise.it;

import com.example.mise.it.support.TestAiConfig;
import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.flow.component.html.H2;
import com.vaadin.testing.unified.component.UnifiedSpan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

/**
 * Pilot Playwright IT — validates the IT toolchain (Spring Boot + Playwright + DramaFinder)
 * against the simplest view in the app. Intentionally does not interact with the chat panel
 * or any AI surface: those are covered by separate ITs that stub the {@code ChatModel}.
 */
@UnifiedSpringBootTest
@ActiveProfiles("it")
@Import(TestAiConfig.class)
class HomeViewIT {

    @Test
    @DisplayName("The debug page has its browser tab title")
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void hasPageTitle(UnifiedPage page) {
        // Escape Hatch kept: the Unified API has no page-title assertion
        Page playwright = page.unwrap(Page.class);
        page.navigate("/debug");
        com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(playwright).hasTitle("Mise — Debug");
    }

    @Test
    @DisplayName("The debug page shows the build stage header")
    void rendersStageHeader(UnifiedPage page) {
        page.navigate("/debug");
        assertThat(page.findComponent(H2.class).withText("Mise — prep build (stage 2)")).isVisible();
    }

    @Test
    @DisplayName("The debug page shows how many chat messages are stored")
    void showsStoredMessageCount(UnifiedPage page) {
        page.navigate("/debug");
        assertThat(page.find(UnifiedSpan.class).withTextContaining("Stored messages:")).isVisible();
    }
}
