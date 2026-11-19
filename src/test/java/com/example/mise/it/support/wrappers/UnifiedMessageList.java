package com.example.mise.it.support.wrappers;

import java.util.List;

import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.ToolImplementation;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;

/**
 * Wrapper for {@link MessageList}, the chat history. unified-test-api 0.1.0-alpha3 ships no Wrapper for it
 * (see docs/unified-testing-alpha3/findings.md, F7). Reads only: use it with {@code page.waitUntil(..)} or
 * AssertJ after a retrying check.
 */
@UnifiedComponent(value = MessageList.class, kind = "message list",
        overrides = @ToolImplementation(tool = TestingTool.BROWSERLESS, value = UnifiedMessageListBrowserless.class))
public interface UnifiedMessageList extends UnifiedWrapper, CommonFilters<UnifiedMessageList> {

    /** The text of each message, oldest first. */
    default List<String> getMessageTexts() {
        return element().findChildren("vaadin-message").stream().map(UnifiedElement::getText).map(String::strip).toList();
    }

    default int getMessageCount() {
        return getMessageTexts().size();
    }

    default String getMessageText(int index) {
        return getMessageTexts().get(index);
    }
}
