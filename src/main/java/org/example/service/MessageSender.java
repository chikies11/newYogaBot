package org.example.service;

import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public interface MessageSender {
    boolean executeDeleteMessage(DeleteMessage deleteMessage) throws TelegramApiException;
}