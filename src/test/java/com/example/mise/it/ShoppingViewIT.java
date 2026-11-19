package com.example.mise.it;

import org.springframework.boot.test.web.server.LocalServerPort;
import com.example.mise.it.support.TestChatModel;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
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
import com.example.mise.domain.plan.MealEditRepository;
import com.example.mise.domain.plan.MealRepository;
import com.example.mise.domain.plan.PlanRepository;
import com.example.mise.domain.plan.PlanService;
import com.example.mise.domain.preferences.ViewPreference;
import com.example.mise.domain.preferences.ViewPreferenceRepository;
import com.example.mise.domain.shopping.ExtraShoppingItemRepository;
import com.example.mise.domain.shopping.PantryItem;
import com.example.mise.domain.shopping.PantryRepository;
import com.example.mise.domain.shopping.PantryService;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * UC-005 Playwright IT — Shopping list view at /shopping.
 *
 * <p>Lifecycle: {@code setupTest()} seeds a Household and a seeded active plan before
 * the browser navigates to /shopping. The plan is generated from two specific recipes
 * (salmon-pasta + tuna-pasta) that share two ingredients (pasta, garlic) to enable
 * deterministic consolidation assertions (AC #2).
 *
 * <p>Cleanup in {@code @AfterEach} deletes rows in FK-safe order:
 * meal_edit → extra_shopping_item → pantry_item → view_preference →
 * conversation_message → meal → plan → household.
 */
@ActiveProfiles("it")
@Import(TestAiConfig.class)
@UnifiedSpringBootTest
class ShoppingViewIT {

    @LocalServerPort
    int port;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

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
    private PantryService pantryService;

    @Autowired
    private PantryRepository pantryRepository;

    @Autowired
    private ViewPreferenceRepository viewPreferenceRepository;

    @Autowired
    private ExtraShoppingItemRepository extraShoppingItemRepository;

    /**
     * Override: seed Household + active plan BEFORE the base class navigates to /shopping.
     * The plan is generated via the standard production path (picks 7 recipes).
     * Both salmon-pasta and tuna-pasta share "pasta" (400 g each) and "garlic" (3 cloves each),
     * which enables AC #2 (consolidation) and AC #5 (already-have on garlic) assertions.
     */
    @BeforeEach
    void seedBeforeNavigation() throws Exception {
        // Seed household
        Household h = new Household();
        h.setName("IT Shopping Household");
        h.setSize(2);
        h.setWeeklyBudget(new BigDecimal("120.00"));
        h.setAllergies(List.of());
        h.setHatedFoods(List.of());
        householdService.save(h);

        // Generate the active plan using the production path (7 meals from catalog).
        var household = householdService.findHousehold().orElseThrow();
        planService.generateActivePlan(household, recipeCatalog);
    }

    @AfterEach
    void cleanUp() {
        // FK-safe order: meal_edit → extra_shopping_item → pantry_item → view_preference
        //               → conversation_message → meal → plan → household
        mealEditRepository.deleteAll();
        extraShoppingItemRepository.deleteAll();
        pantryRepository.deleteAll();
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

    // ── AC assertions ─────────────────────────────────────────────────────────

    /**
     * AC #1 (route/title): visiting /shopping loads the correct page title.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void hasPageTitle(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate("/shopping");
        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright).hasTitle("Mise — Shopping")
        assertThat(playwright).hasTitle("Mise — Shopping");
    }

    /**
     * AC #1: the recommended-store name is present somewhere in the view.
     * On desktop the store badge in the header strip is hidden (display:none per CSS) and
     * the store name appears in the recommendation panel headline instead. The badge is
     * always attached to the DOM (for mobile use) but not visible on desktop. We assert the
     * recommendation-panel headline carries a non-empty store name on desktop.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void recommendedStoreBadgeIsVisible(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate("/shopping");
        // Store badge is in the DOM (for mobile) but hidden on desktop — check it's attached
        // TODO unified: Dramafinder Escape Hatch: playwright.locator("#mise-shopping-store-recommended")
        Locator badge = playwright.locator("#mise-shopping-store-recommended");
        // TODO unified: Dramafinder Escape Hatch: assertThat(badge).isAttached()
        assertThat(badge).isAttached();

        // The store name is always visible via the recommendation panel headline on desktop
        // TODO unified: Dramafinder Escape Hatch: playwright.getByTestId("recommendation-panel").locator(".mise-shopping-rec-store-name")
        Locator storeHeadline = playwright.getByTestId("recommendation-panel")
                .locator(".mise-shopping-rec-store-name");
        // TODO unified: Dramafinder Escape Hatch: assertThat(storeHeadline).isVisible()
        assertThat(storeHeadline).isVisible();
        // TODO unified: Dramafinder Escape Hatch: assertThat(storeHeadline).not().hasText("—")
        assertThat(storeHeadline).not().hasText("—");
        // TODO unified: Dramafinder Escape Hatch: assertThat(storeHeadline).not().isEmpty()
        assertThat(storeHeadline).not().isEmpty();
    }

    /**
     * AC #1: the total-cost span is visible and contains a € sign.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void totalCostSpanShowsEuroSign(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate("/shopping");
        // TODO unified: Dramafinder Escape Hatch: playwright.getByTestId("shopping-total-cost")
        Locator totalCost = playwright.getByTestId("shopping-total-cost");
        // TODO unified: Dramafinder Escape Hatch: assertThat(totalCost).isVisible()
        assertThat(totalCost).isVisible();
        // TODO unified: Dramafinder Escape Hatch: assertThat(totalCost).containsText("€")
        assertThat(totalCost).containsText("€");
    }

    /**
     * AC #1: the store-mode toggle control is visible with both segment buttons.
     * Scoped to the recommendation panel — the toggle appears in two places in the DOM
     * (mobile strip + desktop panel); on desktop only the panel instance is visible.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void storeModeToggleIsVisible(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate("/shopping");
        // TODO unified: Dramafinder Escape Hatch: playwright.getByTestId("recommendation-panel")
        Locator panel = playwright.getByTestId("recommendation-panel");
        // TODO unified: Dramafinder Escape Hatch: assertThat(panel.getByTestId("store-mode-toggle")).isVisible()
        assertThat(panel.getByTestId("store-mode-toggle")).isVisible();
        // TODO unified: Dramafinder Escape Hatch: assertThat(panel.getByTestId("store-mode-one")).isVisible()
        assertThat(panel.getByTestId("store-mode-one")).isVisible();
        // TODO unified: Dramafinder Escape Hatch: assertThat(panel.getByTestId("store-mode-mix")).isVisible()
        assertThat(panel.getByTestId("store-mode-mix")).isVisible();
    }

    /**
     * AC #1: the pantry section (collapsed by default) is present in the DOM.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void pantrySectionExistsInDom(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate("/shopping");
        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright.getByTestId("pantry-section")).isAttached()
        assertThat(playwright.getByTestId("pantry-section")).isAttached();
    }

    /**
     * AC #1: at least one aisle group is rendered when the plan is seeded.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void atLeastOneAisleGroupRendered(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate("/shopping");
        // TODO unified: Dramafinder Escape Hatch: playwright.locator("[id^='mise-shopping-aisle-']")
        Locator aisleGroups = playwright.locator("[id^='mise-shopping-aisle-']");
        // TODO unified: Dramafinder Escape Hatch: assertThat(aisleGroups.first()).isVisible()
        assertThat(aisleGroups.first()).isVisible();
        // Require at least 1 aisle group
        // TODO unified: Dramafinder Escape Hatch: aisleGroups.count()
        int count = aisleGroups.count();
        Assertions.assertThat(count).isGreaterThanOrEqualTo(1);
    }

    /**
     * AC #1: at least one shopping row is rendered.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void atLeastOneShoppingRowRendered(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate("/shopping");
        // TODO unified: Dramafinder Escape Hatch: playwright.getByTestId("shopping-row")
        Locator rows = playwright.getByTestId("shopping-row");
        // TODO unified: Dramafinder Escape Hatch: assertThat(rows.first()).isVisible()
        assertThat(rows.first()).isVisible();
        // TODO unified: Dramafinder Escape Hatch: rows.count()
        int count = rows.count();
        Assertions.assertThat(count).isGreaterThanOrEqualTo(1);
    }

    /**
     * AC #2 (consolidation): salmon-pasta and tuna-pasta both contain "pasta" (400 g each).
     * When both appear in the active plan (7 meals, catalog has 18 recipes, so statistical
     * chance both appear in a 7-meal plan is moderate). This test seeds the plan via
     * generateActivePlan; if the seeded plan happens to include both pasta recipes the
     * assertion is live, otherwise the test confirms a single pasta row (no duplicates).
     *
     * <p>Deterministic consolidation approach: since we cannot force which 7 recipes are
     * selected (generateActivePlan picks randomly), we assert the invariant that there is
     * at most ONE shopping row whose name is "pasta" — regardless of how many recipes
     * contributed. Consolidation must never produce duplicate rows for the same ingredient
     * and unit.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void pastaIngredientAppearsAtMostOnce(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate("/shopping");
        // Find all shopping rows whose visible text contains "pasta"
        // TODO unified: Dramafinder Escape Hatch: playwright.locator("[data-testid='shopping-row']").filter(new Locator.FilterOptions().setHasText("pasta"))
        Locator pastaRows = playwright.locator("[data-testid='shopping-row']")
                .filter(new Locator.FilterOptions().setHasText("pasta"));
        // TODO unified: Dramafinder Escape Hatch: pastaRows.count()
        int count = pastaRows.count();
        // Consolidation rule: cannot have more than 1 row for the same ingredient+unit combo
        Assertions.assertThat(count)
                .as("AC #2: pasta must appear as at most one consolidated row, never duplicated")
                .isLessThanOrEqualTo(1);
    }

    /**
     * AC #3 (staple subtraction): a PantryItem saved with staple=true and name "olive oil"
     * must NOT appear as a shopping row after page reload.
     * olive oil appears in tuna-pasta and lentil-soup; as a staple it must be suppressed.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void stapleOliveOilIsSuppressedFromShoppingList(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate("/shopping");
        // Save a staple pantry item for olive oil BEFORE re-navigating
        var household = householdService.findHousehold().orElseThrow();
        var oliveOil = new PantryItem();
        oliveOil.setHouseholdId(household.getId());
        oliveOil.setIngredientName("olive oil");
        oliveOil.setStaple(true);
        pantryService.save(oliveOil);

        // Reload the view so ShoppingService re-derives with the new pantry staple
        // TODO unified: Dramafinder Escape Hatch: // Reload the view so ShoppingService re-derives with the new pantry stapleplaywright.navigate(baseUrl() + "/shopping")
        playwright.navigate(baseUrl() + "/shopping");

        // Assert there is no shopping row whose visible text contains "olive oil"
        // TODO unified: Dramafinder Escape Hatch: playwright.locator("[data-testid='shopping-row']").filter(new Locator.FilterOptions().setHasText("olive oil"))
        Locator oliveOilRows = playwright.locator("[data-testid='shopping-row']")
                .filter(new Locator.FilterOptions().setHasText("olive oil"));
        // TODO unified: Dramafinder Escape Hatch: oliveOilRows.count()
        Assertions.assertThat(oliveOilRows.count())
                .as("AC #3: olive oil (staple) must be suppressed from the shopping list")
                .isZero();
    }

    /**
     * AC #4 (store-mode toggle + persistence): clicking "Cheapest mix" persists a
     * ViewPreference row with mode=CHEAPEST_MIX. After a page reload the Cheapest-mix
     * button retains the "active" CSS class.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void storeModeTogglePersistsAcrossReload(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate("/shopping");
        // Scope to the recommendation panel — both strip and panel have the same data-testids;
        // on desktop the panel is the visible/interactive one.
        // TODO unified: Dramafinder Escape Hatch: playwright.getByTestId("recommendation-panel")
        Locator panel = playwright.getByTestId("recommendation-panel");

        // Click the "Cheapest mix" button
        // TODO unified: Dramafinder Escape Hatch: // Click the "Cheapest mix" buttonpanel.getByTestId("store-mode-mix").click()
        panel.getByTestId("store-mode-mix").click();

        // Wait for the view to rebuild (the button should now have the "active" class)
        // TODO unified: Dramafinder Escape Hatch: // Wait for the view to rebuild (the button should now have the "active" class)assertThat(panel.getByTestId("store-mode-mix")).hasClass(new java.util.regex.Pattern[]{java.util.regex.Pattern.compile("active")})
        assertThat(panel.getByTestId("store-mode-mix")).hasClass(
                new java.util.regex.Pattern[]{java.util.regex.Pattern.compile("active")});

        // Assert DB: ViewPreference row exists with mode=CHEAPEST_MIX
        var household = householdService.findHousehold().orElseThrow();
        var prefOpt = viewPreferenceRepository.findByHouseholdIdAndViewAndWidgetKey(
                household.getId(), ViewPreference.View.SHOPPING, "storeMode");
        Assertions.assertThat(prefOpt)
                .as("AC #4: ViewPreference row for storeMode must exist after toggle")
                .isPresent();
        Assertions.assertThat(prefOpt.get().getSettings())
                .as("AC #4: stored settings must indicate CHEAPEST_MIX mode")
                .contains("CHEAPEST_MIX");

        // Reload the page — the preference must be restored from DB
        // TODO unified: Dramafinder Escape Hatch: // Reload the page — the preference must be restored from DBplaywright.reload()
        playwright.reload();

        // Re-scope after reload
        // TODO unified: Dramafinder Escape Hatch: playwright.getByTestId("recommendation-panel")
        Locator panelAfterReload = playwright.getByTestId("recommendation-panel");

        // After reload, the Cheapest-mix button must still carry the "active" class
        // TODO unified: Dramafinder Escape Hatch: // After reload, the Cheapest-mix button must still carry the "active" classassertThat(panelAfterReload.getByTestId("store-mode-mix")).hasClass(new java.util.regex.Pattern[]{java.util.regex.Pattern.compile("active")})
        assertThat(panelAfterReload.getByTestId("store-mode-mix")).hasClass(
                new java.util.regex.Pattern[]{java.util.regex.Pattern.compile("active")});

        // And the One-store button must NOT carry the "active" class
        // TODO unified: Dramafinder Escape Hatch: // And the One-store button must NOT carry the "active" classassertThat(panelAfterReload.getByTestId("store-mode-one")).not().hasClass(new java.util.regex.Pattern[]{java.util.regex.Pattern.compile("active")})
        assertThat(panelAfterReload.getByTestId("store-mode-one")).not().hasClass(
                new java.util.regex.Pattern[]{java.util.regex.Pattern.compile("active")});
    }

    /**
     * AC #5 (already-have affordance): clicking the "already have" button on the first
     * visible shopping row (a) removes that row from the active list (count drops by 1),
     * and (b) creates a non-staple PantryItem for that ingredient.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void alreadyHaveButtonRemovesRowAndCreatesPantryItem(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate("/shopping");
        // TODO unified: Dramafinder Escape Hatch: playwright.getByTestId("shopping-row")
        Locator rows = playwright.getByTestId("shopping-row");
        // Wait for the list to settle before snapshotting the count. Without this
        // the test flakes: rows.count() returns mid-render (e.g. 33 of 34 visible),
        // we click delete, and the final 34th row arrives and is then removed,
        // landing on 32 instead of before-1 = 33. Two consecutive snapshots with
        // a brief delay between them is enough to gate the click on a stable list.
        // TODO unified: Dramafinder Escape Hatch: // Wait for the list to settle before snapshotting the count. Without this// the test flakes: rows.count() returns mid-render (e.g. 33 of 34 visible),// we click delete, and the final 34th row arrives and is then removed,// landing on 32 instead of before-1 = 33. Two consecutive snapshots with// a brief delay between them is enough to gate the click on a stable list.assertThat(rows.first()).isVisible()
        assertThat(rows.first()).isVisible();
        int before;
        // TODO unified: Dramafinder Escape Hatch: rows.count()
        // TODO unified: Dramafinder Escape Hatch: playwright.waitForTimeout(150)
        while (true) {
            int sample = rows.count();
            playwright.waitForTimeout(150);
            if (sample == rows.count()) { before = sample; break; }
        }
        Assertions.assertThat(before)
                .as("Precondition: shopping list must have at least one row to test AC #5")
                .isGreaterThanOrEqualTo(1);

        // Get the name of the ingredient in the first row by reading its visible text
        // The item-name span carries class mise-shopping-item-name
        // TODO unified: Dramafinder Escape Hatch: rows.first()
        Locator firstRow = rows.first();
        // TODO unified: Dramafinder Escape Hatch: firstRow.locator(".mise-shopping-item-name").innerText()
        String ingredientName = firstRow.locator(".mise-shopping-item-name").innerText().trim();

        // Click the "already have" button scoped to the first row
        // TODO unified: Dramafinder Escape Hatch: // Click the "already have" button scoped to the first rowfirstRow.getByTestId("row-already-have").click()
        firstRow.getByTestId("row-already-have").click();

        // AC #5a: the row count must have dropped by exactly one
        // TODO unified: Dramafinder Escape Hatch: // AC #5a: the row count must have dropped by exactly oneassertThat(playwright.getByTestId("shopping-row")).hasCount(before - 1)
        assertThat(playwright.getByTestId("shopping-row")).hasCount(before - 1);

        // AC #5b: a PantryItem with that ingredient name (non-staple) must exist in DB
        var household = householdService.findHousehold().orElseThrow();
        List<PantryItem> pantryItems = pantryRepository.findByHouseholdId(household.getId());
        final String name = ingredientName;
        Assertions.assertThat(pantryItems)
                .as("AC #5: PantryItem for '%s' must exist after clicking 'already have'", name)
                .anyMatch(pi -> pi.getIngredientName().equalsIgnoreCase(name) && !pi.isStaple());
    }

    /**
     * AC #6 (live reflow — broadcaster-driven, deterministic path): after a plan meal
     * is swapped via planService directly, navigating back to /shopping shows the
     * updated ingredient set. This exercises the re-derive path without requiring the
     * async broadcaster (which depends on UI.access from a non-UI thread).
     *
     * <p>Strategy: capture the initial set of row ids, swap one meal to lentil-soup
     * (which has "red lentils" — a distinctive ingredient not present in other catalog
     * recipes), then reload /shopping and assert "red lentils" appears.
     * If the active plan already contains lentil-soup, we swap to it from something else
     * which is still a valid re-derive; in that edge case the assertion may not be
     * meaningful, so we pick the first non-lentil-soup meal and swap to lentil-soup.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void planSwapReflowsShoppingList(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate("/shopping");
        var household = householdService.findHousehold().orElseThrow();
        var plan = planService.findActivePlan(household.getId()).orElseThrow();

        // Find a meal that is NOT already lentil-soup (swap target has "red lentils")
        var meals = planService.findMeals(plan.getId());
        var targetMeal = meals.stream()
                .filter(m -> !"lentil-soup".equals(m.getRecipeRef()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("All 7 meals are already lentil-soup — cannot test reflow"));

        // Swap the meal to lentil-soup via the service (deterministic — no AI involved)
        planService.swapMeal(targetMeal.getId(), "lentil-soup", "IT reflow test");

        // Navigate back to /shopping — beforeEnter re-derives the list from the updated plan
        // TODO unified: Dramafinder Escape Hatch: // Navigate back to /shopping — beforeEnter re-derives the list from the updated planplaywright.navigate(baseUrl() + "/shopping")
        playwright.navigate(baseUrl() + "/shopping");

        // "red lentils" is a unique ingredient from lentil-soup; it must now appear in the list
        // TODO unified: Dramafinder Escape Hatch: playwright.locator("[data-testid='shopping-row']").filter(new Locator.FilterOptions().setHasText("red lentils"))
        Locator redLentilRows = playwright.locator("[data-testid='shopping-row']")
                .filter(new Locator.FilterOptions().setHasText("red lentils"));
        // Playwright LocatorAssertions does not have .as(); the failure message comes from isVisible() timeout
        // TODO unified: Dramafinder Escape Hatch: // Playwright LocatorAssertions does not have .as(); the failure message comes from isVisible() timeoutassertThat(redLentilRows.first()).isVisible()
        assertThat(redLentilRows.first()).isVisible();
    }
}
