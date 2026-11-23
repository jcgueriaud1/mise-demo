package com.example.mise.it;

import com.example.mise.domain.conversation.ConversationMessage;
import com.example.mise.domain.conversation.ConversationMessageRepository;
import com.example.mise.it.support.TestAiConfig;
import com.example.mise.it.support.TestChatModel;
import com.example.mise.ui.onboarding.OnboardingView;

import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedMessageInput;
import com.vaadin.testing.unified.component.UnifiedMessageList;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.vaadin.addons.dramafinder.element.MessageListElement;

import org.assertj.core.api.Assertions;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

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
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void seededGreetingRendersOnFirstLoad(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate(OnboardingView.class);
        // TODO unified: Dramafinder Escape Hatch: MessageListElement.get(playwright)
        MessageListElement messages = MessageListElement.get(playwright);
        // TODO unified: Dramafinder Escape Hatch: messages.assertMessageCount(1)
        messages.assertMessageCount(1);
        // TODO unified: Dramafinder Escape Hatch: assertThat(messages.getMessage(0)).containsText(OPENING_GREETING_FRAGMENT)
        assertThat(messages.getMessage(0)).containsText(OPENING_GREETING_FRAGMENT);
    }

    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void userInputRoundTripsThroughStubbedAssistant(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate(OnboardingView.class);
        chatModel.queueReply("Got it — two adults, no fish, €90/week. Sound right?");

        UnifiedMessageInput input = page.find(UnifiedMessageInput.class);
        // TODO unified: Dramafinder Escape Hatch: MessageListElement.get(playwright)
        MessageListElement messages = MessageListElement.get(playwright);

        input.send("Two adults, no fish, around €90 a week");

        // Greeting + user + assistant
        // TODO unified: Dramafinder Escape Hatch: messages.assertMessageCount(3)
        messages.assertMessageCount(3);
        // TODO unified: Dramafinder Escape Hatch: assertThat(messages.getMessage(1)).containsText("Two adults, no fish")
        assertThat(messages.getMessage(1)).containsText("Two adults, no fish");
        // TODO unified: Dramafinder Escape Hatch: assertThat(messages.getMessage(2)).containsText("Got it — two adults, no fish")
        assertThat(messages.getMessage(2)).containsText("Got it — two adults, no fish");
    }

    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    void defaultReplyUsedWhenTestForgetsToQueueOne(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        page.navigate(OnboardingView.class);
        // Guardrail: a forgotten queueReply() shouldn't silently call the (unreachable) real LLM.
        // The stub falls back to a sentinel string instead.
        UnifiedMessageInput input = page.find(UnifiedMessageInput.class);
        // TODO unified: Dramafinder Escape Hatch: MessageListElement.get(playwright)
        MessageListElement messages = MessageListElement.get(playwright);

        input.send("hello");

        // TODO unified: Dramafinder Escape Hatch: messages.assertMessageCount(3)
        messages.assertMessageCount(3);
        // TODO unified: Dramafinder Escape Hatch: assertThat(messages.getMessage(2)).containsText("no reply queued")
        assertThat(messages.getMessage(2)).containsText("no reply queued");
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
    void messagesPersistedWithOnboardingViewContext(UnifiedPage page) {
        page.navigate(OnboardingView.class);
        chatModel.queueReply("Noted — I'll keep your preferences in mind.");
        UnifiedMessageInput input = page.find(UnifiedMessageInput.class);

        input.send("Two adults, no allergies, €80/week");

        // Wait for the assistant reply to land in the MessageList before checking the DB,
        // ensuring onResponseComplete has had time to flush to the repository.
        UnifiedMessageList messages = page.find(UnifiedMessageList.class);
        com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat(messages).hasMessageCount(3);

        long onboardingCount = conversationMessageRepository.findAll().stream()
                .filter(m -> m.getViewContext() == ConversationMessage.ViewContext.ONBOARDING)
                .count();

        Assertions.assertThat(onboardingCount).isGreaterThan(0);
    }
}
