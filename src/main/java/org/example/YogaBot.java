package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public class YogaBot extends TelegramLongPollingBot {

    private final String BOT_TOKEN = System.getenv("7970982996:AAFeH9IMDHqyTTmqhshuxdhRibxz7fVP_I0");
    private final String BOT_USERNAME = System.getenv("katysyoga_bot");
    private final String ADMIN_ID = System.getenv("639619404"); // твой userId
    private final String CHANNEL_ID = System.getenv("@yoga_yollayo11"); // @username канала
    private final String DB_URL = System.getenv("DATABASE_URL=postgres://user:27Kirill2727Kirill27@postgres/dbname");

    private boolean reminderEnabled = false;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public YogaBot() {
        initDb();
        startDailyReminder();
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
                String userId = update.getMessage().getFrom().getId().toString();

                if (text.equals("/start")) {
                    if (userId.equals(ADMIN_ID)) sendAdminMenu(chatId);
                    else sendUserMenu(chatId);
                } else if (userId.equals(ADMIN_ID)) {
                    if (text.startsWith("Добавить ")) {
                        String lesson = text.replace("Добавить ", "").trim();
                        addLesson(lesson, "");
                        sendText(chatId, "✅ Занятие добавлено: " + lesson);
                    } else if (text.startsWith("Отменить ")) {
                        String lesson = text.replace("Отменить ", "").trim();
                        cancelLesson(lesson);
                        sendText(chatId, "❌ Занятие отменено: " + lesson);
                    }
                }
            } else if (update.hasCallbackQuery()) {
                String chatId = update.getCallbackQuery().getMessage().getChatId().toString();
                String data = update.getCallbackQuery().getData();
                String userId = update.getCallbackQuery().getFrom().getId().toString();
                String username = update.getCallbackQuery().getFrom().getFirstName();

                if (data.startsWith("signup_")) {
                    int lessonId = Integer.parseInt(data.replace("signup_", ""));
                    addSignup(lessonId, username);
                    sendText(chatId, "✅ " + username + " записан(а).");
                }

                if (userId.equals(ADMIN_ID)) {
                    switch (data) {
                        case "toggle_reminder":
                            reminderEnabled = !reminderEnabled;
                            sendText(chatId, reminderEnabled ? "✅ Отбивка включена" : "❌ Отбивка выключена");
                            break;
                        case "view_schedule":
                            sendText(chatId, getLessons());
                            break;
                        case "view_signups":
                            sendText(chatId, getAllSignups());
                            break;
                    }
                }
                execute(new AnswerCallbackQuery(update.getCallbackQuery().getId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================= UI ==================

    private void sendUserMenu(String chatId) throws TelegramApiException {
        List<String[]> lessons = fetchLessons();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (String[] lesson : lessons) {
            String id = lesson[0];
            String datetime = lesson[1];
            rows.add(Collections.singletonList(InlineKeyboardButton.builder()
                    .text("🧘 Записаться: " + datetime)
                    .callbackData("signup_" + id)
                    .build()));
        }
        markup.setKeyboard(rows);

        SendMessage msg = new SendMessage(chatId, "📅 Расписание занятий:");
        msg.setReplyMarkup(markup);
        execute(msg);
    }

    private void sendAdminMenu(String chatId) throws TelegramApiException {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(Collections.singletonList(InlineKeyboardButton.builder()
                .text("🔔 Вкл/Выкл отбивку").callbackData("toggle_reminder").build()));
        rows.add(Collections.singletonList(InlineKeyboardButton.builder()
                .text("📅 Расписание").callbackData("view_schedule").build()));
        rows.add(Collections.singletonList(InlineKeyboardButton.builder()
                .text("👥 Записавшиеся").callbackData("view_signups").build()));

        markup.setKeyboard(rows);

        SendMessage msg = new SendMessage(chatId, "⚙️ Админская панель:\n" +
                "Чтобы добавить занятие: `Добавить Пн 19:00`\n" +
                "Чтобы отменить: `Отменить Пн 19:00`");
        msg.setReplyMarkup(markup);
        execute(msg);
    }

    private void sendText(String chatId, String text) throws TelegramApiException {
        execute(new SendMessage(chatId, text));
    }

    // ================= DB ==================

    private void initDb() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.createStatement().execute("CREATE TABLE IF NOT EXISTS lessons (" +
                    "id SERIAL PRIMARY KEY, datetime TEXT, title TEXT)");
            conn.createStatement().execute("CREATE TABLE IF NOT EXISTS signups (" +
                    "id SERIAL PRIMARY KEY, lesson_id INT REFERENCES lessons(id) ON DELETE CASCADE, username TEXT)");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void addLesson(String datetime, String title) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("INSERT INTO lessons (datetime, title) VALUES (?, ?)");
            ps.setString(1, datetime);
            ps.setString(2, title);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void cancelLesson(String datetime) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("DELETE FROM lessons WHERE datetime = ?");
            ps.setString(1, datetime);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void addSignup(int lessonId, String username) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO signups (lesson_id, username) VALUES (?, ?) ON CONFLICT DO NOTHING");
            ps.setInt(1, lessonId);
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private List<String[]> fetchLessons() {
        List<String[]> lessons = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            ResultSet rs = conn.createStatement().executeQuery("SELECT id, datetime FROM lessons ORDER BY id");
            while (rs.next()) {
                lessons.add(new String[]{String.valueOf(rs.getInt("id")), rs.getString("datetime")});
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lessons;
    }

    private String getLessons() {
        List<String[]> lessons = fetchLessons();
        if (lessons.isEmpty()) return "📭 Расписание пусто.";
        StringBuilder sb = new StringBuilder("📅 Занятия:\n");
        for (String[] l : lessons) sb.append("📌 ").append(l[1]).append("\n");
        return sb.toString();
    }

    private String getAllSignups() {
        StringBuilder sb = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT l.datetime, s.username FROM signups s JOIN lessons l ON l.id = s.lesson_id ORDER BY l.id");
            while (rs.next()) {
                sb.append("📌 ").append(rs.getString("datetime")).append(": ")
                        .append(rs.getString("username")).append("\n");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sb.length() == 0 ? "👥 Никто не записан." : sb.toString();
    }

    // ================= Reminder ==================

    private void startDailyReminder() {
        Runnable task = () -> {
            if (reminderEnabled) {
                try {
                    sendText(CHANNEL_ID, "🧘 Напоминаем: сегодня занятие по йоге!");
                } catch (Exception e) { e.printStackTrace(); }
            }
        };
        long delay = computeInitialDelay();
        scheduler.scheduleAtFixedRate(task, delay, 24 * 60, TimeUnit.MINUTES);
    }

    private long computeInitialDelay() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Moscow"));
        ZonedDateTime nextRun = now.withHour(14).withMinute(0).withSecond(0);
        if (now.compareTo(nextRun) > 0) nextRun = nextRun.plusDays(1);
        return Duration.between(now, nextRun).toMinutes();
    }
}



