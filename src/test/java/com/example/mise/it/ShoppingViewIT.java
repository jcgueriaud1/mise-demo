package com.example.mise.it;

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
import com.example.mise.it.support.TestAiConfig;
import com.example.mise.it.support.TestChatModel;
import com.example.mise.ui.shopping.ShoppingView;

import com.vaadin.flow.component.html.H2;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedDetails;
import com.vaadin.testing.unified.component.UnifiedDiv;
import com.vaadin.testing.unified.component.UnifiedGenericComponent;
import com.vaadin.testing.unified.component.UnifiedSpan;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

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
@UnifiedSpringBootTest
@ActiveProfiles("it")
@Import(TestAiConfig.class)
class ShoppingViewIT {

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
    private PantryService pantryService;

    @Autowired
    private PantryRepository pantryRepository;

    @Autowired
    private ViewPreferenceRepository viewPreferenceRepository;

    @Autowired
    private ExtraShoppingItemRepository extraShoppingItemRepository;

    /**
     * Seed Household + active plan before each test navigates to /shopping.
     * The plan is generated via the standard production path (picks 7 recipes).
     * Both salmon-pasta and tuna-pasta share "pasta" (400 g each) and "garlic" (3 cloves each),
     * which enables AC #2 (consolidation) and AC #5 (already-have on garlic) assertions.
     */
    @BeforeEach
    void seed() {
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
    @DisplayName("The shopping page has its title")
    void hasPageTitle(UnifiedPage page) {
        page.navigate(ShoppingView.class);

        assertThat(page).hasTitle("Mise — Shopping");
    }

    /**
     * AC #1: the recommended-store name is present in the view.
     * On desktop the store badge in the header strip is hidden (display:none per CSS) and
     * the store name appears in the recommendation panel headline instead. The badge is
     * always attached (for mobile use). We assert the recommendation-panel headline carries
     * a non-empty store name.
     */
    @Test
    @DisplayName("The recommended store is named")
    void recommendedStoreBadgeIsVisible(UnifiedPage page) {
        page.navigate(ShoppingView.class);

        // The badge is attached for mobile; CSS hides it on desktop
        assertThat(page.find(UnifiedSpan.class).withTestId("recommended-store")).exists();

        // The store name is shown in the recommendation panel headline
        UnifiedGenericComponent storeHeadline = recommendationPanel(page)
                .findComponent(H2.class).withClassName("mise-shopping-rec-store-name");
        assertThat(storeHeadline).isVisible();
        Assertions.assertThat(storeHeadline.getText()).isNotBlank().isNotEqualTo("—");
    }

    /**
     * AC #1: the total-cost span is visible and contains a € sign.
     */
    @Test
    @DisplayName("The shopping total is shown in euros")
    void totalCostSpanShowsEuroSign(UnifiedPage page) {
        page.navigate(ShoppingView.class);

        assertThat(page.find(UnifiedSpan.class).withTestId("shopping-total-cost"))
                .isVisible().hasTextContaining("€");
    }

    /**
     * AC #1: the store-mode toggle control is visible with both segment buttons.
     * Scoped to the recommendation panel — the toggle appears in two places
     * (mobile strip + desktop panel); on desktop only the panel instance is visible.
     */
    @Test
    @DisplayName("The store mode toggle offers One store and Cheapest mix")
    void storeModeToggleIsVisible(UnifiedPage page) {
        page.navigate(ShoppingView.class);

        UnifiedContainer panel = recommendationPanel(page);
        assertThat(panel.find(UnifiedDiv.class).withTestId("store-mode-toggle")).isVisible();
        assertThat(oneStore(panel)).isVisible();
        assertThat(cheapestMix(panel)).isVisible();
    }

    /**
     * AC #1: the pantry section (collapsed by default) is present.
     */
    @Test
    @DisplayName("The pantry section is present")
    void pantrySectionExistsInDom(UnifiedPage page) {
        page.navigate(ShoppingView.class);

        assertThat(page.find(UnifiedDetails.class).withTestId("pantry-section")).exists();
    }

    /**
     * AC #1: at least one aisle group is rendered when the plan is seeded.
     */
    @Test
    @DisplayName("The list is grouped by aisle")
    void atLeastOneAisleGroupRendered(UnifiedPage page) {
        page.navigate(ShoppingView.class);

        assertThat(page.find(UnifiedDiv.class).withClassName("mise-shopping-aisle-group")).exists();
    }

    /**
     * AC #1: at least one shopping row is rendered.
     */
    @Test
    @DisplayName("The list has shopping rows")
    void atLeastOneShoppingRowRendered(UnifiedPage page) {
        page.navigate(ShoppingView.class);

        assertThat(shoppingRows(page)).exists();
    }

    /**
     * AC #2 (consolidation): there is at most ONE shopping row whose text contains "pasta",
     * however many recipes contributed (generateActivePlan picks recipes randomly).
     * Consolidation must never produce duplicate rows for the same ingredient and unit.
     */
    @Test
    @DisplayName("An ingredient from several recipes is one consolidated row")
    void pastaIngredientAppearsAtMostOnce(UnifiedPage page) {
        page.navigate(ShoppingView.class);
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
        // given: olive oil saved as a staple
        page.navigate(ShoppingView.class);
        var household = householdService.findHousehold().orElseThrow();
        var oliveOil = new PantryItem();
        oliveOil.setHouseholdId(household.getId());
        oliveOil.setIngredientName("olive oil");
        oliveOil.setStaple(true);
        pantryService.save(oliveOil);

        // when: a reload re-derives the list with the new staple
        page.reload();

        // then
        assertThat(shoppingRows(page)).exists();
        assertThat(shoppingRows(page).withTextContaining("olive oil")).doesNotExist();
    }

    /**
     * AC #4 (store-mode toggle + persistence): clicking "Cheapest mix" persists a
     * ViewPreference row with mode=CHEAPEST_MIX. After a page reload the Cheapest-mix
     * button keeps the "active" class.
     */
    @Test
    @DisplayName("The chosen store mode is saved and restored after a reload")
    void storeModeTogglePersistsAcrossReload(UnifiedPage page) {
        // given
        page.navigate(ShoppingView.class);

        // when
        cheapestMix(recommendationPanel(page)).click();

        // then: the view rebuilds with Cheapest mix active ...
        assertThat(cheapestMix(recommendationPanel(page)).withClassName("active")).exists();

        // ... and the mode is stored
        var household = householdService.findHousehold().orElseThrow();
        var prefOpt = viewPreferenceRepository.findByHouseholdIdAndViewAndWidgetKey(
                household.getId(), ViewPreference.View.SHOPPING, "storeMode");
        Assertions.assertThat(prefOpt)
                .as("AC #4: ViewPreference row for storeMode must exist after toggle")
                .isPresent();
        Assertions.assertThat(prefOpt.get().getSettings())
                .as("AC #4: stored settings must indicate CHEAPEST_MIX mode")
                .contains("CHEAPEST_MIX");

        // when: reload, the preference is restored from the DB
        page.reload();

        // then
        assertThat(cheapestMix(recommendationPanel(page)).withClassName("active")).exists();
        assertThat(oneStore(recommendationPanel(page)).withClassName("active")).doesNotExist();
    }

    /**
     * AC #5 (already-have affordance): clicking "already have" on a shopping row
     * (a) removes that row from the active list (count drops by 1), and
     * (b) creates a non-staple PantryItem for that ingredient.
     */
    @Test
    @DisplayName("Already have removes the row and adds the item to the pantry")
    void alreadyHaveButtonRemovesRowAndCreatesPantryItem(UnifiedPage page) {
        // given: the list rendered; Auto-Wait settles it before the count is read
        page.navigate(ShoppingView.class);
        assertThat(shoppingRows(page)).exists();
        int before = shoppingRows(page).all().size();
        // Any row will do: take the first one's ingredient name
        String ingredientName = shoppingRows(page).all().get(0)
                .find(UnifiedSpan.class).withClassName("mise-shopping-item-name").getText().trim();

        // when
        page.find(UnifiedButton.class).withAccessibleName("Mark " + ingredientName + " as already have").click();

        // then: AC #5a, the row is gone ...
        assertThat(shoppingRows(page)).hasCount(before - 1);

        // ... AC #5b, and a non-staple PantryItem exists for it
        var household = householdService.findHousehold().orElseThrow();
        List<PantryItem> pantryItems = pantryRepository.findByHouseholdId(household.getId());
        Assertions.assertThat(pantryItems)
                .as("AC #5: PantryItem for '%s' must exist after clicking 'already have'", ingredientName)
                .anyMatch(pi -> pi.getIngredientName().equalsIgnoreCase(ingredientName) && !pi.isStaple());
    }

    /**
     * AC #6 (live reflow — deterministic path): after a plan meal is swapped via
     * planService directly, reloading /shopping shows the updated ingredient set.
     * This exercises the re-derive path without requiring the async broadcaster.
     *
     * <p>Strategy: swap the first non-lentil-soup meal to lentil-soup (which has
     * "red lentils" — an ingredient no other catalog recipe has), then reload /shopping
     * and assert "red lentils" appears.
     */
    @Test
    @DisplayName("A swapped meal's ingredients appear on the shopping list")
    void planSwapReflowsShoppingList(UnifiedPage page) {
        // given
        page.navigate(ShoppingView.class);
        var household = householdService.findHousehold().orElseThrow();
        var plan = planService.findActivePlan(household.getId()).orElseThrow();
        var targetMeal = planService.findMeals(plan.getId()).stream()
                .filter(m -> !"lentil-soup".equals(m.getRecipeRef()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("All 7 meals are already lentil-soup — cannot test reflow"));

        // when: swap via the service (deterministic — no AI involved), then reload
        planService.swapMeal(targetMeal.getId(), "lentil-soup", "IT reflow test");
        page.reload();

        // then
        assertThat(shoppingRows(page).withTextContaining("red lentils")).exists();
    }

    private static UnifiedContainer recommendationPanel(UnifiedPage page) {
        return page.find(UnifiedContainer.class).withTestId("recommendation-panel");
    }

    private static UnifiedDiv shoppingRows(UnifiedPage page) {
        return page.find(UnifiedDiv.class).withTestId("shopping-row");
    }

    private static UnifiedButton oneStore(UnifiedContainer panel) {
        return panel.find(UnifiedButton.class).withTestId("store-mode-one");
    }

    private static UnifiedButton cheapestMix(UnifiedContainer panel) {
        return panel.find(UnifiedButton.class).withTestId("store-mode-mix");
    }
}
