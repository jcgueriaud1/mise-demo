package com.example.mise.it;

import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Pilot Playwright IT — validates the IT toolchain (Spring Boot + Playwright + DramaFinder)
 * against the simplest view in the app. Intentionally does not interact with the chat panel
 * or any AI surface: those are covered by separate ITs that stub the {@code ChatModel}.
 */
@UnifiedSpringBootTest
class HomeViewIT {

    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void hasPageTitle(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate("/debug");
        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright).hasTitle("Mise — Debug")
        assertThat(playwright).hasTitle("Mise — Debug");
    }

    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void rendersStageHeader(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate("/debug");
        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright.getByRole(com.microsoft.playwright.options.AriaRole.HEADING).filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText("Mise — prep build (stage 2)"))).isVisible()
        assertThat(playwright.getByRole(com.microsoft.playwright.options.AriaRole.HEADING)
                .filter(new com.microsoft.playwright.Locator.FilterOptions()
                        .setHasText("Mise — prep build (stage 2)")))
                .isVisible();
    }

    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void showsStoredMessageCount(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate("/debug");
        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright.getByText("Stored messages:")).isVisible()
        assertThat(playwright.getByText("Stored messages:")).isVisible();
    }
}
