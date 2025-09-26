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

    private final String BOT_USERNAME = System.getenv("katysyoga_bot");
    private final String BOT_TOKEN = System.getenv("7970982996:AAFeH9IMDHqyTTmqhshuxdhRibxz7fVP_I0");
    private final String ADMIN_ID = System.getenv("639619404");
    private final String CHANNEL_ID = System.getenv("@yoga_yollayo11");
    private final String DB_URL = System.getenv("postgresql://yogabot_user:NZ8XT9dWuccinu31ke6qcy7KcnwY5cpC@dpg-d3bbrbu3jp1c73atqikg-a/yogabot_db");

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public YogaBot() {
        initDb();
        scheduleDailyReminder();
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

    /** Планировщик: отправка уведомления каждый день в 14:00 (МСК) */
    private void scheduleDailyReminder() {
        Runnable task = () -> {
            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                PreparedStatement ps = conn.prepareStatement("""
                    SELECT id, datetime, title FROM lessons
                    WHERE datetime::date = (CURRENT_DATE + INTERVAL '1 day')
                """);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int lessonId = rs.getInt("id");
                    Timestamp dt = rs.getTimestamp("datetime");
                    String title = rs.getString("title");

                    String text = "📅 Завтра в " +
                            dt.toLocalDateTime().toLocalTime() +
                            " — " + title;

                    // inline-кнопка "Записаться"
                    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                    List<InlineKeyboardButton> row = new ArrayList<>();
                    InlineKeyboardButton signupBtn = new InlineKeyboardButton();
                    signupBtn.setText("Записаться");
                    signupBtn.setCallbackData("signup_" + lessonId);
                    row.add(signupBtn);
                    markup.setKeyboard(List.of(row));

                    SendMessage msg = new SendMessage(CHANNEL_ID, text);
                    msg.setReplyMarkup(markup);
                    execute(msg);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        // вычисляем время до 14:00 по МСК
        ZoneId moscow = ZoneId.of("Europe/Moscow");
        LocalDateTime now = LocalDateTime.now(moscow);
        LocalDateTime next14 = now.withHour(14).withMinute(0).withSecond(0);
        if (now.isAfter(next14)) {
            next14 = next14.plusDays(1);
        }
        long initialDelay = Duration.between(now, next14).toSeconds();
        long oneDay = 24 * 60 * 60;

        scheduler.scheduleAtFixedRate(task, initialDelay, oneDay, TimeUnit.SECONDS);
    }

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

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
                        PreparedStatement ps = conn.prepareStatement("""
                            INSERT INTO signups (lesson_id, username)
                            VALUES (?, ?)
                        """);
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

    /** Главное меню */
    private void sendMainMenu(String chatId) throws TelegramApiException {
        SendMessage msg = new SendMessage(chatId,
                "Выберите действие:");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(new InlineKeyboardButton("🔔 Уведомления").setCallbackData("menu_notify")));
        rows.add(List.of(new InlineKeyboardButton("📖 Расписание").setCallbackData("menu_schedule")));
        rows.add(List.of(new InlineKeyboardButton("👥 Записавшиеся").setCallbackData("menu_signups")));
        rows.add(List.of(new InlineKeyboardButton("✏️ Изменить занятие").setCallbackData("menu_edit")));
        rows.add(List.of(new InlineKeyboardButton("❌ Отменить занятие").setCallbackData("menu_cancel")));

        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        execute(msg);
    }

    private void toggleSubscription(String chatId, Long userId) throws TelegramApiException {
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

    private void showSchedule(String chatId) throws TelegramApiException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
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

    private void showSignups(String chatId) throws TelegramApiException {
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
        return ADMIN_ID != null && ADMIN_ID.equals(userId.toString());
    }

    private void sendMsg(String chatId, String text) throws TelegramApiException {
        execute(new SendMessage(chatId, text));
    }
}