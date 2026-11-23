package com.example.mise.it;

import com.example.mise.capabilities.recipes.RecipeCatalog;
import com.example.mise.domain.conversation.ConversationMessage;
import com.example.mise.domain.conversation.ConversationMessageRepository;
import com.example.mise.domain.household.Household;
import com.example.mise.domain.household.HouseholdRepository;
import com.example.mise.domain.household.HouseholdService;
import com.example.mise.domain.plan.Meal;
import com.example.mise.domain.plan.MealEditRepository;
import com.example.mise.domain.plan.MealRepository;
import com.example.mise.domain.plan.PinnedMealException;
import com.example.mise.domain.plan.PlanRepository;
import com.example.mise.domain.plan.PlanService;
import com.example.mise.it.support.TestAiConfig;
import com.example.mise.it.support.TestChatModel;
import com.example.mise.ui.plan.PlanView;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedDiv;
import com.vaadin.testing.unified.component.UnifiedMessageInput;
import com.vaadin.testing.unified.component.UnifiedMessageList;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedParagraph;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

/**
 * UC-002 + UC-003 Playwright IT — Plan view with seeded household + active plan.
 *
 * <p>Lifecycle: {@code setupTest()} is overridden (same technique as
 * {@link OnboardingRedirectIT}) to seed a Household and an ACTIVE Plan before
 * the browser navigates to /plan, so the view renders a full 7-row meal grid.
 *
 * <p>Chat-round-trip tests stub the {@link com.example.mise.it.support.TestChatModel}
 * via {@code chatModel.queueReply(...)} before submitting user input. No live LLM
 * endpoint is contacted.
 *
 * <p>Cleanup in {@code @AfterEach} deletes rows in FK-safe order:
 * meal_edit → conversation_message → meal → plan → household.
 */
@UnifiedSpringBootTest
@ActiveProfiles("it")
@Import(TestAiConfig.class)
class PlanViewIT {

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

    /** Seed a household and its active plan before each test navigates to /plan. */
    @BeforeEach
    void seed() {
        // Seed household
        Household h = new Household();
        h.setName("IT Plan Household");
        h.setSize(2);
        h.setWeeklyBudget(new BigDecimal("100.00"));
        h.setAllergies(List.of());
        h.setHatedFoods(List.of());
        householdService.save(h);

        // Seed the active plan via the production code path — picks 7 recipes from
        // the YAML catalog and saves Plan + 7 Meal rows with status=PLANNED.
        var household = householdService.findHousehold().orElseThrow();
        planService.generateActivePlan(household, recipeCatalog);
    }

    @AfterEach
    void cleanUp() {
        // Delete in FK-safe order: meal_edit → conversation → meal → plan → household
        mealEditRepository.deleteAll();
        conversationMessageRepository.deleteAll();
        // Get all plans for this household (need to delete meals per plan)
        householdRepository.findAll().forEach(hh ->
                planRepository.findByHouseholdIdOrderByWeekStartDateDesc(hh.getId())
                        .forEach(plan -> {
                            mealRepository.deleteAll(mealRepository.findByPlanId(plan.getId()));
                            planRepository.delete(plan);
                        })
        );
        householdRepository.deleteAll();
    }

    /**
     * UC-002 AC: loading /plan renders the page with the correct title.
     */
    @Test
    @DisplayName("The plan page has its title")
    void hasPageTitle(UnifiedPage page) {
        page.navigate(PlanView.class);

        assertThat(page).hasTitle("Mise — Plan");
    }

    /**
     * UC-002 AC: the weekly KPI strip with all four cards is visible.
     * BR-03: stats are computed from the currently shown meals.
     */
    @Test
    @DisplayName("The weekly KPI strip shows its four cards")
    void kpiStripRenders(UnifiedPage page) {
        page.navigate(PlanView.class);

        // KpiCard is a project component without a Wrapper; its test id is its only handle
        assertThat(kpiStrip(page)).isVisible();
        assertThat(page.find(UnifiedContainer.class).withTestId("kpi-card-weekly-cost")).isVisible();
        assertThat(page.find(UnifiedContainer.class).withTestId("kpi-card-avg-meal")).isVisible();
        assertThat(page.find(UnifiedContainer.class).withTestId("kpi-card-total-prep")).isVisible();
        assertThat(page.find(UnifiedContainer.class).withTestId("kpi-card-avg-kcal")).isVisible();
    }

    /**
     * UC-002 AC: the meal grid has exactly 7 rows (Mon–Sun).
     * BR-02: grid always has 7 rows; missing slots show empty placeholders.
     */
    @Test
    @DisplayName("The meal grid has seven rows")
    void mealGridHasSevenRows(UnifiedPage page) {
        page.navigate(PlanView.class);

        assertThat(page.find(UnifiedDiv.class).withTestId("meal-row")).hasCount(7);
    }

    /**
     * UC-002 AC: the cost-by-category panel is visible and shows its title.
     */
    @Test
    @DisplayName("The cost by category panel is shown with its title")
    void costByCategoryPanelRenders(UnifiedPage page) {
        page.navigate(PlanView.class);

        UnifiedContainer panel = page.find(UnifiedContainer.class).withTestId("cost-by-category-panel");
        assertThat(panel).isVisible();
        assertThat(panel.find(UnifiedParagraph.class).withText("COST BY CATEGORY")).isVisible();
    }

    /**
     * UC-002 AC: chat round-trip via the shared dock works correctly on the Plan view.
     * The stubbed assistant reply appears in the MessageList and updates the preview.
     */
    @Test
    @DisplayName("A chat round trip on the plan updates the conversation and the reply preview")
    void chatRoundTripWorksOnSharedDock(UnifiedPage page) {
        // given
        page.navigate(PlanView.class);
        chatModel.queueReply("Friday is creamy salmon pasta.");

        // when
        sendInChatDock(page, "What's on Friday?");

        // then
        assertThat(chatDock(page).find(UnifiedMessageList.class)).hasMessageCount(2);
        assertThat(lastAiMessage(page)).hasTextContaining("Friday is creamy salmon pasta.");
    }

    /**
     * UC-002 / BR-06: after a chat round-trip on /plan, the persisted messages carry
     * ViewContext.PLAN so the conversation is correctly attributed to the Plan view.
     */
    @Test
    @DisplayName("Chat messages sent on the plan are stored with the plan view context")
    void conversationPersistedWithPlanViewContext(UnifiedPage page) {
        // given
        page.navigate(PlanView.class);
        chatModel.queueReply("Friday is creamy salmon pasta.");

        // when
        sendInChatDock(page, "What's on Friday?");

        // then: shown ...
        assertThat(chatDock(page).find(UnifiedMessageList.class)).hasMessageCount(2);
        // ... and both turns stored as PLAN. The response listener stores the turn after
        // the reply renders, so wait for the rows rather than reading them once.
        page.step("The conversation is stored as plan", () ->
                page.waitUntil(() -> conversationMessageRepository.findAll().stream()
                        .filter(r -> r.getViewContext() == ConversationMessage.ViewContext.PLAN)
                        .count() >= 2));
    }

    // ── UC-003 tests ──────────────────────────────────────────────────────────

    /**
     * UC-003 UI requirement: a pin icon button is visible on every meal row (Mon–Sun).
     */
    @Test
    @DisplayName("Every meal row has a pin button")
    void pinButtonVisibleOnEveryRow(UnifiedPage page) {
        page.navigate(PlanView.class);

        assertThat(page.find(UnifiedButton.class).withTestId("meal-action-pin")).hasCount(7);
    }

    /**
     * UC-003 BR-04: after {@link PlanService#swapMeal} sets lastEditedBy=AI and
     * lastEditedAt=now, the "edited" pill is rendered on the affected row when the
     * view is reloaded.
     */
    @Test
    @DisplayName("A meal the AI just swapped shows the edited pill")
    void editedPillRendersAfterAiSwap(UnifiedPage page) {
        // given
        page.navigate(PlanView.class);
        Meal thursdayMeal = findMealForDay(DayOfWeek.THURSDAY);

        // when: the AI swap (sets lastEditedBy=AI, lastEditedAt=now, status=EDITED),
        // then a reload so PlanView.beforeEnter() re-fetches the meals
        planService.swapMeal(thursdayMeal.getId(), findAlternativeRecipe(thursdayMeal.getRecipeRef()),
                "vegetarian alternative for kid-friendly request");
        page.reload();

        // then: the pill appears on Thursday's row within the 60-second window
        assertThat(mealRow(page, thursdayMeal.getDate()).find(UnifiedSpan.class)
                .withTestId("meal-status-edited-pill")).isVisible();
    }

    /**
     * UC-003 BR-01: every AI-driven meal swap produces exactly one {@link
     * com.example.mise.domain.plan.MealEdit} row with the correct audit fields.
     * A service-level check; it opens no page.
     */
    @Test
    @DisplayName("An AI swap writes one audit row")
    void mealEditRowPersistedAfterAiSwap() {
        var household = householdService.findHousehold().orElseThrow();
        var plan = planService.findActivePlan(household.getId()).orElseThrow();

        LocalDate thursday = plan.getWeekStartDate()
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY));
        Meal thursdayMeal = planService.findMeals(plan.getId()).stream()
                .filter(m -> m.getDate().equals(thursday))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Thursday meal in seeded plan"));

        String originalRecipeRef = thursdayMeal.getRecipeRef();

        String newRecipeRef = recipeCatalog.findAll().stream()
                .map(r -> r.getId())
                .filter(id -> !id.equals(originalRecipeRef))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No alternative recipe found in catalog"));

        String reason = "vegetarian alternative for kid-friendly request";
        planService.swapMeal(thursdayMeal.getId(), newRecipeRef, reason);

        var edits = mealEditRepository.findByMealIdOrderByChangedAtDesc(thursdayMeal.getId());
        Assertions.assertThat(edits).hasSize(1);
        Assertions.assertThat(edits.get(0).getReason()).isEqualTo(reason);
        Assertions.assertThat(edits.get(0).getChangedBy()).isEqualTo(Meal.Editor.AI);
        Assertions.assertThat(edits.get(0).getPreviousRecipeRef()).isEqualTo(originalRecipeRef);
    }

    /**
     * UC-003 BR-03: attempting to swap a pinned meal throws {@link PinnedMealException};
     * after a reload the row is still there.
     */
    @Test
    @DisplayName("A pinned meal can't be swapped and its row stays")
    void pinnedMealRejectsSwap(UnifiedPage page) {
        // given: Monday pinned via the explicit-editor overload (UC-003)
        page.navigate(PlanView.class);
        Meal mondayMeal = findMealForDay(DayOfWeek.MONDAY);
        String newRecipeRef = findAlternativeRecipe(mondayMeal.getRecipeRef());
        planService.setPinned(mondayMeal.getId(), true, Meal.Editor.USER);

        // when / then: the swap is rejected
        Assertions.assertThatThrownBy(() ->
                planService.swapMeal(mondayMeal.getId(), newRecipeRef, "test"))
                .isInstanceOf(PinnedMealException.class);

        // then: after a reload Monday's row is still present
        page.reload();
        assertThat(mealRow(page, mondayMeal.getDate())).isVisible();
    }

    /**
     * UC-003 UI surface: the stubbed assistant reply appears in the message list and
     * the last-AI-message preview after typing into the chat dock on /plan.
     * Tool-call behaviour is covered by PlanToolsTest unit tests; this test only
     * asserts the chat UI surface.
     */
    @Test
    @DisplayName("A swap request in the chat shows the assistant's reply")
    void chatRoundTripOnPlanViewWithStubbedSwap(UnifiedPage page) {
        // given
        page.navigate(PlanView.class);
        String reply = "Swapped Thursday’s meal for a vegetarian option — kid-friendly pea risotto, under 30 min.";
        chatModel.queueReply(reply);

        // when
        sendInChatDock(page, "Make Thursday vegetarian, kid is having a friend over.");

        // then
        assertThat(chatDock(page).find(UnifiedMessageList.class)).hasMessageCount(2);
        assertThat(lastAiMessage(page)).hasTextContaining(reply);
    }

    /**
     * UC-003 locator stability: every pin button carries a {@code data-pin-date} attribute
     * matching its ISO date, allowing per-day pin assertions in later UCs.
     */
    @Test
    @DisplayName("Pin buttons carry their day's date")
    void pinButtonHasDataPinDateAttribute(UnifiedPage page) {
        page.navigate(PlanView.class);
        LocalDate monday = findMealForDay(DayOfWeek.MONDAY).getDate();

        assertThat(page.find(UnifiedButton.class).withTestId("meal-action-pin")
                .withAttribute("data-pin-date", monday.toString())).isVisible();
    }

    // ─── UC-004 ──────────────────────────────────────────────────────────────

    /**
     * Helper: finds the first meal for the given day of week in the active plan.
     */
    private Meal findMealForDay(DayOfWeek dayOfWeek) {
        var household = householdService.findHousehold().orElseThrow();
        var plan = planService.findActivePlan(household.getId()).orElseThrow();
        LocalDate targetDate = plan.getWeekStartDate()
                .with(TemporalAdjusters.nextOrSame(dayOfWeek));
        return planService.findMeals(plan.getId()).stream()
                .filter(m -> m.getDate().equals(targetDate))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No meal on " + dayOfWeek + " in seeded plan"));
    }

    /** Helper: a recipe ref that differs from {@code excludeRef}. */
    private String findAlternativeRecipe(String excludeRef) {
        return recipeCatalog.findAll().stream()
                .map(r -> r.getId())
                .filter(id -> !id.equals(excludeRef))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No alternative recipe found in catalog"));
    }

    /**
     * UC-004 AC: the undo icon button is visible on a row that has a MealEdit history row.
     */
    @Test
    @DisplayName("A row with edit history has an undo button")
    void undoButtonVisibleOnRowWithEditHistory(UnifiedPage page) {
        // given: one AI swap on Wednesday
        page.navigate(PlanView.class);
        Meal wednesday = findMealForDay(DayOfWeek.WEDNESDAY);
        planService.swapMeal(wednesday.getId(), findAlternativeRecipe(wednesday.getRecipeRef()),
                "vegetarian swap for IT");

        // when
        page.reload();

        // then
        assertThat(undoButton(page, "Wednesday")).isVisible();
    }

    /**
     * UC-004 AC: the undo icon button is NOT visible on a row with no edit history.
     * After seeding a swap for Wednesday only, Monday must NOT have an undo button.
     */
    @Test
    @DisplayName("A row without edit history has no undo button")
    void undoButtonNotVisibleOnRowWithNoHistory(UnifiedPage page) {
        // given: Wednesday swapped, so the feature is active, but Monday has no history
        page.navigate(PlanView.class);
        Meal wednesday = findMealForDay(DayOfWeek.WEDNESDAY);
        planService.swapMeal(wednesday.getId(), findAlternativeRecipe(wednesday.getRecipeRef()),
                "vegetarian swap for IT");

        // when
        page.reload();

        // then
        assertThat(undoButton(page, "Wednesday")).isVisible();
        assertThat(undoButton(page, "Monday").visible()).doesNotExist();
    }

    /**
     * UC-004 AC #3: clicking the row undo button (a) restores the meal's recipeRef,
     * (b) creates a NEW MealEdit row with reason starting "Undo of edit",
     * (c) the post-undo MealEdit count for that meal = pre-undo count + 1.
     */
    @Test
    @DisplayName("Undo on a row restores the previous recipe and writes an undo audit row")
    void clickingRowUndoRestoresPreviousRecipeAndWritesUndoAuditRow(UnifiedPage page) {
        // given: Thursday swapped
        page.navigate(PlanView.class);
        Meal thursday = findMealForDay(DayOfWeek.THURSDAY);
        String originalRef = thursday.getRecipeRef();
        planService.swapMeal(thursday.getId(), findAlternativeRecipe(originalRef), "swap for IT undo test");
        int preUndoCount = mealEditRepository.findByMealIdOrderByChangedAtDesc(thursday.getId()).size();
        page.reload();

        // when
        undoButton(page, "Thursday").click();

        // then: the user is told ...
        assertThat(page.find(UnifiedNotification.class).withText("Undo applied")).exists();

        // ... the recipe is restored ...
        Meal reloaded = mealRepository.findById(thursday.getId()).orElseThrow();
        Assertions.assertThat(reloaded.getRecipeRef()).isEqualTo(originalRef);

        // ... and a new undo audit row is written
        var editsAfter = mealEditRepository.findByMealIdOrderByChangedAtDesc(thursday.getId());
        Assertions.assertThat(editsAfter).hasSize(preUndoCount + 1);
        Assertions.assertThat(editsAfter.get(0).getReason()).startsWith("Undo of edit");
    }

    /**
     * UC-004 AC: clicking the "why?" button for a day with edit history causes a user
     * message containing "why did you change [Day]?" to appear in the chat MessageList,
     * and the stubbed assistant reply lands as a second message.
     */
    @Test
    @DisplayName("Why? on an edited row asks the chat and shows the reply")
    void whyButtonClickSubmitsChatMessageAndReceivesReply(UnifiedPage page) {
        // given: Friday swapped
        page.navigate(PlanView.class);
        Meal friday = findMealForDay(DayOfWeek.FRIDAY);
        planService.swapMeal(friday.getId(), findAlternativeRecipe(friday.getRecipeRef()),
                "fish swap for IT why test");
        page.reload();
        chatModel.queueReply("I swapped Friday because of your fish allergy preference.");
        // Focus the chat input to expand the dock so the message list is visible
        chatDock(page).find(UnifiedMessageInput.class).element().focus();

        // when: "why?" pre-fills and submits the chat prompt
        whyButton(page, "Friday").click();

        // then: the user turn, then the assistant reply
        UnifiedMessageList messages = chatDock(page).find(UnifiedMessageList.class);
        assertThat(messages).hasMessageCount(2);
        Assertions.assertThat(messages.getMessageTexts().get(0)).contains("why did you change Friday?");
    }

    /**
     * UC-004 AC: the "why?" button is visible on a row that has edit history.
     */
    @Test
    @DisplayName("A row with edit history has a why? button")
    void whyButtonVisibleOnRowWithEditHistory(UnifiedPage page) {
        // given
        page.navigate(PlanView.class);
        Meal tuesday = findMealForDay(DayOfWeek.TUESDAY);
        planService.swapMeal(tuesday.getId(), findAlternativeRecipe(tuesday.getRecipeRef()),
                "swap for IT why-visible test");

        // when
        page.reload();

        // then
        assertThat(whyButton(page, "Tuesday")).isVisible();
    }

    /**
     * UC-004 AC: attempting to undo a pinned meal shows a "Cannot undo — meal is pinned"
     * Vaadin notification. The service layer throws PinnedMealException; the UI surfaces it.
     */
    @Test
    @DisplayName("Undo on a pinned meal says it is pinned")
    void undoOnPinnedMealShowsPinnedNotification(UnifiedPage page) {
        // given: Monday edited (so the undo button appears), then pinned
        page.navigate(PlanView.class);
        Meal monday = findMealForDay(DayOfWeek.MONDAY);
        planService.swapMeal(monday.getId(), findAlternativeRecipe(monday.getRecipeRef()),
                "swap for IT pinned-undo test");
        planService.setPinned(monday.getId(), true, Meal.Editor.USER);
        page.reload();

        // when
        undoButton(page, "Monday").click();

        // then
        assertThat(page.find(UnifiedNotification.class)
                .withTextContaining("Cannot undo — meal is pinned")).exists();
    }

    /**
     * UC-004 AC: after an undo the weekly KPI strip is still present and visible,
     * confirming that a successful undo doesn't break the view's KPI rendering.
     */
    @Test
    @DisplayName("The KPI strip is still shown after an undo")
    void kpiStripStillRenderedAfterUndo(UnifiedPage page) {
        // given
        page.navigate(PlanView.class);
        Meal wednesday = findMealForDay(DayOfWeek.WEDNESDAY);
        planService.swapMeal(wednesday.getId(), findAlternativeRecipe(wednesday.getRecipeRef()),
                "swap for IT kpi-post-undo test");
        page.reload();

        // when
        undoButton(page, "Wednesday").click();

        // then
        assertThat(page.find(UnifiedNotification.class).withText("Undo applied")).exists();
        assertThat(kpiStrip(page)).isVisible();
    }

    // ── Lookups and user tasks ───────────────────────────────────────────────

    private static UnifiedContainer kpiStrip(UnifiedPage page) {
        return page.find(UnifiedContainer.class).withTestId("kpi-strip");
    }

    private static UnifiedDiv mealRow(UnifiedPage page, LocalDate date) {
        return page.find(UnifiedDiv.class).withTestId("meal-row").withAttribute("data-meal-date", date.toString());
    }

    private static UnifiedButton undoButton(UnifiedPage page, String day) {
        return page.find(UnifiedButton.class).withAccessibleName("Undo " + day);
    }

    private static UnifiedButton whyButton(UnifiedPage page, String day) {
        return page.find(UnifiedButton.class).withAccessibleName("Why " + day);
    }

    private static UnifiedContainer chatDock(UnifiedPage page) {
        return page.find(UnifiedContainer.class).withTestId("chat-dock");
    }

    private static UnifiedDiv lastAiMessage(UnifiedPage page) {
        return chatDock(page).find(UnifiedDiv.class).withTestId("chat-last-ai-message");
    }

    private static void sendInChatDock(UnifiedPage page, String prompt) {
        page.step("Ask the chat: " + prompt, () -> {
            UnifiedMessageInput input = chatDock(page).find(UnifiedMessageInput.class);
            input.send(prompt);
            // Focus expands the dock (CSS :focus-within) so the history is on screen
            input.element().focus();
        });
    }
}
