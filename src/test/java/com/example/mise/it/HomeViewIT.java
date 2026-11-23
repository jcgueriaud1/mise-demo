package com.example.mise.it;

import com.example.mise.it.support.TestAiConfig;
import com.example.mise.it.support.TestChatModel;
import com.example.mise.ui.HomeView;

import com.vaadin.flow.component.html.H2;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedSpan;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

/**
 * Pilot UI IT — validates the IT toolchain (Spring Boot + Unified UI Tests, on Browserless and Playwright)
 * against the simplest view in the app. Intentionally does not interact with the chat panel
 * or any AI surface: those are covered by separate ITs that stub the {@code ChatModel}.
 */
@UnifiedSpringBootTest
@ActiveProfiles("it")
@Import(TestAiConfig.class)
class HomeViewIT {

    @Autowired
    protected TestChatModel chatModel;

    @BeforeEach
    void resetChatModelBetweenTests() {
        chatModel.reset();
    }

    @Test
    @DisplayName("The debug page has its title")
    void hasPageTitle(UnifiedPage page) {
        page.navigate(HomeView.class);

        assertThat(page).hasTitle("Mise — Debug");
    }

    @Test
    @DisplayName("The debug page shows the stage header")
    void rendersStageHeader(UnifiedPage page) {
        page.navigate(HomeView.class);

        // H2 has no Wrapper; findComponent finds it by its text
        assertThat(page.findComponent(H2.class).withText("Mise — prep build (stage 2)")).isVisible();
    }

    @Test
    @DisplayName("The debug page shows the stored message count")
    void showsStoredMessageCount(UnifiedPage page) {
        page.navigate(HomeView.class);

        assertThat(page.find(UnifiedSpan.class).withTextContaining("Stored messages:")).isVisible();
    }
}
