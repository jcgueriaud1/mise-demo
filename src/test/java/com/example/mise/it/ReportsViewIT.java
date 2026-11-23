package com.example.mise.it;

import com.example.mise.ai.tools.ReportingTools;
import com.example.mise.domain.preferences.ViewPreferenceService;
import com.example.mise.it.support.TestAiConfig;
import com.example.mise.it.support.TestChatModel;
import com.example.mise.ui.reports.ReportsView;
import com.example.mise.capabilities.recipes.RecipeCatalog;
import com.example.mise.domain.conversation.ConversationMessageRepository;
import com.example.mise.domain.household.Household;
import com.example.mise.domain.household.HouseholdRepository;
import com.example.mise.domain.household.HouseholdService;
import com.example.mise.domain.plan.MealEditRepository;
import com.example.mise.domain.plan.MealRepository;
import com.example.mise.domain.plan.PlanRepository;
import com.example.mise.domain.plan.PlanService;
import com.example.mise.domain.preferences.ViewPreference;
import com.example.mise.domain.preferences.ViewPreferenceRepository;

import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * UC-007 Playwright IT — Reports dashboard view at /reports.
 *
 * <p>Lifecycle: {@code setupTest()} seeds a Household, 4 HISTORICAL plans (to satisfy
 * the "≥4 weeks of data" AC #1), and one ACTIVE plan for the current week, then navigates
 * to /reports. AC-level assertions cover the deterministic UI surface only; LLM-driven
 * paths (natural-language chat triggers) are deferred to Phase 5a.
 *
 * <p>State-seam tests exercise the UC-012 persistence path directly: a saved
 * {@code ViewPreference} query must restore into the controller-driven widgets on load
 * (BR-10), and {@link ReportingTools#resetReportsWidget} must drop it again. The
 * LLM-driven reshape path is verified against the live model, not here.
 *
 * <p>Cleanup in {@code @AfterEach} deletes rows in FK-safe order:
 * meal_edit → view_preference → conversation_message → meal → plan → household.
 */
@UnifiedSpringBootTest
@ActiveProfiles("it")
@Import(TestAiConfig.class)
class ReportsViewIT {

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

    @Autowired
    private PlanService planService;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private MealEditRepository mealEditRepository;

    @Autowired
    private RecipeCatalog recipeCatalog;

    @Autowired
    private ConversationMessageRepository conversationMessageRepository;

    @Autowired
    private ViewPreferenceRepository viewPreferenceRepository;

    @Autowired
    private ReportingTools reportingTools;

    @Autowired
    private ViewPreferenceService viewPreferenceService;

    /**
     * Override: seed Household + 4 HISTORICAL plans + 1 ACTIVE plan BEFORE the base class
     * navigates to /reports. Seeding history satisfies AC #1 ("≥4 weeks of data").
     */
    @BeforeEach
    public void setupTest() throws Exception {
        Household h = new Household();
        h.setName("IT Reports Household");
        h.setSize(2);
        h.setWeeklyBudget(new BigDecimal("100.00"));
        h.setAllergies(List.of());
        h.setHatedFoods(List.of());
        householdService.save(h);

        var household = householdService.findHousehold().orElseThrow();

        // Seed 4 HISTORICAL plans so the cost-trend and leaderboard have data
        planService.seedHistory(household, 4, recipeCatalog);

        // Seed the current week's ACTIVE plan
        planService.generateActivePlan(household, recipeCatalog);
    }

    @AfterEach
    void cleanUp() {
        // FK-safe order: meal_edit → view_preference → conversation_message → meal → plan → household
        mealEditRepository.deleteAll();
        viewPreferenceRepository.deleteAll();
        conversationMessageRepository.deleteAll();
        householdRepository.findAll().forEach(hh ->
                planRepository.findByHouseholdIdOrderByWeekStartDateDesc(hh.getId())
                        .forEach(plan -> {
                            mealRepository.deleteAll(mealRepository.findByPlanId(plan.getId()));
                            planRepository.delete(plan);
                        })
        );
        householdRepository.deleteAll();
    }

    // ── AC #1: route and page title ───────────────────────────────────────────

    /**
     * AC #1: /reports loads with the correct page title.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void hasPageTitle(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate(ReportsView.class);
        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright).hasTitle("Mise — Reports")
        assertThat(playwright).hasTitle("Mise — Reports");
    }

    /**
     * AC #1: the root reports container is visible.
     */
    @Test
    void reportsViewContainerIsVisible(UnifiedPage page) {
        page.navigate(ReportsView.class);
        com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat(page.find(UnifiedContainer.class).withTestId("reports-view")).isVisible();
    }

    // ── AC #1: all three widgets render ───────────────────────────────────────

    /**
     * AC #1: the weekly cost-trend chart container is visible.
     */
    @Test
    void costTrendWidgetIsVisible(UnifiedPage page) {
        page.navigate(ReportsView.class);
        com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat(page.find(UnifiedContainer.class).withTestId("report-cost-trend")).isVisible();
    }

    /**
     * AC #1: the cost-by-category chart container is visible.
     */
    @Test
    void categoryBreakdownWidgetIsVisible(UnifiedPage page) {
        page.navigate(ReportsView.class);
        com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat(page.find(UnifiedContainer.class).withTestId("report-category-breakdown")).isVisible();
    }

    /**
     * AC #1: the per-meal leaderboard grid is visible.
     */
    @Test
    void leaderboardWidgetIsVisible(UnifiedPage page) {
        page.navigate(ReportsView.class);
        com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat(page.find(UnifiedContainer.class).withTestId("leaderboard-grid")).isVisible();
    }

    /**
     * AC #1 composite: all three widget containers are visible in a single check.
     * This mirrors the "allThreeWidgetsRender" specification item.
     */
    @Test
    void allThreeWidgetsRender(UnifiedPage page) {
        page.navigate(ReportsView.class);
        com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat(page.find(UnifiedContainer.class).withTestId("report-cost-trend")).isVisible();
        com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat(page.find(UnifiedContainer.class).withTestId("report-category-breakdown")).isVisible();
        com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat(page.find(UnifiedContainer.class).withTestId("leaderboard-grid")).isVisible();
    }

    // ── AC #1: leaderboard has rows ───────────────────────────────────────────

    /**
     * AC #1: given 4 historical + 1 active plan (35 meals total), the leaderboard Grid
     * must have at least 1 rendered row.
     *
     * <p>Vaadin Grid renders data rows inside {@code vaadin-grid-cell-content} elements.
     * We assert the grid is non-empty by checking {@code vaadin-grid-cell-content} count
     * is ≥ 1 scoped to the leaderboard container.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void leaderboardGridHasRows(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate(ReportsView.class);
        // TODO unified: Dramafinder Escape Hatch: playwright.getByTestId("leaderboard-grid")
        var leaderboardGrid = playwright.getByTestId("leaderboard-grid");
        // TODO unified: Dramafinder Escape Hatch: assertThat(leaderboardGrid).isVisible()
        assertThat(leaderboardGrid).isVisible();
        // Vaadin Grid renders row cells as vaadin-grid-cell-content inside the grid element
        // TODO unified: Dramafinder Escape Hatch: leaderboardGrid.locator("vaadin-grid-cell-content").count()
        int cellCount = leaderboardGrid.locator("vaadin-grid-cell-content").count();
        Assertions.assertThat(cellCount)
                .as("Leaderboard must have at least 1 rendered cell (AC #1: ≥1 row)")
                .isGreaterThanOrEqualTo(1);
    }

    // ── UC-012 BR-10: persisted query restores into the grid ─────────────────

    /**
     * BR-10 (state seam): a ViewPreference row holding a custom leaderboard SQL query
     * must be restored into the Grid on load — the custom column header appears.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void persistedLeaderboardQueryRestoresOnLoad(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate(ReportsView.class);
        var household = householdService.findHousehold().orElseThrow();
        viewPreferenceService.saveSettings(household.getId(), ViewPreference.View.REPORTS,
                "leaderboard", java.util.Map.of("query",
                        "SELECT recipe_name AS \"Meal\", COUNT(*) AS \"Cooked times\" "
                        + "FROM meal_history GROUP BY recipe_name ORDER BY COUNT(*) DESC"));

        page.navigate("reports");
        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright.getByTestId("leaderboard-grid").getByText("Cooked times")).isVisible()
        assertThat(playwright.getByTestId("leaderboard-grid").getByText("Cooked times")).isVisible();

        // Second reload — the customization survives (persistence, not session state)
        page.navigate("reports");
        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright.getByTestId("leaderboard-grid").getByText("Cooked times")).isVisible()
        assertThat(playwright.getByTestId("leaderboard-grid").getByText("Cooked times")).isVisible();
    }

    // ── UC-008 AC #2 / UC-012: derived kcal-per-euro leaderboard column ───────

    /**
     * UC-008 AC #2, ported from the retired
     * {@code NavigationToolsAIIT#crossViewCommandChainsGoToWithTargetTool}.
     *
     * <p>Adding a kcal-per-euro column to the leaderboard is no longer a standalone
     * {@code addLeaderboardColumn} tool (removed in UC-012). The reshape is now a
     * {@code GridAIController} ({@code update_grid_data}) action that persists a
     * leaderboard SQL query into {@code ViewPreference}. This verifies the state seam
     * that controller writes to: a persisted query deriving kcal-per-euro
     * (avg kcal ÷ avg cost — the old tool's BR-03 semantics) restores into the Grid on
     * load, with the derived-column header rendered, and survives a reload.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void leaderboardKcalPerEuroColumnRestoresOnLoad(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate(ReportsView.class);
        var household = householdService.findHousehold().orElseThrow();
        viewPreferenceService.saveSettings(household.getId(), ViewPreference.View.REPORTS,
                "leaderboard", java.util.Map.of("query",
                        "SELECT recipe_name AS \"Meal\", COUNT(*) AS \"Times\", "
                        + "ROUND(AVG(CAST(kcal_per_serving AS DOUBLE)) "
                        + "/ AVG(CAST(est_cost_eur AS DOUBLE)), 1) AS \"kcal per euro\" "
                        + "FROM meal_history GROUP BY recipe_name ORDER BY 3 DESC"));

        page.navigate("reports");
        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright.getByTestId("leaderboard-grid").getByText("kcal per euro")).isVisible()
        assertThat(playwright.getByTestId("leaderboard-grid").getByText("kcal per euro")).isVisible();

        // Second reload — the derived column survives (persistence, not session state).
        page.navigate("reports");
        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright.getByTestId("leaderboard-grid").getByText("kcal per euro")).isVisible()
        assertThat(playwright.getByTestId("leaderboard-grid").getByText("kcal per euro")).isVisible();
    }

    // ── UC-012 BR-10: reset drops the persisted state ─────────────────────────

    /**
     * BR-10 (reset seam): {@link ReportingTools#resetReportsWidget} deletes the
     * ViewPreference row; after reload the widget is back on its default query.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void resetWidgetRevertsToDefaultQuery(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate(ReportsView.class);
        var household = householdService.findHousehold().orElseThrow();
        viewPreferenceService.saveSettings(household.getId(), ViewPreference.View.REPORTS,
                "leaderboard", java.util.Map.of("query",
                        "SELECT recipe_name AS \"Meal\", COUNT(*) AS \"Cooked times\" "
                        + "FROM meal_history GROUP BY recipe_name ORDER BY COUNT(*) DESC"));

        page.navigate("reports");
        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright.getByTestId("leaderboard-grid").getByText("Cooked times")).isVisible()
        assertThat(playwright.getByTestId("leaderboard-grid").getByText("Cooked times")).isVisible();

        reportingTools.resetReportsWidget("leaderboard");

        page.navigate("reports");
        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright.getByTestId("leaderboard-grid").getByText("Cooked times")).not().isVisible()
        assertThat(playwright.getByTestId("leaderboard-grid").getByText("Cooked times")).not().isVisible();
        // Default query header is back
        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright.getByTestId("leaderboard-grid").getByText("Times")).isVisible()
        assertThat(playwright.getByTestId("leaderboard-grid").getByText("Times")).isVisible();
    }

    // ── Reset buttons are rendered (UI surface) ───────────────────────────────

    /**
     * AC: the per-widget reset buttons are present in the DOM (always rendered —
     * see {@code widgetShell(...)} in ReportsView).
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void resetButtonsArePresentForCustomisableWidgets(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate(ReportsView.class);
        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright.getByTestId("report-reset-trendChart")).isAttached()
        assertThat(playwright.getByTestId("report-reset-trendChart")).isAttached();
        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright.getByTestId("report-reset-categoryChart")).isAttached()
        assertThat(playwright.getByTestId("report-reset-categoryChart")).isAttached();
        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright.getByTestId("report-reset-leaderboard")).isAttached()
        assertThat(playwright.getByTestId("report-reset-leaderboard")).isAttached();
    }
}
