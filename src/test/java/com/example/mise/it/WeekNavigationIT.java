package com.example.mise.it;

import com.example.mise.capabilities.recipes.RecipeCatalog;
import com.example.mise.domain.conversation.ConversationMessageRepository;
import com.example.mise.domain.household.Household;
import com.example.mise.domain.household.HouseholdRepository;
import com.example.mise.domain.household.HouseholdService;
import com.example.mise.domain.plan.Meal;
import com.example.mise.domain.plan.MealEditRepository;
import com.example.mise.domain.plan.MealRepository;
import com.example.mise.domain.plan.Plan;
import com.example.mise.domain.plan.PlanRepository;
import com.example.mise.domain.plan.PlanService;
import com.example.mise.it.support.TestAiConfig;
import com.example.mise.it.support.TestChatModel;
import com.example.mise.ui.plan.PlanView;
import com.example.mise.ui.shopping.ShoppingView;

import com.vaadin.flow.router.QueryParameters;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedDatePicker;
import com.vaadin.testing.unified.component.UnifiedDiv;
import com.vaadin.testing.unified.component.UnifiedSpan;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

/**
 * UC-010 Playwright IT — week navigation header controls (prev / next / badge / DatePicker).
 *
 * <p>Seeding strategy: 3 plans per test —
 * <ul>
 *   <li>HISTORICAL, weekStart = today − 14 days (oldest)</li>
 *   <li>HISTORICAL, weekStart = today − 7 days (middle)</li>
 *   <li>ACTIVE,     weekStart = today's Monday (newest)</li>
 * </ul>
 * Each plan gets 7 Meal rows so PlanView renders a full meal grid and so per-plan
 * recipe-ref assertions are deterministic.
 *
 * <p>Cleanup in {@code @AfterEach} deletes rows in FK-safe order:
 * meal_edit → conversation_message → meal → plan → household.
 *
 * <p><b>Pre-onboarding note:</b> The week-nav controls (prev/next/badge) live in
 * {@code MainLayout}, which is <em>not</em> the layout for {@code /welcome}. When there
 * is no household {@code PlanView.beforeEnter()} immediately redirects to {@code /welcome}
 * (a standalone layout), so the MainLayout header never renders. The pre-onboarding
 * disabled-button and placeholder-label behaviours cannot therefore be asserted at the
 * Playwright IT layer — they are verified at the unit / browserless layer instead.
 */
@UnifiedSpringBootTest
@ActiveProfiles("it")
@Import(TestAiConfig.class)
class WeekNavigationIT {

    @Autowired
    protected TestChatModel chatModel;

    @BeforeEach
    void resetChatModelBetweenTests() {
        chatModel.reset();
    }

    private static final DateTimeFormatter WEEK_FMT = DateTimeFormatter.ofPattern("MMM d");

    // ── Spring beans ──────────────────────────────────────────────────────────

    @Autowired
    private HouseholdService householdService;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private MealEditRepository mealEditRepository;

    @Autowired
    private ConversationMessageRepository conversationMessageRepository;

    @Autowired
    private RecipeCatalog recipeCatalog;

    // ── Seeded plan references ────────────────────────────────────────────────

    /** The oldest historical plan (today − 14 days). */
    private Plan oldestPlan;
    /** Middle historical plan (today − 7 days). */
    private Plan middlePlan;
    /** The active plan (today's Monday). */
    private Plan activePlan;

    /** Seeds 3 plans (oldest HISTORICAL, middle HISTORICAL, active ACTIVE) with 7 meals each. */
    @BeforeEach
    void seed() {
        seedHousehold();
    }

    @AfterEach
    void cleanUp() {
        mealEditRepository.deleteAll();
        conversationMessageRepository.deleteAll();
        householdRepository.findAll().forEach(hh ->
                planRepository.findByHouseholdIdOrderByWeekStartDateDesc(hh.getId())
                        .forEach(plan -> {
                            mealRepository.deleteAll(mealRepository.findByPlanId(plan.getId()));
                            planRepository.delete(plan);
                        })
        );
        householdRepository.deleteAll();
        oldestPlan = null;
        middlePlan = null;
        activePlan = null;
    }

    // ── Seeding helpers ───────────────────────────────────────────────────────

    private void seedHousehold() {
        Household h = new Household();
        h.setName("IT Week Nav Household");
        h.setSize(2);
        h.setWeeklyBudget(new BigDecimal("100.00"));
        h.setAllergies(List.of());
        h.setHatedFoods(List.of());
        householdService.save(h);

        var household = householdService.findHousehold().orElseThrow();
        LocalDate activeMonday = PlanService.currentWeekMonday();

        oldestPlan = seedPlan(household, activeMonday.minusWeeks(2), Plan.Status.HISTORICAL);
        middlePlan = seedPlan(household, activeMonday.minusWeeks(1), Plan.Status.HISTORICAL);
        activePlan = seedPlan(household, activeMonday,               Plan.Status.ACTIVE);
    }

    /** Creates a Plan + 7 Meal rows using the recipe catalog to populate recipeRef. */
    private Plan seedPlan(Household household, LocalDate weekStart, Plan.Status status) {
        var plan = new Plan();
        plan.setHouseholdId(household.getId());
        plan.setWeekStartDate(weekStart);
        plan.setStatus(status);
        plan = planRepository.save(plan);

        var recipes = recipeCatalog.findAll();
        for (int day = 0; day < 7; day++) {
            var meal = new Meal();
            meal.setPlanId(plan.getId());
            meal.setDate(weekStart.plusDays(day));
            meal.setSlot(Meal.Slot.DINNER);
            meal.setServings(household.getSize());
            meal.setStatus(status == Plan.Status.ACTIVE ? Meal.Status.PLANNED : Meal.Status.COOKED);
            meal.setLastEditedBy(Meal.Editor.USER);
            meal.setRecipeRef(recipes.get(day % recipes.size()).getId());
            mealRepository.save(meal);
        }
        return plan;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * AC: On the ACTIVE plan's week the header renders with prev enabled and next disabled.
     */
    @Test
    @DisplayName("On the active week, Previous week is enabled and Next week disabled")
    void activeWeekHasPrevEnabledAndNextDisabled(UnifiedPage page) {
        // Default navigation lands on the active plan (/plan without ?week=)
        page.navigate(PlanView.class);

        // Prev enabled — there are 2 earlier plans; next disabled — no plan after active
        assertThat(prevWeek(page)).isVisible().isEnabled();
        assertThat(nextWeek(page)).isVisible().isDisabled();
    }

    /**
     * AC: The week badge shows the active week's Monday formatted as "Week of {MMM d}".
     */
    @Test
    @DisplayName("The week badge shows the active week")
    void activeWeekBadgeShowsActivePlanMonday(UnifiedPage page) {
        page.navigate(PlanView.class);

        assertThat(weekBadge(page)).hasTextContaining(badgeFor(activePlan));
    }

    /**
     * AC: Clicking prev once changes the badge to show the previous (middle) plan's Monday.
     */
    @Test
    @DisplayName("Previous week shows the week before")
    void clickPrevOnceShowsMiddleWeekInBadge(UnifiedPage page) {
        page.navigate(PlanView.class);

        prevWeek(page).click();

        assertThat(weekBadge(page)).hasTextContaining(badgeFor(middlePlan));
    }

    /**
     * AC: After clicking prev once, the ?week= param appears in the URL.
     */
    @Test
    @DisplayName("Previous week puts the week in the URL")
    void weekParamAppearsInUrlAfterPrevClick(UnifiedPage page) {
        page.navigate(PlanView.class);

        prevWeek(page).click();

        assertThat(weekBadge(page)).hasTextContaining(badgeFor(middlePlan));
        assertThat(page).hasQueryParameter("week", middlePlan.getWeekStartDate().toString());
    }

    /**
     * AC: Clicking prev twice (from active) lands on the oldest plan; prev is disabled, next is enabled.
     */
    @Test
    @DisplayName("Previous week twice lands on the oldest week, where only Next week is enabled")
    void clickPrevTwiceLandsOnOldestPlan(UnifiedPage page) {
        // given
        page.navigate(PlanView.class);
        prevWeek(page).click();
        assertThat(weekBadge(page)).hasTextContaining(badgeFor(middlePlan));

        // when
        prevWeek(page).click();

        // then
        assertThat(weekBadge(page)).hasTextContaining(badgeFor(oldestPlan));
        assertThat(prevWeek(page)).isDisabled();
        assertThat(nextWeek(page)).isEnabled();
    }

    /**
     * AC: After navigating to the oldest plan, clicking next returns to the middle plan.
     */
    @Test
    @DisplayName("Next week from the oldest week goes forward one week")
    void nextButtonNavigatesForwardAfterReachingOldest(UnifiedPage page) {
        // given: on the oldest week
        page.navigate(PlanView.class);
        prevWeek(page).click();
        assertThat(weekBadge(page)).hasTextContaining(badgeFor(middlePlan));
        prevWeek(page).click();
        assertThat(weekBadge(page)).hasTextContaining(badgeFor(oldestPlan));

        // when
        nextWeek(page).click();

        // then
        assertThat(weekBadge(page)).hasTextContaining(badgeFor(middlePlan));
    }

    /**
     * AC: The ?week= param survives a page reload — the badge still shows the same week.
     */
    @Test
    @DisplayName("The shown week survives a reload")
    void weekParamPersistsAcrossReload(UnifiedPage page) {
        // given: on the middle week
        page.navigate(PlanView.class);
        prevWeek(page).click();
        assertThat(weekBadge(page)).hasTextContaining(badgeFor(middlePlan));
        assertThat(page).hasQueryParameter("week", middlePlan.getWeekStartDate().toString());

        // when
        page.reload();

        // then
        assertThat(weekBadge(page)).hasTextContaining(badgeFor(middlePlan));
    }

    /**
     * AC: Clicking the week badge opens the DatePicker overlay.
     */
    @Test
    @DisplayName("Clicking the week badge opens the date picker")
    void clickingBadgeOpensDatePickerOverlay(UnifiedPage page) {
        page.navigate(PlanView.class);

        weekBadge(page).click();

        assertThat(weekPicker(page)).hasProperty("opened", true);
    }

    /**
     * AC: Picking a Wednesday from the DatePicker snaps to that week's Monday (BR-07).
     * The badge must show the Monday after picking a Wednesday.
     */
    @Test
    @DisplayName("Picking a Wednesday shows the week starting that Monday")
    void pickingWednesdaySnapsToMonday(UnifiedPage page) {
        // given: the picker open
        page.navigate(PlanView.class);
        weekBadge(page).click();
        assertThat(weekPicker(page)).hasProperty("opened", true);

        // when: a Wednesday of the oldest plan's week is picked
        pickDate(page, oldestPlan.getWeekStartDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY)));

        // then: the badge shows that week's Monday, not the Wednesday
        assertThat(weekBadge(page)).hasTextContaining(badgeFor(oldestPlan));
    }

    /**
     * AC: Picking the same week the user is currently viewing is a no-op — the URL doesn't change.
     */
    @Test
    @DisplayName("Picking the week already shown changes nothing")
    void pickingSameWeekIsNoOp(UnifiedPage page) {
        // given: on the middle week, the picker open
        page.navigate(PlanView.class);
        prevWeek(page).click();
        assertThat(weekBadge(page)).hasTextContaining(badgeFor(middlePlan));
        weekBadge(page).click();
        assertThat(weekPicker(page)).hasProperty("opened", true);

        // when: the Monday already shown is picked
        pickDate(page, middlePlan.getWeekStartDate());

        // then: still on the same week and URL
        assertThat(page).isAt(PlanView.class)
                .hasQueryParameter("week", middlePlan.getWeekStartDate().toString());
        assertThat(weekBadge(page)).hasTextContaining(badgeFor(middlePlan));
    }

    /**
     * AC: Tab links preserve the ?week= param when switching between Plan and Shopping.
     * After clicking prev on /plan (landing on /plan?week=X), clicking the Shopping tab
     * must navigate to /shopping?week=X.
     */
    @Test
    @DisplayName("Switching to Shopping keeps the shown week")
    void tabLinkPreservesWeekParam(UnifiedPage page) {
        // given: on the middle week
        page.navigate(PlanView.class);
        prevWeek(page).click();
        String weekStr = middlePlan.getWeekStartDate().toString();
        assertThat(weekBadge(page)).hasTextContaining(badgeFor(middlePlan));
        assertThat(page).hasQueryParameter("week", weekStr);

        // when: the Shopping tab (a Div with a click listener)
        page.find(UnifiedDiv.class).withClassName("mise-tab").withText("Shopping").click();

        // then
        assertThat(page).isAt(ShoppingView.class).hasTitle("Mise — Shopping")
                .hasQueryParameter("week", weekStr);
    }

    /**
     * AC: Navigating directly to /plan?week=<old-monday> renders that older plan's meals
     * in the PlanView (7 meal rows belonging to oldestPlan).
     */
    @Test
    @DisplayName("Opening an older week directly shows its seven meals")
    void directWeekUrlRendersOlderPlanMeals(UnifiedPage page) {
        openWeek(page, oldestPlan);

        assertThat(weekBadge(page)).hasTextContaining(badgeFor(oldestPlan));
        assertThat(page.find(UnifiedDiv.class).withTestId("meal-row")).hasCount(7);
    }

    /**
     * AC: Clicking the brand wordmark navigates to /plan (active week, no ?week= param).
     */
    @Test
    @DisplayName("The brand wordmark goes back to the active week")
    void brandWordmarkNavigatesToActiveWeek(UnifiedPage page) {
        // given: on the oldest week
        openWeek(page, oldestPlan);
        assertThat(weekBadge(page)).hasTextContaining(badgeFor(oldestPlan));

        // when
        page.find(UnifiedDiv.class).withTestId("app-header-wordmark").click();

        // then: the active week, on a clean URL
        assertThat(weekBadge(page)).hasTextContaining(badgeFor(activePlan));
        assertThat(page).hasQueryParameter("week", null);
    }

    // ── Lookups ──────────────────────────────────────────────────────────────

    private static void openWeek(UnifiedPage page, Plan plan) {
        page.navigate(PlanView.class, QueryParameters.of("week", plan.getWeekStartDate().toString()));
    }

    private static String badgeFor(Plan plan) {
        return "Week of " + plan.getWeekStartDate().format(WEEK_FMT);
    }

    private static UnifiedButton prevWeek(UnifiedPage page) {
        return page.find(UnifiedButton.class).withAccessibleName("Previous week");
    }

    private static UnifiedButton nextWeek(UnifiedPage page) {
        return page.find(UnifiedButton.class).withAccessibleName("Next week");
    }

    private static UnifiedSpan weekBadge(UnifiedPage page) {
        return page.find(UnifiedSpan.class).withId("mise-week-badge");
    }

    /**
     * Picks a date in the open week picker. The picker's text field is hidden by CSS (only
     * its calendar overlay shows), so {@code setValue(..)}, which types into the field as a
     * user would, finds it hidden in a browser; and the overlay's calendar has no Wrapper.
     * Setting the picker's {@code value} property is what a pick in the calendar does, on
     * every Testing Tool.
     */
    private static void pickDate(UnifiedPage page, LocalDate date) {
        page.step("Pick " + date + " in the week picker", () ->
                weekPicker(page).element().setProperty("value", date.toString()));
    }

    private static UnifiedDatePicker weekPicker(UnifiedPage page) {
        return page.find(UnifiedDatePicker.class).withId("mise-week-datepicker");
    }
}
