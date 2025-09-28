package org.example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

@Component
public class YogaBot extends TelegramWebhookBot {

    @Value("${bot.username}")
    private String botUsername;

    @Value("${bot.token}")
    private String botToken;

    @Value("${BOT_PATH:/}")
    private String botPath;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${app.channelId}")
    private String channelId;

    @Value("${app.adminId}")
    private String adminId;

    // Подписки в памяти
    private final Map<Long, Boolean> subscriptions = new HashMap<>();

    // Инициализация БД после того, как Spring установил поля
    @PostConstruct
    public void postConstruct() {
        initDb();
    }

    @Override
    public String getBotUsername() { return botUsername; }

    @Override
    public String getBotToken() { return botToken; }

    @Override
    public String getBotPath() { return botPath; }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText();
            Long userId = update.getMessage().getFrom().getId();

            switch (text) {
                case "/start" -> sendMsg(chatId, "Привет! Я YogaBot 🧘");
                case "🔔 Уведомления" -> toggleSubscription(chatId, userId);
                case "📖 Расписание" -> showSchedule(chatId);
                default -> sendMsg(chatId, "Команда не распознана");
            }
        }
        return null; // null, если не нужно отправлять объект BotApiMethod
    }

    /** Отправка сообщения */
    private void sendMsg(Long chatId, String text) {
        SendMessage message = new SendMessage(chatId.toString(), text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    /** Инициализация базы данных */
    private void initDb() {
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            Statement st = conn.createStatement();
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS lessons (
                    id BIGSERIAL PRIMARY KEY,
                    datetime TIMESTAMP NOT NULL,
                    title TEXT NOT NULL
                )
            """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS signups (
                    id BIGSERIAL PRIMARY KEY,
                    lesson_id BIGINT REFERENCES lessons(id) ON DELETE CASCADE,
                    username TEXT NOT NULL
                )
            """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS subscriptions (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT UNIQUE NOT NULL
                )
            """);
            System.out.println("✅ База данных инициализирована.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Подписка / отписка */
    private void toggleSubscription(Long chatId, Long userId) {
        boolean subscribed = subscriptions.getOrDefault(userId, false);
        subscriptions.put(userId, !subscribed);
        sendMsg(chatId, subscribed ? "🔕 Уведомления отключены" : "🔔 Уведомления включены");
    }

    /** Показ расписания */
    private void showSchedule(Long chatId) {
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT id, datetime, title FROM lessons ORDER BY datetime");
            StringBuilder sb = new StringBuilder("📖 Расписание:\n");
            while (rs.next()) {
                sb.append("• ")
                        .append(rs.getTimestamp("datetime").toLocalDateTime())
                        .append(" — ")
                        .append(rs.getString("title"))
                        .append("\n");
            }
            sendMsg(chatId, sb.toString());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Проверка админа */
    private boolean isAdmin(Long userId) {
        return adminId != null && adminId.equals(userId.toString());
    }
}