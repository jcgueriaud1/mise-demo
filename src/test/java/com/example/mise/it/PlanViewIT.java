package com.example.mise.it;

import com.example.mise.ui.plan.PlanView;
import com.example.mise.it.support.TestChatModel;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import com.example.mise.it.support.TestAiConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.example.mise.it.support.wrappers.UnifiedMessageInput;
import com.example.mise.it.support.wrappers.UnifiedMessageList;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedDiv;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedParagraph;
import com.vaadin.testing.unified.component.UnifiedSpan;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import com.microsoft.playwright.Page;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

/**
 * UC-002 + UC-003 Playwright IT — Plan view with seeded household + active plan.
 *
 * <p>Lifecycle: a {@code @BeforeEach} seeds a Household and an ACTIVE Plan; each test
 * then opens /plan, so the view renders a full 7-row meal grid.
 *
 * <p>Chat-round-trip tests stub the {@link com.example.mise.it.support.TestChatModel}
 * via {@code chatModel.queueReply(...)} before submitting user input. No live LLM
 * endpoint is contacted.
 *
 * <p>Cleanup in {@code @AfterEach} deletes rows in FK-safe order:
 * meal_edit → conversation_message → meal → plan → household.
 */
@ActiveProfiles("it")
@Import(TestAiConfig.class)
@UnifiedSpringBootTest
class PlanViewIT {


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

    /** Seeds the household and its active plan before each test opens /plan. */
    @BeforeEach
    void seedBeforeNavigation() throws Exception {
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
    @DisplayName("The plan page has its browser tab title")
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void hasPageTitle(UnifiedPage page) {
        // Escape Hatch kept: the Unified API has no page-title assertion
        Page playwright = page.unwrap(Page.class);
        page.navigate(PlanView.class);
        com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(playwright).hasTitle("Mise — Plan");
    }

    /**
     * UC-002 AC: the weekly KPI strip with all four cards is visible.
     * BR-03: stats are computed from the currently shown meals.
     */
    @Test
    @DisplayName("The plan shows the weekly KPI strip with its four cards")
    void kpiStripRenders(UnifiedPage page) {
        page.navigate(PlanView.class);
        assertThat(byTestId(page, "kpi-strip")).isVisible();
        assertThat(byTestId(page, "kpi-card-weekly-cost")).isVisible();
        assertThat(byTestId(page, "kpi-card-avg-meal")).isVisible();
        assertThat(byTestId(page, "kpi-card-total-prep")).isVisible();
        assertThat(byTestId(page, "kpi-card-avg-kcal")).isVisible();
    }

    /**
     * UC-002 AC: the meal grid has exactly 7 rows (Mon–Sun).
     * BR-02: grid always has 7 rows; missing slots show empty placeholders.
     */
    @Test
    @DisplayName("The plan shows a meal for each of the seven days")
    void mealGridHasSevenRows(UnifiedPage page) {
        page.navigate(PlanView.class);
        assertThat(page.find(UnifiedDiv.class).withTestId("meal-row")).hasCount(7);
    }

    /**
     * UC-002 AC: the cost-by-category panel is visible and shows its title.
     */
    @Test
    @DisplayName("The plan shows the cost by category panel")
    void costByCategoryPanelRenders(UnifiedPage page) {
        page.navigate(PlanView.class);
        UnifiedContainer panel = byTestId(page, "cost-by-category-panel");
        assertThat(panel).isVisible();
        assertThat(panel.find(UnifiedParagraph.class).withText("COST BY CATEGORY")).isVisible();
    }

    /**
     * UC-002 AC: chat round-trip via the shared dock works correctly on the Plan view.
     * The stubbed assistant reply appears in the MessageList and updates the preview.
     */
    @Test
    @DisplayName("Asking the assistant from the plan shows the question, the reply and its preview")
    void chatRoundTripWorksOnSharedDock(UnifiedPage page) {
        // given
        page.navigate(PlanView.class);
        chatModel.queueReply("Friday is creamy salmon pasta.");

        // when
        sendChatMessage(page, "What's on Friday?");

        // then
        waitForChatMessages(page, 2);
        assertThat(byTestId(page, "chat-last-ai-message")).hasTextContaining("Friday is creamy salmon pasta.");
    }

    /**
     * UC-002 / BR-06: after a chat round-trip on /plan, the persisted messages carry
     * ViewContext.PLAN so the conversation is correctly attributed to the Plan view.
     */
    @Test
    @DisplayName("A conversation started on the plan is saved as a PLAN conversation")
    void conversationPersistedWithPlanViewContext(UnifiedPage page) {
        // given
        page.navigate(PlanView.class);
        chatModel.queueReply("Friday is creamy salmon pasta.");

        // when
        sendChatMessage(page, "What's on Friday?");
        waitForChatMessages(page, 2);

        // then: both the user and assistant messages are stamped PLAN
        var rows = conversationMessageRepository.findAll();
        Assertions.assertThat(rows)
                .filteredOn(r -> r.getViewContext() == ConversationMessage.ViewContext.PLAN)
                .hasSizeGreaterThanOrEqualTo(2);
    }

    // ── UC-003 tests ──────────────────────────────────────────────────────────

    /**
     * UC-003 UI requirement: a pin icon button is visible on every meal row (Mon–Sun).
     */
    @Test
    @DisplayName("Every day of the plan has a pin button")
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
    @DisplayName("A meal the AI swapped is marked edited after a reload")
    void editedPillRendersAfterAiSwap(UnifiedPage page) {
        // given
        page.navigate(PlanView.class);
        Meal thursdayMeal = findMealForDay(DayOfWeek.THURSDAY);

        // when: an AI swap (sets lastEditedBy=AI, lastEditedAt=now, status=EDITED), then a reload
        planService.swapMeal(thursdayMeal.getId(), findAlternativeRecipe(thursdayMeal.getRecipeRef()),
                "vegetarian alternative for kid-friendly request");
        page.reload();

        // then
        assertThat(mealRow(page, thursdayMeal.getDate()).find(UnifiedSpan.class)
                .withTestId("meal-status-edited-pill")).isVisible();
    }

    /**
     * UC-003 BR-01: every AI-driven meal swap produces exactly one {@link
     * com.example.mise.domain.plan.MealEdit} row with the correct audit fields.
     */
    @Test
    @DisplayName("An AI meal swap writes one audit row with reason, editor and previous recipe")
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
     * after a reload the row still shows the original recipe.
     */
    @Test
    @DisplayName("A pinned meal can't be swapped and is still shown after a reload")
    void pinnedMealRejectsSwap(UnifiedPage page) {
        // given: Monday is pinned (UC-003 explicit-editor overload)
        page.navigate(PlanView.class);
        Meal mondayMeal = findMealForDay(DayOfWeek.MONDAY);
        String newRecipeRef = findAlternativeRecipe(mondayMeal.getRecipeRef());
        planService.setPinned(mondayMeal.getId(), true, Meal.Editor.USER);

        // when / then: swapping it is refused
        Assertions.assertThatThrownBy(() ->
                planService.swapMeal(mondayMeal.getId(), newRecipeRef, "test"))
                .isInstanceOf(PinnedMealException.class);

        // then: after a reload Monday's row is still there
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
    @DisplayName("Asking for a swap from the plan shows the assistant's reply")
    void chatRoundTripOnPlanViewWithStubbedSwap(UnifiedPage page) {
        // given
        page.navigate(PlanView.class);
        String reply = "Swapped Thursday’s meal for a vegetarian option — kid-friendly pea risotto, under 30 min.";
        chatModel.queueReply(reply);

        // when
        sendChatMessage(page, "Make Thursday vegetarian, kid is having a friend over.");

        // then
        waitForChatMessages(page, 2);
        assertThat(byTestId(page, "chat-last-ai-message")).hasTextContaining(reply);
    }

    /**
     * UC-003 locator stability: every meal row's pin button carries a {@code data-pin-date}
     * attribute matching its ISO date, allowing per-day pin assertions in later UCs.
     */
    @Test
    @DisplayName("Each day's pin button can be found by its date")
    void pinButtonHasDataPinDateAttribute(UnifiedPage page) {
        page.navigate(PlanView.class);
        LocalDate monday = findMealForDay(DayOfWeek.MONDAY).getDate();
        assertThat(page.find(UnifiedButton.class).withAttribute("data-pin-date", monday.toString())).isVisible();
    }

    // ─── UC-004 ──────────────────────────────────────────────────────────────

    /**
     * Helper: finds the first meal for the given day of week in the active plan
     * and returns an alternative recipe ref that differs from the meal's current one.
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
    @DisplayName("A day the AI changed has an undo button")
    void undoButtonVisibleOnRowWithEditHistory(UnifiedPage page) {
        // given
        page.navigate(PlanView.class);
        aiSwap(DayOfWeek.WEDNESDAY, "vegetarian swap for IT");

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
    @DisplayName("A day the AI didn't change has no undo button")
    void undoButtonNotVisibleOnRowWithNoHistory(UnifiedPage page) {
        // given: Wednesday swapped (the feature is active), Monday untouched
        page.navigate(PlanView.class);
        aiSwap(DayOfWeek.WEDNESDAY, "vegetarian swap for IT");

        // when
        page.reload();

        // then
        assertThat(undoButton(page, "Wednesday")).isVisible();
        assertThat(undoButton(page, "Monday")).doesNotExist();
    }

    /**
     * UC-004 AC #3: clicking the row undo button (a) restores the meal's recipeRef,
     * (b) creates a NEW MealEdit row with reason starting "Undo of edit",
     * (c) the post-undo MealEdit count for that meal = pre-undo count + 1.
     */
    @Test
    @DisplayName("Undoing an AI change restores the previous meal and records the undo")
    void clickingRowUndoRestoresPreviousRecipeAndWritesUndoAuditRow(UnifiedPage page) {
        // given
        page.navigate(PlanView.class);
        Meal thursday = findMealForDay(DayOfWeek.THURSDAY);
        String originalRef = thursday.getRecipeRef();
        planService.swapMeal(thursday.getId(), findAlternativeRecipe(originalRef), "swap for IT undo test");
        int preUndoCount = mealEditRepository.findByMealIdOrderByChangedAtDesc(thursday.getId()).size();
        page.reload();

        // when
        undoButton(page, "Thursday").click();

        // then
        assertThat(page.find(UnifiedNotification.class).withTextContaining("Undo applied")).isOpen();
        Meal reloaded = mealRepository.findById(thursday.getId()).orElseThrow();
        Assertions.assertThat(reloaded.getRecipeRef()).isEqualTo(originalRef);
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
    @DisplayName("Asking why a day was changed sends the question to the assistant")
    void whyButtonClickSubmitsChatMessageAndReceivesReply(UnifiedPage page) {
        // given
        page.navigate(PlanView.class);
        aiSwap(DayOfWeek.FRIDAY, "fish swap for IT why test");
        page.reload();
        chatModel.queueReply("I swapped Friday because of your fish allergy preference.");

        // when: the "why?" button pre-fills and submits the chat prompt
        page.find(UnifiedButton.class).withAccessibleName("Why Friday").click();

        // then: the user turn with the why-prompt, then the reply
        UnifiedMessageList messages = waitForChatMessages(page, 2);
        Assertions.assertThat(messages.getMessageText(0)).contains("why did you change Friday?");
    }

    /**
     * UC-004 AC: the "why?" button is visible on a row that has edit history.
     */
    @Test
    @DisplayName("A day the AI changed has a why button")
    void whyButtonVisibleOnRowWithEditHistory(UnifiedPage page) {
        // given
        page.navigate(PlanView.class);
        aiSwap(DayOfWeek.TUESDAY, "swap for IT why-visible test");

        // when
        page.reload();

        // then
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Why Tuesday")).isVisible();
    }

    /**
     * UC-004 AC: attempting to undo a pinned meal shows a "Cannot undo — meal is pinned"
     * notification. The service layer throws PinnedMealException; the UI surfaces it.
     */
    @Test
    @DisplayName("Undoing a change on a pinned meal is refused with a notification")
    void undoOnPinnedMealShowsPinnedNotification(UnifiedPage page) {
        // given: Monday has an AI edit (so the undo button shows) and is then pinned
        page.navigate(PlanView.class);
        Meal monday = aiSwap(DayOfWeek.MONDAY, "swap for IT pinned-undo test");
        planService.setPinned(monday.getId(), true, Meal.Editor.USER);
        page.reload();

        // when
        undoButton(page, "Monday").click();

        // then
        assertThat(page.find(UnifiedNotification.class).withTextContaining("Cannot undo — meal is pinned")).isOpen();
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
        aiSwap(DayOfWeek.WEDNESDAY, "swap for IT kpi-post-undo test");
        page.reload();

        // when
        undoButton(page, "Wednesday").click();

        // then
        assertThat(page.find(UnifiedNotification.class).withTextContaining("Undo applied")).isOpen();
        assertThat(byTestId(page, "kpi-strip")).isVisible();
    }

    // ── lookups and user tasks ────────────────────────────────────────────────

    private static UnifiedContainer byTestId(UnifiedPage page, String testId) {
        return page.find(UnifiedContainer.class).withTestId(testId);
    }

    private static UnifiedDiv mealRow(UnifiedPage page, LocalDate date) {
        return page.find(UnifiedDiv.class).withTestId("meal-row").withAttribute("data-meal-date", date.toString());
    }

    private static UnifiedButton undoButton(UnifiedPage page, String dayName) {
        return page.find(UnifiedButton.class).withAccessibleName("Undo " + dayName);
    }

    private static void sendChatMessage(UnifiedPage page, String message) {
        page.find(UnifiedContainer.class).withTestId("chat-dock").find(UnifiedMessageInput.class).send(message);
    }

    private static UnifiedMessageList waitForChatMessages(UnifiedPage page, int count) {
        UnifiedMessageList messages = page.find(UnifiedMessageList.class).withTestId("chat-message-list");
        page.waitUntil(() -> messages.getMessageCount() == count);
        return messages;
    }

    /** An AI swap of the given day's meal, done through the service as the assistant's tool would. */
    private Meal aiSwap(DayOfWeek day, String reason) {
        Meal meal = findMealForDay(day);
        planService.swapMeal(meal.getId(), findAlternativeRecipe(meal.getRecipeRef()), reason);
        return meal;
    }
}
