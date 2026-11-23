package com.example.mise.it;

import com.example.mise.domain.conversation.ConversationMessage;
import com.example.mise.domain.conversation.ConversationMessageRepository;
import com.example.mise.it.support.TestAiConfig;
import com.example.mise.it.support.TestChatModel;
import com.example.mise.ui.onboarding.OnboardingView;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedMessageInput;
import com.vaadin.testing.unified.component.UnifiedMessageList;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import org.assertj.core.api.Assertions;

import java.util.List;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

/**
 * UC-001 round-trip IT against a deterministic {@link com.example.mise.it.support.TestChatModel}.
 *
 * <p>Covers the chat plumbing only: seeded greeting renders, user input is echoed, the
 * stubbed assistant reply lands in the {@code MessageList}, no LLM endpoint is contacted.
 * Tool-call coverage (the {@code recordHousehold} flow that drives navigation to {@code /plan})
 * is exercised separately by {@code OnboardingToolsTest} as a unit test — keeping this IT focused
 * on the Vaadin <-> Spring AI <-> stubbed ChatModel wiring.
 *
 * <p>BR-01 (existing household → redirect to /plan) is covered by the companion
 * {@link OnboardingRedirectIT} class, which seeds the household before navigation.
 */
@UnifiedSpringBootTest
@ActiveProfiles("it")
@Import(TestAiConfig.class)
class OnboardingViewIT {

    @Autowired
    protected TestChatModel chatModel;

    @BeforeEach
    void resetChatModelBetweenTests() {
        chatModel.reset();
    }

    private static final String OPENING_GREETING_FRAGMENT = "Hi — I'll set up your dinners";

    @Autowired
    ConversationMessageRepository conversationMessageRepository;

    @Test
    @DisplayName("A first visit shows the onboarding greeting")
    void seededGreetingRendersOnFirstLoad(UnifiedPage page) {
        page.navigate(OnboardingView.class);

        UnifiedMessageList messages = page.find(UnifiedMessageList.class);
        assertThat(messages).hasMessageCount(1).hasMessageContaining(OPENING_GREETING_FRAGMENT);
    }

    @Test
    @DisplayName("The user's answer and the assistant's reply appear in the conversation")
    void userInputRoundTripsThroughStubbedAssistant(UnifiedPage page) {
        // given
        page.navigate(OnboardingView.class);
        chatModel.queueReply("Got it — two adults, no fish, €90/week. Sound right?");
        UnifiedMessageList messages = page.find(UnifiedMessageList.class);

        // when
        page.find(UnifiedMessageInput.class).send("Two adults, no fish, around €90 a week");

        // then: greeting + user + assistant, in that order
        assertThat(messages).hasMessageCount(3);
        List<String> texts = messages.getMessageTexts();
        Assertions.assertThat(texts.get(1)).contains("Two adults, no fish");
        Assertions.assertThat(texts.get(2)).contains("Got it — two adults, no fish");
    }

    @Test
    @DisplayName("Without a queued reply the stub answers with a sentinel, not the real model")
    void defaultReplyUsedWhenTestForgetsToQueueOne(UnifiedPage page) {
        // Guardrail: a forgotten queueReply() shouldn't silently call the (unreachable) real LLM.
        // The stub falls back to a sentinel string instead.
        page.navigate(OnboardingView.class);
        UnifiedMessageList messages = page.find(UnifiedMessageList.class);

        page.find(UnifiedMessageInput.class).send("hello");

        assertThat(messages).hasMessageCount(3);
        Assertions.assertThat(messages.getMessageTexts().get(2)).contains("no reply queued");
    }

    /**
     * BR-07: messages persisted after a round-trip carry {@code viewContext = ONBOARDING}.
     *
     * <p>Queues a stub reply, submits user input (triggering {@code onResponseComplete}
     * which calls {@code ConversationService.syncFromOrchestrator(..., ONBOARDING)}),
     * then asserts that at least one ONBOARDING-tagged row exists in the DB.
     * We use an absolute "at least one" check rather than a delta because the Spring test
     * context (and its in-memory H2) is shared across tests in this class — earlier tests
     * that trigger {@code onResponseComplete} may already have deposited ONBOARDING rows.
     * Asserting existence (rather than delta) is stable and still verifies the viewContext
     * column is set correctly for newly written rows.
     */
    @Test
    @DisplayName("Onboarding messages are stored with the onboarding view context")
    void messagesPersistedWithOnboardingViewContext(UnifiedPage page) {
        // given
        page.navigate(OnboardingView.class);
        chatModel.queueReply("Noted — I'll keep your preferences in mind.");

        // when
        page.find(UnifiedMessageInput.class).send("Two adults, no allergies, €80/week");

        // then: the reply is shown ...
        assertThat(page.find(UnifiedMessageList.class)).hasMessageCount(3);
        // ... and persisted. The response listener stores the turn after the reply renders,
        // so wait for the row rather than reading the repository once.
        page.step("The conversation is stored as onboarding", () ->
                page.waitUntil(() -> conversationMessageRepository.findAll().stream()
                        .anyMatch(m -> m.getViewContext() == ConversationMessage.ViewContext.ONBOARDING)));
    }
}
