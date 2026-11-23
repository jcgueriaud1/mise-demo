package com.example.mise.it;

import com.example.mise.capabilities.recipes.RecipeCatalog;
import com.example.mise.domain.conversation.ConversationMessage;
import com.example.mise.domain.conversation.ConversationMessageRepository;
import com.example.mise.domain.household.Household;
import com.example.mise.domain.household.HouseholdRepository;
import com.example.mise.domain.household.HouseholdService;
import com.example.mise.domain.plan.MealEditRepository;
import com.example.mise.domain.plan.MealRepository;
import com.example.mise.domain.plan.PlanRepository;
import com.example.mise.domain.plan.PlanService;
import com.example.mise.it.support.TestAiConfig;
import com.example.mise.it.support.TestChatModel;
import com.example.mise.ui.plan.PlanView;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedDiv;
import com.vaadin.testing.unified.component.UnifiedMessageInput;
import com.vaadin.testing.unified.component.UnifiedMessageList;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;


import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

/**
 * UC-008 chat-dock interaction model — covers BR-08 (two-state dock), BR-09
 * (scroll-to-latest-on-expand), and BR-10 (.ai-working indicator).
 *
 * <p>Setup mirrors {@link PlanViewIT}: seed a household + active plan so the
 * Plan view (which hosts the shared dock) renders fully. The chat dock lives
 * in MainLayout so any view would do; /plan is convenient.
 *
 * <p>BR-10 specifically pauses the streaming response via {@link
 * com.example.mise.it.support.TestChatModel#pauseNextResponse()} so the test
 * can observe the in-flight `.ai-working` state on the dock between submit and
 * response-complete. Releasing the latch lets the response finish and the
 * class clear.
 */
@UnifiedSpringBootTest
@ActiveProfiles("it")
@Import(TestAiConfig.class)
class ChatDockIT {

    @Autowired
    protected TestChatModel chatModel;

    @BeforeEach
    void resetChatModelBetweenTests() {
        chatModel.reset();
    }

    @Autowired private HouseholdService householdService;
    @Autowired private HouseholdRepository householdRepository;
    @Autowired private PlanService planService;
    @Autowired private PlanRepository planRepository;
    @Autowired private MealRepository mealRepository;
    @Autowired private MealEditRepository mealEditRepository;
    @Autowired private RecipeCatalog recipeCatalog;
    @Autowired private ConversationMessageRepository conversationMessageRepository;

    @BeforeEach
    void seed() {
        Household h = new Household();
        h.setName("IT ChatDock Household");
        h.setSize(2);
        h.setWeeklyBudget(new BigDecimal("100.00"));
        h.setAllergies(List.of());
        h.setHatedFoods(List.of());
        householdService.save(h);

        var household = householdService.findHousehold().orElseThrow();
        planService.generateActivePlan(household, recipeCatalog);

        // BR-09 needs enough chat history that the message list is scrollable.
        // Seed 12 alternating user/assistant rows; the orchestrator rehydrates
        // them when MainLayout activates the household. ConversationMessage has
        // no household FK (single-household demo) and @PrePersist fills createdAt.
        for (int i = 0; i < 12; i++) {
            ConversationMessage row = new ConversationMessage();
            row.setRole(i % 2 == 0 ? ConversationMessage.Role.USER
                                   : ConversationMessage.Role.ASSISTANT);
            row.setContent(i % 2 == 0
                    ? "Earlier user turn #" + (i / 2 + 1)
                    : "Earlier assistant reply #" + (i / 2 + 1)
                            + " — padded text to make the row tall enough that 12 of them"
                            + " produce a scrollable history.");
            row.setViewContext(ConversationMessage.ViewContext.PLAN);
            conversationMessageRepository.save(row);
        }
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
    }

    /**
     * BR-08 (two-state dock): blurred = collapsed (max-height 0, single-line
     * preview visible); focused = expanded (max-height > 0, preview hidden).
     * Computed CSS needs a real browser.
     */
    @Test
    @RequiresBrowser
    @DisplayName("The chat dock expands on focus and collapses on blur")
    void dockExpandsOnFocusAndCollapsesOnBlur(UnifiedPage page) {
        // given: collapsed, the one-line preview of the last reply shown
        page.navigate(PlanView.class);
        UnifiedContainer dock = chatDock(page);
        UnifiedMessageList history = dock.find(UnifiedMessageList.class);
        UnifiedDiv preview = dock.find(UnifiedDiv.class).withClassName("mise-last-ai-message");
        assertThat(dock).isVisible();
        assertThat(history).hasCssValue("max-height", "0px");
        assertThat(preview).isVisible();

        // when: the input takes focus, the dock expands (max-height transitions over ~180 ms)
        dock.find(UnifiedMessageInput.class).element().focus();

        // then
        page.step("see the chat history expanded", () ->
                page.waitUntil(() -> maxHeightPx(history) > 100));
        assertThat(preview).isHidden();

        // when: a click outside the dock blurs it
        page.find(UnifiedContainer.class).withTestId("kpi-strip").click();

        // then
        assertThat(history).hasCssValue("max-height", "0px");
        assertThat(preview).isVisible();
    }

    /**
     * BR-09 (scroll-to-latest on expand): after seeding ~12 messages, focusing
     * the input must scroll the message list to the bottom regardless of where
     * it was before.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    @DisplayName("Expanding the chat dock scrolls to the latest message")
    void expandScrollsToLatestMessage(UnifiedPage page) {
        // given: the dock open
        page.navigate(PlanView.class);
        UnifiedContainer dock = chatDock(page);
        UnifiedMessageList history = dock.find(UnifiedMessageList.class);
        UnifiedMessageInput input = dock.find(UnifiedMessageInput.class);
        input.element().focus();
        page.step("see the chat history expanded", () ->
                page.waitUntil(() -> maxHeightPx(history) > 100));

        // Escape Hatch kept: JavaScript, the Unified API can neither set nor read a scroll position
        Locator historyEl = page.unwrap(Page.class).getByTestId("chat-message-list");
        historyEl.evaluate("h => { h.scrollTop = 0; }");

        // when: blur, then focus again to trigger the focusin handler
        page.find(UnifiedContainer.class).withTestId("kpi-strip").click();
        assertThat(history).hasCssValue("max-height", "0px");
        input.element().focus();

        // then: the transitionend-driven scroll lands at the bottom
        // Escape Hatch kept: JavaScript, scroll position (see above)
        page.step("see the chat history scrolled to the latest message", () ->
                page.waitUntil(() -> (Boolean) historyEl.evaluate(
                        "h => h.scrollHeight - (h.scrollTop + h.clientHeight) < 5")));
    }

    /**
     * BR-10 (.ai-working indicator): on submit the dock gains `.ai-working`;
     * once the orchestrator's responseCompleteListener fires (after we release
     * the pause-latch), the class is removed.
     */
    @Test
    @DisplayName("The chat dock shows it is working while a reply streams")
    void aiWorkingClassToggledAroundSubmit(UnifiedPage page) {
        // given: a reply that waits for the test's go-ahead
        page.navigate(PlanView.class);
        CountDownLatch responseLatch = chatModel.pauseNextResponse();
        chatModel.queueReply("Sure — Thursday is Roasted Vegetable Pasta.");

        // when
        chatDock(page).find(UnifiedMessageInput.class).send("Swap Thursday for something vegetarian.");

        // then: .ai-working is set while streaming ...
        assertThat(chatDock(page).withClassName("ai-working")).exists();

        // ... and cleared once the response completes
        responseLatch.countDown();
        assertThat(chatDock(page).withClassName("ai-working")).doesNotExist();
    }

    private static UnifiedContainer chatDock(UnifiedPage page) {
        return page.find(UnifiedContainer.class).withTestId("chat-dock");
    }

    private static double maxHeightPx(UnifiedMessageList history) {
        return Double.parseDouble(history.element().getCssValue("max-height").replace("px", ""));
    }
}
