package com.example.mise.it.support.wrappers;

import java.util.List;

import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;

/** Browserless: the messages are the server-side items; the browser renders them as vaadin-message. */
public interface UnifiedMessageListBrowserless extends UnifiedMessageList {

    @Override
    default List<String> getMessageTexts() {
        return unwrap(MessageList.class).getItems().stream().map(MessageListItem::getText).map(String::strip).toList();
    }
}
