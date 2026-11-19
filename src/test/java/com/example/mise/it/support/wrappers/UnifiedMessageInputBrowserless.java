package com.example.mise.it.support.wrappers;

import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageInputTester;

/** Browserless: there is no client-side input to type into; the tester fires the submit event. */
public interface UnifiedMessageInputBrowserless extends UnifiedMessageInput {

    @Override
    default void send(String message) {
        new MessageInputTester<>(unwrap(MessageInput.class)).send(message);
    }
}
