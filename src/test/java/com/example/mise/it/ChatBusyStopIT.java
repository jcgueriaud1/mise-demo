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

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedMessageInput;
import com.vaadin.testing.unified.component.UnifiedMessageList;
import com.vaadin.testing.unified.component.UnifiedSpan;
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

import org.assertj.core.api.Assertions;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

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
    void seed() {
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
    @DisplayName("A prompt sent while a reply is streaming is held back with a busy note")
    void submittingWhileBusy_showsBusyNote_andSuppressesPrompt(UnifiedPage page) {
        // given: a first turn held in flight by the pause latch
        page.navigate(PlanView.class);
        CountDownLatch responseLatch = chatModel.pauseNextResponse();
        chatModel.queueReply("Sure — Thursday is Roasted Vegetable Pasta.");
        UnifiedContainer dock = chatDock(page);
        UnifiedMessageInput input = dock.find(UnifiedMessageInput.class);
        input.send("Swap Thursday for something vegetarian.");
        assertThat(dock.withClassName("ai-working")).exists();

        // when: a second prompt while busy
        input.send("and also add chicken on Friday");

        // then: it is suppressed and surfaced as a busy note ...
        assertThat(busyNote(page)).isVisible().hasTextContaining("still working");
        // ... and not echoed into the conversation
        Assertions.assertThat(dock.find(UnifiedMessageList.class).getMessageTexts())
                .as("the ignored second prompt must not be echoed into the conversation")
                .noneMatch(text -> text.contains("add chicken on Friday"));

        // when: the first turn finishes, the note clears
        responseLatch.countDown();
        assertThat(dock.withClassName("ai-working")).doesNotExist();
        assertThat(busyNote(page)).isHidden();
    }

    /**
     * BR-04: "stop" with nothing running is a no-op — a brief note appears and the model is
     * never called.
     */
    @Test
    @DisplayName("Stop with nothing running says so and never calls the model")
    void stopWhileIdle_showsNothingRunning_andNeverCallsModel(UnifiedPage page) {
        page.navigate(PlanView.class);

        chatDock(page).find(UnifiedMessageInput.class).send("stop");

        assertThat(busyNote(page)).isVisible().hasTextContaining("Nothing is running");
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
    @DisplayName("Stop while a reply streams cancels it and keeps the partial reply, marked stopped")
    void stopWhileBusy_cancelsTurn_keepsPartialWithStoppedMarker(UnifiedPage page) {
        // given: a reply that streams one chunk and never completes on its own
        page.navigate(PlanView.class);
        chatModel.holdNextResponseOpen("Here is what I am working on");
        UnifiedContainer dock = chatDock(page);
        UnifiedMessageInput input = dock.find(UnifiedMessageInput.class);
        UnifiedMessageList messages = dock.find(UnifiedMessageList.class);
        input.send("Write me a very long detailed plan for the whole week.");
        assertThat(dock.withClassName("ai-working")).exists();
        assertThat(messages).hasMessageContaining("what I am working on");
        int promptsBeforeStop = chatModel.receivedPrompts().size();

        // when: cancel. The turn can only end via this cancel — not via a natural
        // completion or the 10s stub timeout.
        input.send("  STOP ");

        // then: .ai-working clears because the stream was cancelled ...
        assertThat(dock.withClassName("ai-working")).doesNotExist();
        // ... and the partial reply survives, marked (stopped)
        assertThat(messages).hasMessageContaining("(stopped)");
        Assertions.assertThat(messages.getMessageTexts())
                .as("partial reply is kept and marked (stopped)")
                .anyMatch(text -> text.contains("what I am working on") && text.contains("(stopped)"));

        // "stop" itself must never have been sent to the model.
        Assertions.assertThat(chatModel.receivedPrompts().size())
                .as("\"stop\" must not be sent to the model").isEqualTo(promptsBeforeStop);
    }

    private static UnifiedContainer chatDock(UnifiedPage page) {
        return page.find(UnifiedContainer.class).withTestId("chat-dock");
    }

    private static UnifiedSpan busyNote(UnifiedPage page) {
        return page.find(UnifiedSpan.class).withTestId("chat-busy-note");
    }
}
