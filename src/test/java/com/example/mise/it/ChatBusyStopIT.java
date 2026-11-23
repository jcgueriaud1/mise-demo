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
import com.example.mise.it.support.TestAiConfig;
import com.example.mise.it.support.TestChatModel;
import com.example.mise.ui.plan.PlanView;

import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.vaadin.addons.dramafinder.element.MessageInputElement;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.assertj.core.api.Assertions;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * UC-013: chat busy feedback + "stop" to cancel an in-flight turn.
 *
 * <p>Deterministic against the stubbed {@link com.example.mise.it.support.TestChatModel}:
 * <ul>
 *   <li>{@code pauseNextResponse()} holds a turn in flight so a second submit can be
 *       observed being suppressed with a busy note (BR-01, BR-02).</li>
 *   <li>{@code holdNextResponseOpen(...)} keeps the stream open with no natural completion,
 *       so a "stop" submit is what ends it — proving the cancel path (BR-03, BR-06).</li>
 *   <li>"stop" while idle needs no model at all (BR-04).</li>
 * </ul>
 *
 * <p>Setup mirrors {@link ChatDockIT}: seed a household + active plan so the Plan view
 * (which hosts the shared dock) renders.
 */
@UnifiedSpringBootTest
@ActiveProfiles("it")
@Import(TestAiConfig.class)
class ChatBusyStopIT {

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
    public void setupTest() throws Exception {
        Household h = new Household();
        h.setName("IT BusyStop Household");
        h.setSize(2);
        h.setWeeklyBudget(new BigDecimal("100.00"));
        h.setAllergies(List.of());
        h.setHatedFoods(List.of());
        householdService.save(h);

        var household = householdService.findHousehold().orElseThrow();
        planService.generateActivePlan(household, recipeCatalog);
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
     * BR-01 / BR-02: a prompt submitted while a turn is in flight is not sent — a transient
     * busy note appears and the ignored prompt is not echoed into the conversation.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void submittingWhileBusy_showsBusyNote_andSuppressesPrompt(UnifiedPage page) throws Exception {
        Page playwright = page.unwrap(Page.class);
        page.navigate(PlanView.class);
        CountDownLatch responseLatch = chatModel.pauseNextResponse();
        chatModel.queueReply("Sure — Thursday is Roasted Vegetable Pasta.");

        // TODO unified: Dramafinder Escape Hatch: playwright.getByTestId("chat-dock")
        var chatDock = playwright.getByTestId("chat-dock");
        // TODO unified: Dramafinder Escape Hatch: MessageInputElement.get(chatDock)
        MessageInputElement input = MessageInputElement.get(chatDock);

        // First turn — held in flight by the pause latch.
        // TODO unified: Dramafinder Escape Hatch: input.typeAndSubmit("Swap Thursday for something vegetarian.")
        input.typeAndSubmit("Swap Thursday for something vegetarian.");
        // TODO unified: Dramafinder Escape Hatch: playwright.waitForFunction("d => d.classList.contains('ai-working')", chatDock.elementHandle())
        playwright.waitForFunction("d => d.classList.contains('ai-working')", chatDock.elementHandle());

        // Second submit while busy: must be suppressed and surfaced as a busy note.
        // TODO unified: Dramafinder Escape Hatch: input.typeAndSubmit("and also add chicken on Friday")
        input.typeAndSubmit("and also add chicken on Friday");

        // TODO unified: Dramafinder Escape Hatch: playwright.getByTestId("chat-busy-note")
        var busyNote = playwright.getByTestId("chat-busy-note");
        // TODO unified: Dramafinder Escape Hatch: playwright.waitForFunction("n => n && getComputedStyle(n).display !== 'none' && n.textContent.trim().length > 0",busyNote.elementHandle())
        playwright.waitForFunction("n => n && getComputedStyle(n).display !== 'none' && n.textContent.trim().length > 0",
                busyNote.elementHandle());
        // TODO unified: Dramafinder Escape Hatch: assertThat(busyNote).isVisible()
        assertThat(busyNote).isVisible();
        // TODO unified: Dramafinder Escape Hatch: assertThat(busyNote).containsText("still working")
        assertThat(busyNote).containsText("still working");

        // The suppressed prompt must NOT appear as a chat message.
        // TODO unified: Dramafinder Escape Hatch: playwright.evaluate("() => [...document.querySelectorAll('vaadin-message')]"+ ".some(m => (m.textContent || '').includes('add chicken on Friday'))")
        Boolean suppressedEchoed = (Boolean) playwright.evaluate(
                "() => [...document.querySelectorAll('vaadin-message')]"
                + ".some(m => (m.textContent || '').includes('add chicken on Friday'))");
        Assertions.assertThat(suppressedEchoed)
                .as("the ignored second prompt must not be echoed into the conversation").isFalse();

        // Let the first turn finish; the note clears on response-complete.
        responseLatch.countDown();
        // TODO unified: Dramafinder Escape Hatch: playwright.waitForFunction("d => !d.classList.contains('ai-working')", chatDock.elementHandle())
        playwright.waitForFunction("d => !d.classList.contains('ai-working')", chatDock.elementHandle());
        // TODO unified: Dramafinder Escape Hatch: playwright.waitForFunction("n => getComputedStyle(n).display === 'none' || n.offsetParent === null",busyNote.elementHandle())
        playwright.waitForFunction("n => getComputedStyle(n).display === 'none' || n.offsetParent === null",
                busyNote.elementHandle());
    }

    /**
     * BR-04: "stop" with nothing running is a no-op — a brief note appears and the model is
     * never called.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void stopWhileIdle_showsNothingRunning_andNeverCallsModel(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate(PlanView.class);
        // TODO unified: Dramafinder Escape Hatch: playwright.getByTestId("chat-dock")
        var chatDock = playwright.getByTestId("chat-dock");
        // TODO unified: Dramafinder Escape Hatch: MessageInputElement.get(chatDock).typeAndSubmit("stop")
        MessageInputElement.get(chatDock).typeAndSubmit("stop");

        // TODO unified: Dramafinder Escape Hatch: playwright.getByTestId("chat-busy-note")
        var busyNote = playwright.getByTestId("chat-busy-note");
        // TODO unified: Dramafinder Escape Hatch: playwright.waitForFunction("n => n && getComputedStyle(n).display !== 'none' && n.textContent.trim().length > 0",busyNote.elementHandle())
        playwright.waitForFunction("n => n && getComputedStyle(n).display !== 'none' && n.textContent.trim().length > 0",
                busyNote.elementHandle());
        // TODO unified: Dramafinder Escape Hatch: assertThat(busyNote).isVisible()
        assertThat(busyNote).isVisible();
        // TODO unified: Dramafinder Escape Hatch: assertThat(busyNote).containsText("Nothing is running")
        assertThat(busyNote).containsText("Nothing is running");

        Assertions.assertThat(chatModel.receivedPrompts())
                .as("idle \"stop\" must never reach the model").isEmpty();
    }

    /**
     * BR-03 / BR-06: typing "stop" while a turn is in flight cancels it. The stream is held
     * open (never completes on its own), so the turn ends only because of the cancel: the
     * .ai-working state clears and the partial reply is kept with a "(stopped)" marker.
     * Casing/whitespace are ignored ("  STOP ").
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void stopWhileBusy_cancelsTurn_keepsPartialWithStoppedMarker(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate(PlanView.class);
        chatModel.holdNextResponseOpen("Here is what I am working on");

        // TODO unified: Dramafinder Escape Hatch: playwright.getByTestId("chat-dock")
        var chatDock = playwright.getByTestId("chat-dock");
        // TODO unified: Dramafinder Escape Hatch: MessageInputElement.get(chatDock)
        MessageInputElement input = MessageInputElement.get(chatDock);

        // TODO unified: Dramafinder Escape Hatch: input.typeAndSubmit("Write me a very long detailed plan for the whole week.")
        input.typeAndSubmit("Write me a very long detailed plan for the whole week.");
        // TODO unified: Dramafinder Escape Hatch: playwright.waitForFunction("d => d.classList.contains('ai-working')", chatDock.elementHandle())
        playwright.waitForFunction("d => d.classList.contains('ai-working')", chatDock.elementHandle());
        // The held partial chunk has streamed into the assistant bubble.
        // TODO unified: Dramafinder Escape Hatch: playwright.waitForFunction("() => [...document.querySelectorAll('vaadin-message')]"+ ".some(m => (m.textContent || '').includes('what I am working on'))")
        playwright.waitForFunction(
                "() => [...document.querySelectorAll('vaadin-message')]"
                + ".some(m => (m.textContent || '').includes('what I am working on'))");

        int promptsBeforeStop = chatModel.receivedPrompts().size();

        // Cancel. The model only emitted one chunk and never completed, so the turn can only
        // end via this cancel — not via a natural completion or the 10s stub timeout.
        // TODO unified: Dramafinder Escape Hatch: input.typeAndSubmit("  STOP ")
        input.typeAndSubmit("  STOP ");

        // .ai-working clears because the stream was cancelled.
        // TODO unified: Dramafinder Escape Hatch: playwright.waitForFunction("d => !d.classList.contains('ai-working')", chatDock.elementHandle())
        playwright.waitForFunction("d => !d.classList.contains('ai-working')", chatDock.elementHandle());

        // The partial reply survives, marked (stopped).
        // TODO unified: Dramafinder Escape Hatch: playwright.waitForFunction("() => [...document.querySelectorAll('vaadin-message')]"+ ".some(m => /\\(stopped\\)/.test(m.textContent || ''))")
        playwright.waitForFunction(
                "() => [...document.querySelectorAll('vaadin-message')]"
                + ".some(m => /\\(stopped\\)/.test(m.textContent || ''))");
        // TODO unified: Dramafinder Escape Hatch: playwright.evaluate("() => [...document.querySelectorAll('vaadin-message')]"+ ".some(m => (m.textContent || '').includes('what I am working on')"+ " && /\\(stopped\\)/.test(m.textContent || ''))")
        Boolean stoppedKept = (Boolean) playwright.evaluate(
                "() => [...document.querySelectorAll('vaadin-message')]"
                + ".some(m => (m.textContent || '').includes('what I am working on')"
                + " && /\\(stopped\\)/.test(m.textContent || ''))");
        Assertions.assertThat(stoppedKept)
                .as("partial reply is kept and marked (stopped)").isTrue();

        // "stop" itself must never have been sent to the model.
        Assertions.assertThat(chatModel.receivedPrompts().size())
                .as("\"stop\" must not be sent to the model").isEqualTo(promptsBeforeStop);
    }
}
