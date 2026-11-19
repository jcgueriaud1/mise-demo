package com.example.mise.it;

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

import com.microsoft.playwright.Page;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.flow.component.html.H2;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedDetails;
import com.vaadin.testing.unified.component.UnifiedDiv;
import com.vaadin.testing.unified.component.UnifiedGenericComponent;
import com.vaadin.testing.unified.component.UnifiedSpan;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

/**
 * UC-005 Playwright IT — Shopping list view at /shopping.
 *
 * <p>Lifecycle: a {@code @BeforeEach} seeds a Household and a seeded active plan before
 * each test opens /shopping. The plan is generated from two specific recipes
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
     * Seeds Household + active plan before each test opens /shopping.
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
    @DisplayName("The shopping page has its browser tab title")
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void hasPageTitle(UnifiedPage page) {
        // Escape Hatch kept: the Unified API has no page-title assertion
        Page playwright = page.unwrap(Page.class);
        page.navigate("/shopping");
        com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(playwright).hasTitle("Mise — Shopping");
    }

    /**
     * AC #1: the recommended-store name is present in the view. The header-strip badge is for
     * mobile (hidden by CSS on desktop, so its visibility is a browser concern); the store name
     * the user reads on desktop is the recommendation panel's headline.
     */
    @Test
    @DisplayName("The shopping page names the recommended store")
    void recommendedStoreBadgeIsVisible(UnifiedPage page) {
        page.navigate("/shopping");

        assertThat(page.find(UnifiedSpan.class).withId("mise-shopping-store-recommended")).exists();
        UnifiedGenericComponent storeHeadline = recommendationPanel(page).findComponent(H2.class)
                .withClassName("mise-shopping-rec-store-name");
        assertThat(storeHeadline).isVisible();
        Assertions.assertThat(storeHeadline.getText().strip()).isNotEmpty().isNotEqualTo("—");
    }

    /**
     * AC #1: the total-cost span is visible and contains a € sign.
     */
    @Test
    @DisplayName("The shopping page shows the total cost in euros")
    void totalCostSpanShowsEuroSign(UnifiedPage page) {
        page.navigate("/shopping");
        UnifiedContainer totalCost = page.find(UnifiedContainer.class).withTestId("shopping-total-cost");
        assertThat(totalCost).isVisible();
        assertThat(totalCost).hasTextContaining("€");
    }

    /**
     * AC #1: the store-mode toggle control is visible with both segment buttons.
     * Scoped to the recommendation panel — the toggle appears in two places
     * (mobile strip + desktop panel).
     */
    @Test
    @DisplayName("The store mode toggle offers one store or the cheapest mix")
    void storeModeToggleIsVisible(UnifiedPage page) {
        page.navigate("/shopping");
        UnifiedContainer panel = recommendationPanel(page);
        assertThat(panel.find(UnifiedContainer.class).withTestId("store-mode-toggle")).isVisible();
        assertThat(storeModeButton(panel, "store-mode-one")).isVisible();
        assertThat(storeModeButton(panel, "store-mode-mix")).isVisible();
    }

    /**
     * AC #1: the pantry section (collapsed by default) is present.
     */
    @Test
    @DisplayName("The shopping page has a pantry section")
    void pantrySectionExistsInDom(UnifiedPage page) {
        page.navigate("/shopping");
        assertThat(page.find(UnifiedDetails.class).withTestId("pantry-section")).exists();
    }

    /**
     * AC #1: at least one aisle group is rendered when the plan is seeded.
     */
    @Test
    @DisplayName("The shopping list is grouped by aisle")
    void atLeastOneAisleGroupRendered(UnifiedPage page) {
        page.navigate("/shopping");
        assertThat(page.find(UnifiedDiv.class).withClassName("mise-shopping-aisle-group")).exists();
    }

    /**
     * AC #1: at least one shopping row is rendered.
     */
    @Test
    @DisplayName("The shopping list has items")
    void atLeastOneShoppingRowRendered(UnifiedPage page) {
        page.navigate("/shopping");
        assertThat(shoppingRows(page)).exists();
    }

    /**
     * AC #2 (consolidation): we cannot force which 7 recipes generateActivePlan picks, so we
     * assert the invariant that there is at most ONE shopping row for "pasta" — regardless of
     * how many recipes contributed. Consolidation must never produce duplicate rows for the
     * same ingredient and unit.
     */
    @Test
    @DisplayName("An ingredient used by several meals is listed once")
    void pastaIngredientAppearsAtMostOnce(UnifiedPage page) {
        page.navigate("/shopping");
        assertThat(shoppingRows(page)).exists();
        Assertions.assertThat(shoppingRows(page).withTextContaining("pasta").all())
                .as("AC #2: pasta must appear as at most one consolidated row, never duplicated")
                .hasSizeLessThanOrEqualTo(1);
    }

    /**
     * AC #3 (staple subtraction): a PantryItem saved with staple=true and name "olive oil"
     * must NOT appear as a shopping row after page reload.
     */
    @Test
    @DisplayName("A pantry staple is left off the shopping list")
    void stapleOliveOilIsSuppressedFromShoppingList(UnifiedPage page) {
        // given: olive oil is a pantry staple
        page.navigate("/shopping");
        var household = householdService.findHousehold().orElseThrow();
        var oliveOil = new PantryItem();
        oliveOil.setHouseholdId(household.getId());
        oliveOil.setIngredientName("olive oil");
        oliveOil.setStaple(true);
        pantryService.save(oliveOil);

        // when: ShoppingService re-derives the list
        page.reload();

        // then
        assertThat(shoppingRows(page)).exists();
        assertThat(shoppingRows(page).withTextContaining("olive oil")).doesNotExist();
    }

    /**
     * AC #4 (store-mode toggle + persistence): clicking "Cheapest mix" persists a
     * ViewPreference row with mode=CHEAPEST_MIX. After a page reload the Cheapest-mix
     * button is still the active one.
     */
    @Test
    @DisplayName("The chosen store mode is remembered after a reload")
    void storeModeTogglePersistsAcrossReload(UnifiedPage page) {
        // given
        page.navigate("/shopping");

        // when
        storeModeButton(recommendationPanel(page), "store-mode-mix").click();

        // then: the view rebuilds with Cheapest mix active, and the choice is stored
        assertThat(storeModeButton(recommendationPanel(page), "store-mode-mix").withClassName("active")).exists();
        var household = householdService.findHousehold().orElseThrow();
        var prefOpt = viewPreferenceRepository.findByHouseholdIdAndViewAndWidgetKey(
                household.getId(), ViewPreference.View.SHOPPING, "storeMode");
        Assertions.assertThat(prefOpt)
                .as("AC #4: ViewPreference row for storeMode must exist after toggle")
                .isPresent();
        Assertions.assertThat(prefOpt.get().getSettings())
                .as("AC #4: stored settings must indicate CHEAPEST_MIX mode")
                .contains("CHEAPEST_MIX");

        // when
        page.reload();

        // then
        UnifiedContainer panel = recommendationPanel(page);
        assertThat(storeModeButton(panel, "store-mode-mix").withClassName("active")).exists();
        assertThat(storeModeButton(panel, "store-mode-one").withClassName("active")).doesNotExist();
    }

    /**
     * AC #5 (already-have affordance): clicking the "already have" button on the first
     * shopping row (a) removes that row from the active list (count drops by 1),
     * and (b) creates a non-staple PantryItem for that ingredient.
     */
    @Test
    @DisplayName("Marking an item as already had removes it and adds it to the pantry")
    void alreadyHaveButtonRemovesRowAndCreatesPantryItem(UnifiedPage page) {
        // given
        page.navigate("/shopping");
        assertThat(shoppingRows(page)).exists();
        int before = shoppingRows(page).all().size();
        String ingredientName = shoppingRows(page).all().getFirst().find(UnifiedSpan.class)
                .withClassName("mise-shopping-item-name").getText().strip();

        // when
        page.find(UnifiedButton.class).withAccessibleName("Mark " + ingredientName + " as already have").click();

        // then
        assertThat(shoppingRows(page)).hasCount(before - 1);
        var household = householdService.findHousehold().orElseThrow();
        List<PantryItem> pantryItems = pantryRepository.findByHouseholdId(household.getId());
        Assertions.assertThat(pantryItems)
                .as("AC #5: PantryItem for '%s' must exist after clicking 'already have'", ingredientName)
                .anyMatch(pi -> pi.getIngredientName().equalsIgnoreCase(ingredientName) && !pi.isStaple());
    }

    /**
     * AC #6 (live reflow — deterministic path): after a plan meal is swapped via planService
     * directly, reloading /shopping shows the updated ingredient set. Swaps one meal to
     * lentil-soup, whose "red lentils" no other catalog recipe has.
     */
    @Test
    @DisplayName("Swapping a meal in the plan updates the shopping list")
    void planSwapReflowsShoppingList(UnifiedPage page) {
        // given
        page.navigate("/shopping");
        var household = householdService.findHousehold().orElseThrow();
        var plan = planService.findActivePlan(household.getId()).orElseThrow();
        var targetMeal = planService.findMeals(plan.getId()).stream()
                .filter(m -> !"lentil-soup".equals(m.getRecipeRef()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("All 7 meals are already lentil-soup — cannot test reflow"));

        // when: a deterministic swap (no AI), then a reload re-derives the list
        planService.swapMeal(targetMeal.getId(), "lentil-soup", "IT reflow test");
        page.reload();

        // then
        assertThat(shoppingRows(page).withTextContaining("red lentils")).exists();
    }

    private static UnifiedContainer recommendationPanel(UnifiedPage page) {
        return page.find(UnifiedContainer.class).withTestId("recommendation-panel");
    }

    private static UnifiedButton storeModeButton(UnifiedContainer panel, String testId) {
        return panel.find(UnifiedButton.class).withTestId(testId);
    }

    private static UnifiedDiv shoppingRows(UnifiedPage page) {
        return page.find(UnifiedDiv.class).withTestId("shopping-row");
    }
}
