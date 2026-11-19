package com.example.mise.it;

import com.example.mise.ui.reports.ReportsView;
import com.example.mise.it.support.TestChatModel;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import com.example.mise.it.support.TestAiConfig;

import com.microsoft.playwright.Page;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.example.mise.ai.tools.ReportingTools;
import com.example.mise.domain.preferences.ViewPreferenceService;
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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedGrid;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

/**
 * UC-007 Playwright IT — Reports dashboard view at /reports.
 *
 * <p>Lifecycle: a {@code @BeforeEach} seeds a Household, 4 HISTORICAL plans (to satisfy
 * the "≥4 weeks of data" AC #1), and one ACTIVE plan for the current week; each test then
 * opens /reports. AC-level assertions cover the deterministic UI surface only; LLM-driven
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
@ActiveProfiles("it")
@Import(TestAiConfig.class)
@UnifiedSpringBootTest
class ReportsViewIT {


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
     * Seeds Household + 4 HISTORICAL plans + 1 ACTIVE plan before each test opens /reports.
     * Seeding history satisfies AC #1 ("≥4 weeks of data").
     */
    @BeforeEach
    void seedBeforeNavigation() throws Exception {
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
    @DisplayName("The reports page has its browser tab title")
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void hasPageTitle(UnifiedPage page) {
        // Escape Hatch kept: the Unified API has no page-title assertion
        Page playwright = page.unwrap(Page.class);
        page.navigate(ReportsView.class);
        com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(playwright).hasTitle("Mise — Reports");
    }

    /**
     * AC #1: the root reports container is visible.
     */
    @Test
    @DisplayName("The reports page opens")
    void reportsViewContainerIsVisible(UnifiedPage page) {
        page.navigate(ReportsView.class);
        assertThat(page.find(UnifiedContainer.class).withTestId("reports-view")).isVisible();
    }

    // ── AC #1: all three widgets render ───────────────────────────────────────

    /**
     * AC #1: the weekly cost-trend chart container is visible.
     */
    @Test
    @DisplayName("The reports page shows the weekly cost trend")
    void costTrendWidgetIsVisible(UnifiedPage page) {
        page.navigate(ReportsView.class);
        assertThat(widget(page, "report-cost-trend")).isVisible();
    }

    /**
     * AC #1: the cost-by-category chart container is visible.
     */
    @Test
    @DisplayName("The reports page shows the cost by category")
    void categoryBreakdownWidgetIsVisible(UnifiedPage page) {
        page.navigate(ReportsView.class);
        assertThat(widget(page, "report-category-breakdown")).isVisible();
    }

    /**
     * AC #1: the per-meal leaderboard grid is visible.
     */
    @Test
    @DisplayName("The reports page shows the meal leaderboard")
    void leaderboardWidgetIsVisible(UnifiedPage page) {
        page.navigate(ReportsView.class);
        assertThat(leaderboard(page)).isVisible();
    }

    /**
     * AC #1 composite: all three widget containers are visible in a single check.
     * This mirrors the "allThreeWidgetsRender" specification item.
     */
    @Test
    @DisplayName("The reports page shows all three widgets")
    void allThreeWidgetsRender(UnifiedPage page) {
        page.navigate(ReportsView.class);
        assertThat(widget(page, "report-cost-trend")).isVisible();
        assertThat(widget(page, "report-category-breakdown")).isVisible();
        assertThat(leaderboard(page)).isVisible();
    }

    // ── AC #1: leaderboard has rows ───────────────────────────────────────────

    /**
     * AC #1: given 4 historical + 1 active plan (35 meals total), the leaderboard Grid
     * must have at least 1 row.
     */
    @Test
    @DisplayName("With meal history, the leaderboard lists meals")
    void leaderboardGridHasRows(UnifiedPage page) {
        page.navigate(ReportsView.class);
        assertThat(leaderboard(page)).isVisible();
        assertThat(leaderboard(page).row(0)).exists();
    }

    // ── UC-012 BR-10: persisted query restores into the grid ─────────────────

    /**
     * BR-10 (state seam): a ViewPreference row holding a custom leaderboard SQL query
     * must be restored into the Grid on load — the custom column header appears.
     */
    @Test
    @DisplayName("A saved leaderboard customization is restored when the page is reloaded")
    void persistedLeaderboardQueryRestoresOnLoad(UnifiedPage page) {
        // given
        page.navigate(ReportsView.class);
        saveLeaderboardQuery("SELECT recipe_name AS \"Meal\", COUNT(*) AS \"Cooked times\" "
                + "FROM meal_history GROUP BY recipe_name ORDER BY COUNT(*) DESC");

        // when
        page.reload();

        // then
        waitForLeaderboardColumn(page, "Cooked times");

        // a second reload: the customization survives (persistence, not session state)
        page.reload();
        waitForLeaderboardColumn(page, "Cooked times");
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
    @DisplayName("A saved kcal-per-euro leaderboard column is restored when the page is reloaded")
    void leaderboardKcalPerEuroColumnRestoresOnLoad(UnifiedPage page) {
        // given
        page.navigate(ReportsView.class);
        saveLeaderboardQuery("SELECT recipe_name AS \"Meal\", COUNT(*) AS \"Times\", "
                + "ROUND(AVG(CAST(kcal_per_serving AS DOUBLE)) "
                + "/ AVG(CAST(est_cost_eur AS DOUBLE)), 1) AS \"kcal per euro\" "
                + "FROM meal_history GROUP BY recipe_name ORDER BY 3 DESC");

        // when
        page.reload();

        // then
        waitForLeaderboardColumn(page, "kcal per euro");

        // a second reload: the derived column survives (persistence, not session state)
        page.reload();
        waitForLeaderboardColumn(page, "kcal per euro");
    }

    // ── UC-012 BR-10: reset drops the persisted state ─────────────────────────

    /**
     * BR-10 (reset seam): {@link ReportingTools#resetReportsWidget} deletes the
     * ViewPreference row; after reload the widget is back on its default query.
     */
    @Test
    @DisplayName("Resetting the leaderboard brings back its default columns")
    void resetWidgetRevertsToDefaultQuery(UnifiedPage page) {
        // given
        page.navigate(ReportsView.class);
        saveLeaderboardQuery("SELECT recipe_name AS \"Meal\", COUNT(*) AS \"Cooked times\" "
                + "FROM meal_history GROUP BY recipe_name ORDER BY COUNT(*) DESC");
        page.reload();
        waitForLeaderboardColumn(page, "Cooked times");

        // when
        reportingTools.resetReportsWidget("leaderboard");
        page.reload();

        // then: the default query's header is back
        waitForLeaderboardColumn(page, "Times");
        Assertions.assertThat(leaderboard(page).getColumnHeaders()).doesNotContain("Cooked times");
    }

    // ── Reset buttons are rendered (UI surface) ───────────────────────────────

    /**
     * AC: the per-widget reset buttons are present (always rendered —
     * see {@code widgetShell(...)} in ReportsView).
     */
    @Test
    @DisplayName("Each customizable widget has a reset button")
    void resetButtonsArePresentForCustomisableWidgets(UnifiedPage page) {
        page.navigate(ReportsView.class);
        assertThat(page.find(UnifiedButton.class).withTestId("report-reset-trendChart")).exists();
        assertThat(page.find(UnifiedButton.class).withTestId("report-reset-categoryChart")).exists();
        assertThat(page.find(UnifiedButton.class).withTestId("report-reset-leaderboard")).exists();
    }

    private static UnifiedContainer widget(UnifiedPage page, String testId) {
        return page.find(UnifiedContainer.class).withTestId(testId);
    }

    private static UnifiedGrid leaderboard(UnifiedPage page) {
        return page.find(UnifiedGrid.class).withTestId("leaderboard-grid");
    }

    /** The Unified API has no column-header assertion: wait until the header is there. */
    private static void waitForLeaderboardColumn(UnifiedPage page, String header) {
        UnifiedGrid leaderboard = leaderboard(page);
        page.waitUntil(() -> leaderboard.getColumnHeaders().contains(header));
    }

    private void saveLeaderboardQuery(String query) {
        var household = householdService.findHousehold().orElseThrow();
        viewPreferenceService.saveSettings(household.getId(), ViewPreference.View.REPORTS,
                "leaderboard", java.util.Map.of("query", query));
    }
}
