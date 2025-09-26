package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public class YogaBot extends TelegramLongPollingBot {

    private final String BOT_USERNAME;
    private final String BOT_TOKEN;
    private final String ADMIN_ID;
    private final String CHANNEL_ID;
    private final String DB_URL;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public YogaBot() {
        BOT_USERNAME = requireEnv("BOT_USERNAME");
        BOT_TOKEN = requireEnv("BOT_TOKEN");
        ADMIN_ID = requireEnv("ADMIN_ID");
        CHANNEL_ID = requireEnv("CHANNEL_ID");
        DB_URL = requireEnv("DATABASE_URL");

        initDb();
        scheduleDailyReminder();
    }

    /** Проверка и получение переменной окружения */
    private static String requireEnv(String name) {
        String val = System.getenv(name);
        if (val == null || val.isEmpty()) throw new IllegalStateException("Environment variable " + name + " is not set");
        return val;
    }

    /** Создание таблиц, если их нет */
    private void initDb() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            Statement st = conn.createStatement();
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS lessons (
                    id SERIAL PRIMARY KEY,
                    datetime TIMESTAMP NOT NULL,
                    title TEXT NOT NULL
                )
            """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS signups (
                    id SERIAL PRIMARY KEY,
                    lesson_id INT REFERENCES lessons(id) ON DELETE CASCADE,
                    username TEXT NOT NULL
                )
            """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS subscriptions (
                    id SERIAL PRIMARY KEY,
                    user_id BIGINT UNIQUE NOT NULL
                )
            """);
            System.out.println("✅ База данных инициализирована.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Планировщик: уведомление каждый день в 14:00 (МСК) */
    private void scheduleDailyReminder() {
        Runnable task = () -> {
            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                PreparedStatement ps = conn.prepareStatement("""
                    SELECT id, datetime, title FROM lessons
                    WHERE datetime >= CURRENT_DATE + 1
                      AND datetime < CURRENT_DATE + 2
                """);
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    int lessonId = rs.getInt("id");
                    Timestamp dt = rs.getTimestamp("datetime");
                    String title = rs.getString("title");

                    String text = "📅 Завтра в " +
                            dt.toLocalDateTime().toLocalTime() +
                            " — " + title;

                    InlineKeyboardButton btn = new InlineKeyboardButton();
                    btn.setText("Записаться");
                    btn.setCallbackData("signup_" + lessonId);

                    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                    markup.setKeyboard(List.of(List.of(btn)));

                    sendMsg(CHANNEL_ID, text, markup);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        ZoneId moscow = ZoneId.of("Europe/Moscow");
        LocalDateTime now = LocalDateTime.now(moscow);
        LocalDateTime next14 = now.withHour(14).withMinute(0).withSecond(0).withNano(0);
        if (now.isAfter(next14)) next14 = next14.plusDays(1);

        long initialDelay = Duration.between(now, next14).toSeconds();
        long oneDay = 24 * 60 * 60;

        scheduler.scheduleAtFixedRate(task, initialDelay, oneDay, TimeUnit.SECONDS);
    }

    @Override
    public String getBotUsername() { return BOT_USERNAME; }

    @Override
    public String getBotToken() { return BOT_TOKEN; }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                String chatId = update.getMessage().getChatId().toString();
                String text = update.getMessage().getText();
                Long userId = update.getMessage().getFrom().getId();

                switch (text) {
                    case "/start" -> {
                        sendMsg(chatId, "Привет! Я YogaBot 🧘");
                        sendMainMenu(chatId);
                    }
                    case "🔔 Уведомления" -> toggleSubscription(chatId, userId);
                    case "📖 Расписание" -> showSchedule(chatId);
                    case "✏️ Изменить занятие" -> {
                        if (isAdmin(userId)) sendMsg(chatId, "Здесь будет меню изменения.");
                        else sendMsg(chatId, "⛔ Только админ может изменять занятия.");
                    }
                    case "❌ Отменить занятие" -> {
                        if (isAdmin(userId)) sendMsg(chatId, "Здесь будет выбор занятия для отмены.");
                        else sendMsg(chatId, "⛔ Только админ может отменять занятия.");
                    }
                    case "👥 Записавшиеся" -> showSignups(chatId);
                }
            }

            if (update.hasCallbackQuery()) {
                String data = update.getCallbackQuery().getData();
                String user = update.getCallbackQuery().getFrom().getUserName();
                Long userId = update.getCallbackQuery().getFrom().getId();
                String chatId = update.getCallbackQuery().getMessage().getChatId().toString();

                if (data.startsWith("signup_")) {
                    int lessonId = Integer.parseInt(data.split("_")[1]);
                    try (Connection conn = DriverManager.getConnection(DB_URL)) {
                        PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO signups (lesson_id, username) VALUES (?, ?)"
                        );
                        ps.setInt(1, lessonId);
                        ps.setString(2, user != null ? user : userId.toString());
                        ps.executeUpdate();
                        sendMsg(chatId, "✅ Вы записаны на занятие!");
                    } catch (SQLException e) {
                        sendMsg(chatId, "⚠️ Вы уже записаны или произошла ошибка.");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMainMenu(String chatId) {
        SendMessage msg = new SendMessage(chatId, "Выберите действие:");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(button("🔔 Уведомления", "menu_notify")));
        rows.add(List.of(button("📖 Расписание", "menu_schedule")));
        rows.add(List.of(button("👥 Записавшиеся", "menu_signups")));
        rows.add(List.of(button("✏️ Изменить занятие", "menu_edit")));
        rows.add(List.of(button("❌ Отменить занятие", "menu_cancel")));

        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);

        sendMsg(msg);
    }

    private InlineKeyboardButton button(String text, String callback) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callback);
        return btn;
    }

    private void toggleSubscription(String chatId, Long userId) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement check = conn.prepareStatement("SELECT id FROM subscriptions WHERE user_id=?");
            check.setLong(1, userId);
            ResultSet rs = check.executeQuery();
            if (rs.next()) {
                PreparedStatement del = conn.prepareStatement("DELETE FROM subscriptions WHERE user_id=?");
                del.setLong(1, userId);
                del.executeUpdate();
                sendMsg(chatId, "🔕 Уведомления отключены");
            } else {
                PreparedStatement ins = conn.prepareStatement("INSERT INTO subscriptions (user_id) VALUES (?)");
                ins.setLong(1, userId);
                ins.executeUpdate();
                sendMsg(chatId, "🔔 Уведомления включены");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showSchedule(String chatId) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT datetime, title FROM lessons ORDER BY datetime");
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

    private void showSignups(String chatId) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("""
                SELECT lessons.title, signups.username
                FROM signups
                JOIN lessons ON lessons.id = signups.lesson_id
                ORDER BY lessons.datetime
            """);
            StringBuilder sb = new StringBuilder("👥 Записавшиеся:\n");
            while (rs.next()) {
                sb.append("• ").append(rs.getString("title"))
                        .append(" — @").append(rs.getString("username")).append("\n");
            }
            sendMsg(chatId, sb.toString());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean isAdmin(Long userId) {
        return ADMIN_ID.equals(userId.toString());
    }

    /** Унифицированная отправка сообщения без риска execute(void) */
    private void sendMsg(String chatId, String text) {
        sendMsg(new SendMessage(chatId, text));
    }

    private void sendMsg(String chatId, String text, InlineKeyboardMarkup markup) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);       // обязательно String
        msg.setText(text);           // обязательно String
        msg.setReplyMarkup(markup);  // если markup == null, всё ок

        sendMsg(msg);                // вызываем твою обёртку, которая execute()
    }


    private void sendMsg(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}