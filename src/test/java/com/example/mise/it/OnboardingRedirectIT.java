package com.example.mise.it;

import com.example.mise.it.support.TestChatModel;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import com.example.mise.it.support.TestAiConfig;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.example.mise.domain.household.Household;
import com.example.mise.domain.household.HouseholdRepository;
import com.example.mise.domain.household.HouseholdService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.example.mise.ui.plan.PlanView;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

/**
 * BR-01: If a {@link Household} row already exists, navigating to {@code /welcome} must
 * forward to {@code /plan} without rendering the onboarding chat panel.
 *
 * <p>Lifecycle: a {@code @BeforeEach} seeds the household and each test navigates afterwards, so the
 * {@code BeforeEnterObserver} in {@link com.example.mise.ui.onboarding.OnboardingView} finds an
 * existing row and fires the redirect before the page renders.
 *
 * <p>Cleanup: {@code @AfterEach} deletes all household rows so the shared in-memory H2 and
 * Spring context stay clean for other ITs. {@code @DirtiesContext} is not needed.
 */
@ActiveProfiles("it")
@Import(TestAiConfig.class)
@UnifiedSpringBootTest
class OnboardingRedirectIT {

    @Autowired
    TestChatModel chatModel;

    @BeforeEach
    void resetChatModelBetweenTests() {
        chatModel.reset();
    }

    @Autowired
    private HouseholdService householdService;

    @Autowired
    private HouseholdRepository householdRepository;

    /** Seeds the Household; each test navigates afterwards, so BeforeEnterObserver finds it. */
    @BeforeEach
    void seedBeforeNavigation() throws Exception {
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
    @DisplayName("A user who already has a household is sent from the welcome page to the plan")
    void existingHouseholdRedirectsToPlan(UnifiedPage page) {
        // when
        page.navigate("/welcome");

        // then
        assertThat(page).isAt(PlanView.class);
    }
}
