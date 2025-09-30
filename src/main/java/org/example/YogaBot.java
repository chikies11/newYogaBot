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

    @Value("${bot.path:/}")
    private String botPath;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${app.channelId}")
    private String channelId;

    @Value("${app.adminId}")
    private String adminId;

    private final Map<Long, Boolean> subscriptions = new HashMap<>();

    @PostConstruct
    public void postConstruct() {
        System.out.println("🔄 Инициализация YogaBot...");
        System.out.println("Database URL: " + dbUrl);
        System.out.println("Bot Username: " + botUsername);
        initDb();
        System.out.println("✅ YogaBot инициализирован");
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public String getBotPath() {
        return botPath;
    }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        System.out.println("📨 Получено обновление: " + update.getUpdateId());

        if (update.hasMessage() && update.getMessage().hasText()) {
            Long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText();
            Long userId = update.getMessage().getFrom().getId();

            System.out.println("💬 Сообщение от " + userId + ": " + text);

            switch (text) {
                case "/start" -> {
                    sendMsg(chatId, "Привет! Я YogaBot 🧘\n\nДоступные команды:\n📖 Расписание - показать расписание занятий\n🔔 Уведомления - включить/выключить уведомления");
                }
                case "🔔 Уведомления", "/notifications" -> toggleSubscription(chatId, userId);
                case "📖 Расписание", "/schedule" -> showSchedule(chatId);
                default -> sendMsg(chatId, "Команда не распознана. Используйте:\n/start - начать работу\n/schedule - расписание\n/notifications - уведомления");
            }
        }
        return null;
    }

    private void sendMsg(Long chatId, String text) {
        SendMessage message = new SendMessage(chatId.toString(), text);
        try {
            execute(message);
            System.out.println("✅ Отправлено сообщение в чат " + chatId);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка отправки сообщения: " + e.getMessage());
        }
    }

    private void initDb() {
        System.out.println("🔄 Инициализация базы данных...");
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            Statement st = conn.createStatement();

            // Таблица занятий
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS lessons (
                    id BIGSERIAL PRIMARY KEY,
                    datetime TIMESTAMP NOT NULL,
                    title TEXT NOT NULL
                )
            """);

            // Таблица записей
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS signups (
                    id BIGSERIAL PRIMARY KEY,
                    lesson_id BIGINT REFERENCES lessons(id) ON DELETE CASCADE,
                    username TEXT NOT NULL,
                    user_id BIGINT NOT NULL
                )
            """);

            // Таблица подписок
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS subscriptions (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT UNIQUE NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Добавляем тестовые данные, если таблица пуста
            ResultSet rs = st.executeQuery("SELECT COUNT(*) as count FROM lessons");
            rs.next();
            if (rs.getInt("count") == 0) {
                st.executeUpdate("""
                    INSERT INTO lessons (datetime, title) VALUES 
                    (CURRENT_TIMESTAMP + INTERVAL '1 day', 'Утренняя йога'),
                    (CURRENT_TIMESTAMP + INTERVAL '2 days', 'Вечерняя медитация'),
                    (CURRENT_TIMESTAMP + INTERVAL '3 days', 'Хатха йога')
                """);
                System.out.println("✅ Добавлены тестовые занятия");
            }

            System.out.println("✅ База данных инициализирована");
        } catch (SQLException e) {
            System.err.println("❌ Ошибка инициализации БД: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void toggleSubscription(Long chatId, Long userId) {
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            // Проверяем, подписан ли уже пользователь
            PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT id FROM subscriptions WHERE user_id = ?"
            );
            checkStmt.setLong(1, userId);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                // Отписываем
                PreparedStatement deleteStmt = conn.prepareStatement(
                        "DELETE FROM subscriptions WHERE user_id = ?"
                );
                deleteStmt.setLong(1, userId);
                deleteStmt.executeUpdate();
                sendMsg(chatId, "🔕 Уведомления отключены");
                System.out.println("✅ Пользователь " + userId + " отписался");
            } else {
                // Подписываем
                PreparedStatement insertStmt = conn.prepareStatement(
                        "INSERT INTO subscriptions (user_id) VALUES (?)"
                );
                insertStmt.setLong(1, userId);
                insertStmt.executeUpdate();
                sendMsg(chatId, "🔔 Уведомления включены");
                System.out.println("✅ Пользователь " + userId + " подписался");
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка переключения подписки: " + e.getMessage());
            sendMsg(chatId, "❌ Произошла ошибка при изменении подписки");
        }
    }

    private void showSchedule(Long chatId) {
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("""
                SELECT id, datetime, title 
                FROM lessons 
                WHERE datetime > CURRENT_TIMESTAMP 
                ORDER BY datetime 
                LIMIT 10
            """);

            StringBuilder sb = new StringBuilder("📖 Ближайшие занятия:\n\n");
            int count = 0;

            while (rs.next()) {
                count++;
                sb.append(count)
                        .append(". ")
                        .append(rs.getTimestamp("datetime").toLocalDateTime())
                        .append(" — ")
                        .append(rs.getString("title"))
                        .append("\n");
            }

            if (count == 0) {
                sb.append("На данный момент нет запланированных занятий.");
            }

            sendMsg(chatId, sb.toString());
            System.out.println("✅ Показано расписание для чата " + chatId);

        } catch (SQLException e) {
            System.err.println("❌ Ошибка показа расписания: " + e.getMessage());
            sendMsg(chatId, "❌ Произошла ошибка при загрузке расписания");
        }
    }

    private boolean isAdmin(Long userId) {
        return adminId != null && adminId.equals(userId.toString());
    }
}