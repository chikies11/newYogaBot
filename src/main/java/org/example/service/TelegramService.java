package org.example.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Service
public class TelegramService {

    @Value("${app.channelId:}")
    private String channelId;

    private final TelegramWebhookBot telegramBot;

    public TelegramService(TelegramWebhookBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    public boolean deleteMessageFromChannel(Integer messageId) {
        try {
            DeleteMessage deleteMessage = new DeleteMessage(channelId, messageId);
            boolean result = telegramBot.execute(deleteMessage);

            if (result) {
                System.out.println("✅ Сообщение " + messageId + " удалено из канала");
            } else {
                System.out.println("⚠️ Не удалось удалить сообщение " + messageId + " из канала");
            }

            return result;
        } catch (TelegramApiException e) {
            if (e.getMessage().contains("message to delete not found")) {
                System.out.println("ℹ️ Сообщение " + messageId + " уже удалено из канала");
                return true;
            }
            System.err.println("❌ Ошибка удаления сообщения " + messageId + ": " + e.getMessage());
            return false;
        }
    }
}