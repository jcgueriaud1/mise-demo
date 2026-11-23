package com.example.mise.it;

import com.example.mise.domain.household.Household;
import com.example.mise.domain.household.HouseholdRepository;
import com.example.mise.domain.household.HouseholdService;
import com.example.mise.it.support.TestAiConfig;
import com.example.mise.it.support.TestChatModel;
import com.example.mise.ui.onboarding.OnboardingView;

import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * BR-01: If a {@link Household} row already exists, navigating to {@code /welcome} must
 * forward to {@code /plan} without rendering the onboarding chat panel.
 *
 * <p>Lifecycle design: this class overrides {@code setupTest()} — the {@code @BeforeEach}
 * method from {@link org.vaadin.addons.dramafinder.AbstractBasePlaywrightIT} that creates the
 * browser page and navigates. In JUnit 5, a child method with the same name hides the parent's
 * {@code @BeforeEach}, so {@code super.setupTest()} must be called explicitly to perform the
 * browser navigation. The household is seeded first, so the {@code BeforeEnterObserver} in
 * {@link com.example.mise.ui.onboarding.OnboardingView} finds an existing row and fires
 * the redirect before the page renders.
 *
 * <p>The sibling {@code resetChatModelBetweenTests()} from {@link MisePlaywrightIT} has a
 * different method name so it is not hidden — it still runs as a separate {@code @BeforeEach}.
 *
 * <p>Cleanup: {@code @AfterEach} deletes all household rows so the shared in-memory H2 and
 * Spring context stay clean for other ITs. {@code @DirtiesContext} is not needed.
 */
@UnifiedSpringBootTest
@ActiveProfiles("it")
@Import(TestAiConfig.class)
class OnboardingRedirectIT {

    @Autowired
    protected TestChatModel chatModel;

    @BeforeEach
    void resetChatModelBetweenTests() {
        chatModel.reset();
    }


    @Autowired
    private HouseholdService householdService;

    @Autowired
    private HouseholdRepository householdRepository;

    /**
     * Override: seed the Household before the base class navigates to /welcome.
     * The base @BeforeEach on the same method name is hidden by this override in JUnit 5.
     */
    @BeforeEach
    public void setupTest() throws Exception {
        Household h = new Household();
        h.setName("IT Seed Household");
        h.setSize(2);
        h.setWeeklyBudget(new BigDecimal("90.00"));
        h.setAllergies(List.of());
        h.setHatedFoods(List.of());
        householdService.save(h);
    }

    @AfterEach
    void deleteAllHouseholds() {
        householdRepository.deleteAll();
    }

    /**
     * BR-01: navigating to /welcome when a Household already exists redirects to /plan.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void existingHouseholdRedirectsToPlan(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate(OnboardingView.class);
        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright).hasURL(Pattern.compile(".*/plan.*"))
        assertThat(playwright).hasURL(Pattern.compile(".*/plan.*"));
    }
}
