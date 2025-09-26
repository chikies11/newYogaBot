package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        try {
            // Инициализация API
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

            // Регистрируем бота
            YogaBot bot = new YogaBot();
            botsApi.registerBot(bot);

            System.out.println("✅ YogaBot успешно запущен!");

        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}