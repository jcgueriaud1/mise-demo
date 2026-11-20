package com.example.mise.it.support.wrappers;

import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.ToolImplementation;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.UserStepVerb;
import com.vaadin.testing.unified.query.CommonFilters;

/**
 * Wrapper for {@link MessageInput}, the chat input of the dock and the onboarding view.
 * unified-test-api 0.1.0-alpha3 ships no Wrapper for it (see docs/unified-testing-alpha3/findings.md, F7).
 */
@UnifiedComponent(value = MessageInput.class, kind = "message input",
        overrides = @ToolImplementation(tool = TestingTool.BROWSERLESS, value = UnifiedMessageInputBrowserless.class))
public interface UnifiedMessageInput extends UnifiedWrapper, CommonFilters<UnifiedMessageInput> {

    /** Types the message and presses the send button, as a user does. */
    @UserStepVerb("send {value} with")
    default void send(String message) {
        element().setValueAsUser(message);
        element().findDescendants("vaadin-message-input-button").getFirst().click();
    }

    /**
     * Puts the focus in the input (the dock expands on :focus-within). Focuses the slotted native
     * {@code <textarea>}: neither the {@code vaadin-message-input} host nor its {@code vaadin-text-area} takes
     * focus from a generic element, so focusing them leaves :focus-within unmatched.
     */
    default void focus() {
        element().findDescendants("textarea").getFirst().focus();
    }
}
