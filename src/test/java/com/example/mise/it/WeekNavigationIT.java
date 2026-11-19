package com.example.mise.it;

import com.example.mise.it.support.TestChatModel;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import com.example.mise.it.support.TestAiConfig;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import org.junit.jupiter.api.Test;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedDatePicker;
import com.vaadin.testing.unified.component.UnifiedDiv;
import com.vaadin.testing.unified.component.UnifiedSpan;
import org.springframework.beans.factory.annotation.Autowired;

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
@ActiveProfiles("it")
@Import(TestAiConfig.class)
@UnifiedSpringBootTest
class WeekNavigationIT {


    @Autowired
    TestChatModel chatModel;

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

    /**
     * Seeds 3 plans (oldest HISTORICAL, middle HISTORICAL, active ACTIVE) with 7 meals each,
     * then navigates to /plan so the header renders with all three plans present.
     */
    @BeforeEach
    void seedBeforeNavigation() throws Exception {
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
    @DisplayName("On the active week, previous week is available and next week isn't")
    void activeWeekHasPrevEnabledAndNextDisabled(UnifiedPage page) {
        // when: default navigation lands on the active plan (/plan without ?week=)
        page.navigate("/plan");

        // then: two earlier plans, none after the active one
        assertThat(previousWeekButton(page)).isVisible();
        assertThat(nextWeekButton(page)).isVisible();
        assertThat(previousWeekButton(page)).isEnabled();
        assertThat(nextWeekButton(page)).isDisabled();
    }

    /**
     * AC: The week badge shows the active week's Monday formatted as "Week of {MMM d}".
     */
    @Test
    @DisplayName("The week badge shows the active week")
    void activeWeekBadgeShowsActivePlanMonday(UnifiedPage page) {
        page.navigate("/plan");
        assertThat(weekBadge(page)).hasTextContaining(weekLabel(activePlan));
    }

    /**
     * AC: Clicking prev once changes the badge to show the previous (middle) plan's Monday.
     */
    @Test
    @DisplayName("Going back one week shows the previous week")
    void clickPrevOnceShowsMiddleWeekInBadge(UnifiedPage page) {
        page.navigate("/plan");
        previousWeekButton(page).click();
        assertThat(weekBadge(page)).hasTextContaining(weekLabel(middlePlan));
    }

    /**
     * AC: After clicking prev once, the ?week= param appears in the URL.
     */
    @Test
    @DisplayName("Going back one week puts that week in the URL")
    void weekParamAppearsInUrlAfterPrevClick(UnifiedPage page) {
        page.navigate("/plan");
        previousWeekButton(page).click();
        assertThat(weekBadge(page)).hasTextContaining(weekLabel(middlePlan));
        assertThat(page).hasQueryParameter("week", middlePlan.getWeekStartDate().toString());
    }

    /**
     * AC: Clicking prev twice (from active) lands on the oldest plan; prev is disabled, next is enabled.
     */
    @Test
    @DisplayName("Going back to the oldest week disables previous and enables next")
    void clickPrevTwiceLandsOnOldestPlan(UnifiedPage page) {
        // given
        page.navigate("/plan");

        // when
        goBackTwoWeeks(page);

        // then
        assertThat(previousWeekButton(page)).isDisabled();
        assertThat(nextWeekButton(page)).isEnabled();
    }

    /**
     * AC: After navigating to the oldest plan, clicking next returns to the middle plan.
     */
    @Test
    @DisplayName("From the oldest week, next week goes forward again")
    void nextButtonNavigatesForwardAfterReachingOldest(UnifiedPage page) {
        // given
        page.navigate("/plan");
        goBackTwoWeeks(page);

        // when
        nextWeekButton(page).click();

        // then
        assertThat(weekBadge(page)).hasTextContaining(weekLabel(middlePlan));
    }

    /**
     * AC: The ?week= param survives a page reload — the badge still shows the same week.
     */
    @Test
    @DisplayName("The chosen week is kept after a reload")
    void weekParamPersistsAcrossReload(UnifiedPage page) {
        // given
        page.navigate("/plan");
        previousWeekButton(page).click();
        assertThat(weekBadge(page)).hasTextContaining(weekLabel(middlePlan));
        assertThat(page).hasQueryParameter("week", middlePlan.getWeekStartDate().toString());

        // when
        page.reload();

        // then
        assertThat(weekBadge(page)).hasTextContaining(weekLabel(middlePlan));
    }

    /**
     * AC: Clicking the week badge opens the DatePicker overlay.
     */
    @Test
    @DisplayName("Clicking the week badge opens the week picker")
    void clickingBadgeOpensDatePickerOverlay(UnifiedPage page) {
        page.navigate("/plan");
        // a Span with a click listener: UnifiedSpan has no click() (findings F23)
        weekBadge(page).element().click();
        assertThat(weekPicker(page)).hasProperty("opened", true);
    }

    /**
     * AC: Picking a Wednesday from the DatePicker snaps to that week's Monday (BR-07).
     * The badge must show the Monday after picking a Wednesday.
     */
    @Test
    @DisplayName("Picking a Wednesday shows the week starting that Monday")
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void pickingWednesdaySnapsToMonday(UnifiedPage page) {
        // Escape Hatch kept: the picker's input is hidden by CSS and a user picks a day in the calendar
        // overlay, which the Unified API can't drive (UnifiedDatePicker.setValue refuses a hidden field in a
        // browser, rightly). Set the value as the overlay does, through the web component's property.
        Page playwright = page.unwrap(Page.class);
        // given
        page.navigate("/plan");
        LocalDate wednesday = oldestPlan.getWeekStartDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY));
        weekBadge(page).element().click();
        assertThat(weekPicker(page)).hasProperty("opened", true);

        // when
        playwright.locator("#mise-week-datepicker").evaluate("(picker, value) => { picker.value = value; }",
                wednesday.toString());

        // then: the badge shows the Monday of that week, not the Wednesday
        assertThat(weekBadge(page)).hasTextContaining(weekLabel(oldestPlan));
    }

    /**
     * AC: Picking the same week the user is currently viewing is a no-op — URL doesn't change.
     */
    @Test
    @DisplayName("Picking the week already shown changes nothing")
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void pickingSameWeekIsNoOp(UnifiedPage page) {
        // Escape Hatch kept: the picker's input is hidden by CSS and a user picks a day in the calendar
        // overlay, which the Unified API can't drive (UnifiedDatePicker.setValue refuses a hidden field in a
        // browser, rightly). Set the value as the overlay does, through the web component's property.
        Page playwright = page.unwrap(Page.class);
        // given
        page.navigate("/plan");
        previousWeekButton(page).click();
        assertThat(weekBadge(page)).hasTextContaining(weekLabel(middlePlan));
        weekBadge(page).element().click();
        assertThat(weekPicker(page)).hasProperty("opened", true);

        // when
        playwright.locator("#mise-week-datepicker").evaluate("(picker, value) => { picker.value = value; }",
                middlePlan.getWeekStartDate().toString());

        // then
        assertThat(page).hasPath("/plan");
        assertThat(page).hasQueryParameter("week", middlePlan.getWeekStartDate().toString());
        assertThat(weekBadge(page)).hasTextContaining(weekLabel(middlePlan));
    }

    /**
     * AC: Tab links preserve the ?week= param when switching between Plan and Shopping.
     */
    @Test
    @DisplayName("Switching to the shopping tab keeps the chosen week")
    void tabLinkPreservesWeekParam(UnifiedPage page) {
        // given: the middle (historical) week
        page.navigate("/plan");
        previousWeekButton(page).click();
        String weekStr = middlePlan.getWeekStartDate().toString();
        assertThat(page).hasQueryParameter("week", weekStr);

        // when
        // the tab is a Div with a click listener: UnifiedDiv has no click() (findings F23)
        page.find(UnifiedDiv.class).withClassName("mise-tab").withText("Shopping").element().click();

        // then
        assertThat(page).hasPath("/shopping");
        assertThat(page).hasQueryParameter("week", weekStr);
    }

    /**
     * AC: Navigating directly to /plan?week=<old-monday> renders that older plan's meals.
     */
    @Test
    @DisplayName("Opening an older week by URL shows its seven meals")
    void directWeekUrlRendersOlderPlanMeals(UnifiedPage page) {
        page.navigate("/plan?week=" + oldestPlan.getWeekStartDate());
        assertThat(weekBadge(page)).hasTextContaining(weekLabel(oldestPlan));
        assertThat(page.find(UnifiedDiv.class).withTestId("meal-row")).hasCount(7);
    }

    /**
     * AC: Clicking the brand wordmark navigates to /plan (active week, no ?week= param).
     */
    @Test
    @DisplayName("The Mise logo goes back to the active week")
    void brandWordmarkNavigatesToActiveWeek(UnifiedPage page) {
        // given: the oldest week
        page.navigate("/plan?week=" + oldestPlan.getWeekStartDate());
        assertThat(weekBadge(page)).hasTextContaining(weekLabel(oldestPlan));

        // when
        // the brand is a Div with a click listener: UnifiedDiv has no click() (findings F23)
        page.find(UnifiedDiv.class).withTestId("app-header-wordmark").element().click();

        // then: the active week, with a clean URL
        assertThat(weekBadge(page)).hasTextContaining(weekLabel(activePlan));
        assertThat(page).hasQueryParameter("week", null);
    }

    private static UnifiedButton previousWeekButton(UnifiedPage page) {
        return page.find(UnifiedButton.class).withAccessibleName("Previous week");
    }

    private static UnifiedButton nextWeekButton(UnifiedPage page) {
        return page.find(UnifiedButton.class).withAccessibleName("Next week");
    }

    private static UnifiedSpan weekBadge(UnifiedPage page) {
        return page.find(UnifiedSpan.class).withId("mise-week-badge");
    }

    private static UnifiedDatePicker weekPicker(UnifiedPage page) {
        return page.find(UnifiedDatePicker.class).withId("mise-week-datepicker");
    }

    private static String weekLabel(Plan plan) {
        return "Week of " + plan.getWeekStartDate().format(WEEK_FMT);
    }

    private void goBackTwoWeeks(UnifiedPage page) {
        page.step("Go back to the oldest week", () -> {
            previousWeekButton(page).click();
            assertThat(weekBadge(page)).hasTextContaining(weekLabel(middlePlan));
            previousWeekButton(page).click();
            assertThat(weekBadge(page)).hasTextContaining(weekLabel(oldestPlan));
        });
    }
}
