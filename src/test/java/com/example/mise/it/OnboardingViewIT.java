package com.example.mise.it;

import org.junit.jupiter.api.BeforeEach;
import com.example.mise.it.support.TestChatModel;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import com.example.mise.it.support.TestAiConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.example.mise.domain.conversation.ConversationMessage;
import com.example.mise.domain.conversation.ConversationMessageRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.example.mise.it.support.wrappers.UnifiedMessageInput;
import com.example.mise.it.support.wrappers.UnifiedMessageList;
import org.springframework.beans.factory.annotation.Autowired;

import org.assertj.core.api.Assertions;



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
@ActiveProfiles("it")
@Import(TestAiConfig.class)
@UnifiedSpringBootTest
class OnboardingViewIT {

    @Autowired
    TestChatModel chatModel;

    @BeforeEach
    void resetChatModelBetweenTests() {
        chatModel.reset();
    }

    private static final String OPENING_GREETING_FRAGMENT = "Hi — I'll set up your dinners";

    @Autowired
    ConversationMessageRepository conversationMessageRepository;

    @Test
    @DisplayName("A new user is greeted by the assistant on the welcome page")
    void seededGreetingRendersOnFirstLoad(UnifiedPage page) {
        // when
        page.navigate("/welcome");

        // then
        UnifiedMessageList messages = page.find(UnifiedMessageList.class);
        page.waitUntil(() -> messages.getMessageCount() == 1);
        Assertions.assertThat(messages.getMessageText(0)).contains(OPENING_GREETING_FRAGMENT);
    }

    @Test
    @DisplayName("The user's answer and the assistant's reply appear in the onboarding chat")
    void userInputRoundTripsThroughStubbedAssistant(UnifiedPage page) {
        // given
        page.navigate("/welcome");
        chatModel.queueReply("Got it — two adults, no fish, €90/week. Sound right?");
        UnifiedMessageList messages = page.find(UnifiedMessageList.class);

        // when
        page.find(UnifiedMessageInput.class).send("Two adults, no fish, around €90 a week");

        // then: greeting + user + assistant
        page.waitUntil(() -> messages.getMessageCount() == 3);
        Assertions.assertThat(messages.getMessageText(1)).contains("Two adults, no fish");
        Assertions.assertThat(messages.getMessageText(2)).contains("Got it — two adults, no fish");
    }

    @Test
    @DisplayName("Without a queued reply the stub answers with a sentinel instead of calling the real LLM")
    void defaultReplyUsedWhenTestForgetsToQueueOne(UnifiedPage page) {
        // given: no queueReply(), a guardrail so a forgotten one can't call the (unreachable) real LLM
        page.navigate("/welcome");
        UnifiedMessageList messages = page.find(UnifiedMessageList.class);

        // when
        page.find(UnifiedMessageInput.class).send("hello");

        // then
        page.waitUntil(() -> messages.getMessageCount() == 3);
        Assertions.assertThat(messages.getMessageText(2)).contains("no reply queued");
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
     */
    @Test
    @DisplayName("The onboarding conversation is saved as an ONBOARDING conversation")
    void messagesPersistedWithOnboardingViewContext(UnifiedPage page) {
        // given
        page.navigate("/welcome");
        chatModel.queueReply("Noted — I'll keep your preferences in mind.");
        UnifiedMessageList messages = page.find(UnifiedMessageList.class);

        // when
        page.find(UnifiedMessageInput.class).send("Two adults, no allergies, €80/week");
        // the assistant reply has landed, so onResponseComplete has flushed to the repository
        page.waitUntil(() -> messages.getMessageCount() == 3);

        // then
        long onboardingCount = conversationMessageRepository.findAll().stream()
                .filter(m -> m.getViewContext() == ConversationMessage.ViewContext.ONBOARDING)
                .count();
        Assertions.assertThat(onboardingCount).isGreaterThan(0);
    }
}
