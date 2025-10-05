package org.example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.*;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class YogaBot extends TelegramWebhookBot {

    @Value("${bot.username:}")
    private String botUsername;

    @Value("${bot.token:}")
    private String botToken;

    @Value("${bot.path:}")
    private String botPath;

    @Value("${spring.datasource.url:}")
    private String dbUrl;

    @Value("${spring.datasource.username:}")
    private String dbUsername;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${app.channelId:}")
    private String channelId;

    @Value("${app.adminId:}")
    private String adminId;

    private final Map<Long, String> userStates = new HashMap<>();
    private final Map<DayOfWeek, Map<String, String>> fixedSchedule = new HashMap<>();

    @PostConstruct
    public void postConstruct() {
        System.out.println("🔄 Инициализация YogaBot...");
        System.out.println("Admin ID: " + adminId);
        System.out.println("Channel ID: " + channelId);

        initializeFixedSchedule();

        if (dbUrl != null && !dbUrl.isEmpty() && dbUsername != null && dbPassword != null) {
            initDb();
        } else {
            System.out.println("⚠️ Database не настроен, пропускаем инициализацию БД");
        }
        System.out.println("✅ YogaBot инициализирован");
    }

    private void initializeFixedSchedule() {
        // Понедельник
        Map<String, String> monday = new HashMap<>();
        monday.put("morning", "8:00 - 11:30 - Майсор класс");
        monday.put("evening", "17:00 - 20:00 - Майсор класс");
        fixedSchedule.put(DayOfWeek.MONDAY, monday);

        // Вторник
        Map<String, String> tuesday = new HashMap<>();
        tuesday.put("morning", "8:00 - 11:30 - Майсор класс");
        tuesday.put("evening", "Отдых");
        fixedSchedule.put(DayOfWeek.TUESDAY, tuesday);

        // Среда
        Map<String, String> wednesday = new HashMap<>();
        wednesday.put("morning", "8:00 - 11:30 - Майсор класс");
        wednesday.put("evening", "18:30 - 20:00 - Майсор класс");
        fixedSchedule.put(DayOfWeek.WEDNESDAY, wednesday);

        // Четверг
        Map<String, String> thursday = new HashMap<>();
        thursday.put("morning", "8:00 - 11:30 - Майсор класс");
        thursday.put("evening", "17:00 - 20:00 - Майсор класс");
        fixedSchedule.put(DayOfWeek.THURSDAY, thursday);

        // Пятница
        Map<String, String> friday = new HashMap<>();
        friday.put("morning", "8:00 - 11:30 - Майсор класс");
        friday.put("evening", "17:00 - 20:00 - Майсор класс");
        fixedSchedule.put(DayOfWeek.FRIDAY, friday);

        // Суббота
        Map<String, String> saturday = new HashMap<>();
        saturday.put("morning", "ОТДЫХ");
        saturday.put("evening", "ОТДЫХ");
        fixedSchedule.put(DayOfWeek.SATURDAY, saturday);

        // Воскресенье
        Map<String, String> sunday = new HashMap<>();
        sunday.put("morning", "10:00 - 11:30 LED-КЛАСС\n11:30 - 12:00 Конференция (По необходимости)");
        sunday.put("evening", "Отдых");
        fixedSchedule.put(DayOfWeek.SUNDAY, sunday);
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
        return botPath != null && !botPath.isEmpty() ? botPath : "/";
    }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        System.out.println("🔄 Начало обработки update: " + update.getUpdateId());

        // Проверяем, что это сообщение от админа
        Long userId = null;
        if (update.hasMessage()) {
            userId = update.getMessage().getFrom().getId();
        } else if (update.hasCallbackQuery()) {
            userId = update.getCallbackQuery().getFrom().getId();
        }

        if (userId == null || !isAdmin(userId)) {
            System.out.println("⛔ Неавторизованный доступ от пользователя: " + userId);
            return null;
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            handleMessage(update.getMessage().getChatId(), update.getMessage().getText(), userId);
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }

        System.out.println("✅ Завершение обработки update: " + update.getUpdateId());
        return null;
    }

    private void handleMessage(Long chatId, String text, Long userId) {
        System.out.println("💬 Обработка сообщения от " + userId + ": " + text);

        switch (text) {
            case "/start" -> showMainMenu(chatId);
            case "📅 Расписание" -> showScheduleMenu(chatId);
            case "🔔 Уведомления" -> toggleNotifications(chatId);
            case "📋 Запись" -> showRegistrations(chatId);
            case "🧪 Тест уведомлений" -> sendTestNotificationToAdmin(chatId);
            case "🚫 Отмена" -> {
                userStates.remove(userId);
                showMainMenu(chatId);
            }
            default -> handleState(chatId, text, userId);
        }
    }

    private void handleCallbackQuery(org.telegram.telegrambots.meta.api.objects.CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        System.out.println("🔘 Обработка callback: " + data);

        switch (data) {
            case "schedule_morning" -> showDaySelection(chatId, "morning");
            case "schedule_evening" -> showDaySelection(chatId, "evening");
            case "back_to_schedule" -> showScheduleMenu(chatId);
            case "back_to_main" -> showMainMenu(chatId);
            default -> {
                if (data.startsWith("day_")) {
                    handleDaySelection(chatId, data);
                } else if (data.startsWith("edit_")) {
                    handleEditLesson(chatId, data);
                } else if (data.startsWith("delete_")) {
                    handleDeleteLesson(chatId, data, messageId);
                } else if (data.startsWith("signup_")) {
                    handleUserSignup(callbackQuery);
                } else if (data.startsWith("cancel_")) {
                    handleUserCancel(callbackQuery);
                }
            }
        }
    }

    private void showMainMenu(Long chatId) {
        String text = "🧘 *Админ-панель YogaBot*\n\nВыберите раздел для управления:";

        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(createMainMenuKeyboard());

        try {
            execute(message);
            System.out.println("✅ Показано главное меню для чата " + chatId);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка отправки меню: " + e.getMessage());
        }
    }

    private ReplyKeyboardMarkup createMainMenuKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setSelective(true);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("📅 Расписание");
        row1.add("🔔 Уведомления");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("📋 Запись");

        KeyboardRow row3 = new KeyboardRow();
        row3.add("🧪 Тест уведомлений");

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    private void showScheduleMenu(Long chatId) {
        String scheduleText = getWeeklySchedule();
        String text = "📅 *Расписание на неделю:*\n\n" + scheduleText + "\n\nВыберите раздел для управления:";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> timeRow = new ArrayList<>();
        timeRow.add(createInlineButton("🌅 Утро", "schedule_morning"));
        timeRow.add(createInlineButton("🌇 Вечер", "schedule_evening"));

        List<InlineKeyboardButton> backRow = new ArrayList<>();
        backRow.add(createInlineButton("🔙 Назад", "back_to_main"));

        rows.add(timeRow);
        rows.add(backRow);
        markup.setKeyboard(rows);

        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка отправки меню расписания: " + e.getMessage());
        }
    }

    private String getWeeklySchedule() {
        StringBuilder sb = new StringBuilder();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE (dd.MM)");

        for (int i = 0; i < 7; i++) {
            LocalDate date = today.plusDays(i);
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            String dayName = date.format(formatter);
            dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1);

            sb.append("📅 *").append(dayName).append("*\n");

            String morningLesson = fixedSchedule.get(dayOfWeek).get("morning");
            String eveningLesson = fixedSchedule.get(dayOfWeek).get("evening");

            sb.append("🌅 *Утро:* ").append(morningLesson).append("\n");
            sb.append("🌇 *Вечер:* ").append(eveningLesson).append("\n\n");
        }

        return sb.toString();
    }

    private void showDaySelection(Long chatId, String lessonType) {
        String typeText = lessonType.equals("morning") ? "утренних" : "вечерних";
        String text = "📅 Выберите день для изменения " + typeText + " занятий:";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        DayOfWeek[] days = {
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        };

        String[] dayNames = {"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"};

        for (int i = 0; i < days.length; i++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            String callbackData = "day_" + days[i] + "_" + lessonType;
            row.add(createInlineButton(dayNames[i], callbackData));
            rows.add(row);
        }

        List<InlineKeyboardButton> backRow = new ArrayList<>();
        backRow.add(createInlineButton("🔙 Назад", "back_to_schedule"));
        rows.add(backRow);

        markup.setKeyboard(rows);

        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка отправки выбора дня: " + e.getMessage());
        }
    }

    private void handleDaySelection(Long chatId, String data) {
        String[] parts = data.split("_");
        DayOfWeek dayOfWeek = DayOfWeek.valueOf(parts[1]);
        String lessonType = parts[2];

        String dayName = getRussianDayNameFull(dayOfWeek);
        String currentSchedule = fixedSchedule.get(dayOfWeek).get(lessonType);

        String text = "📅 *" + dayName + " - " + (lessonType.equals("morning") ? "Утро" : "Вечер") + "*\n\n";
        text += "📝 *Текущее расписание:*\n" + currentSchedule + "\n\n";
        text += "Выберите действие:";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> actionRow = new ArrayList<>();
        actionRow.add(createInlineButton("✏️ Изменить", "edit_" + dayOfWeek + "_" + lessonType));
        actionRow.add(createInlineButton("🗑️ Удалить", "delete_" + dayOfWeek + "_" + lessonType));

        List<InlineKeyboardButton> backRow = new ArrayList<>();
        backRow.add(createInlineButton("🔙 Назад", "schedule_" + lessonType));

        rows.add(actionRow);
        rows.add(backRow);
        markup.setKeyboard(rows);

        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка отправки действий для дня: " + e.getMessage());
        }
    }

    private void handleEditLesson(Long chatId, String data) {
        String[] parts = data.split("_");
        DayOfWeek dayOfWeek = DayOfWeek.valueOf(parts[1]);
        String lessonType = parts[2];

        String dayName = getRussianDayNameFull(dayOfWeek);
        String typeText = lessonType.equals("morning") ? "утреннего" : "вечернего";
        String currentSchedule = fixedSchedule.get(dayOfWeek).get(lessonType);

        userStates.put(chatId, "editing_" + dayOfWeek + "_" + lessonType);

        String text = "✏️ *Изменение " + typeText + " занятия на " + dayName + "*\n\n";
        text += "📝 *Текущее расписание:*\n" + currentSchedule + "\n\n";
        text += "✍️ *Введите новое расписание в формате:*\n";
        text += "`10:00 - 11:30 - Аштанга йога`\n\n";
        text += "Или отправьте '🚫 Отмена' для отмены";

        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(createCancelKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка запроса изменения: " + e.getMessage());
        }
    }

    private void handleDeleteLesson(Long chatId, String data, Integer messageId) {
        String[] parts = data.split("_");
        DayOfWeek dayOfWeek = DayOfWeek.valueOf(parts[1]);
        String lessonType = parts[2];

        String dayName = getRussianDayNameFull(dayOfWeek);
        String typeText = lessonType.equals("morning") ? "утреннее" : "вечернее";

        fixedSchedule.get(dayOfWeek).put(lessonType, "Отдых");

        String text = "✅ *" + typeText + " занятие на " + dayName + " удалено!*\n\n";
        text += "Теперь в расписании указано: *Отдых*";

        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(chatId.toString());
        editMessage.setMessageId(messageId);
        editMessage.setText(text);
        editMessage.setParseMode("Markdown");

        try {
            execute(editMessage);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка обновления сообщения: " + e.getMessage());
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        showDaySelection(chatId, lessonType);
    }

    private void handleState(Long chatId, String text, Long userId) {
        String state = userStates.get(userId);

        if (state != null && state.startsWith("editing_")) {
            String[] parts = state.split("_");
            DayOfWeek dayOfWeek = DayOfWeek.valueOf(parts[1]);
            String lessonType = parts[2];

            updateLessonSchedule(chatId, text, dayOfWeek, lessonType);
            userStates.remove(userId);
        }
    }

    private void updateLessonSchedule(Long chatId, String newSchedule, DayOfWeek dayOfWeek, String lessonType) {
        String dayName = getRussianDayNameFull(dayOfWeek);
        String typeText = lessonType.equals("morning") ? "утреннее" : "вечернее";

        fixedSchedule.get(dayOfWeek).put(lessonType, newSchedule);

        String text = "✅ *" + typeText + " занятие на " + dayName + " обновлено!*\n\n";
        text += "📝 *Новое расписание:*\n" + newSchedule + "\n\n";
        text += "Изменения отразятся в уведомлениях и общем расписании.";

        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setParseMode("Markdown");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка отправки подтверждения: " + e.getMessage());
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        showDaySelection(chatId, lessonType);
    }

    private ReplyKeyboardMarkup createCancelKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setSelective(true);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        row.add("🚫 Отмена");

        keyboardMarkup.setKeyboard(List.of(row));
        return keyboardMarkup;
    }

    private String getRussianDayNameFull(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY: return "Понедельник";
            case TUESDAY: return "Вторник";
            case WEDNESDAY: return "Среда";
            case THURSDAY: return "Четверг";
            case FRIDAY: return "Пятница";
            case SATURDAY: return "Суббота";
            case SUNDAY: return "Воскресенье";
            default: return "";
        }
    }

    private void toggleNotifications(Long chatId) {
        try (Connection conn = getConnection()) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT notifications_enabled FROM bot_settings WHERE id = 1");

            boolean currentState = true;
            if (rs.next()) {
                currentState = rs.getBoolean("notifications_enabled");
            }

            PreparedStatement updateStmt = conn.prepareStatement(
                    "INSERT INTO bot_settings (id, notifications_enabled) VALUES (1, ?) ON CONFLICT (id) DO UPDATE SET notifications_enabled = ?"
            );
            boolean newState = !currentState;
            updateStmt.setBoolean(1, newState);
            updateStmt.setBoolean(2, newState);
            updateStmt.executeUpdate();

            String text = newState ?
                    "🔔 *Уведомления включены!*\n\nАвтоматические уведомления будут отправляться в канал:\n• Утренние - в 12:00 МСК\n• Вечерние - в 18:00 МСК\n• Отсутствие занятий - в 14:00 МСК" :
                    "🔕 *Уведомления отключены!*\n\nАвтоматические уведомления не будут отправляться в канал.";

            SendMessage message = new SendMessage(chatId.toString(), text);
            message.setParseMode("Markdown");
            execute(message);

        } catch (Exception e) {
            System.err.println("❌ Ошибка переключения уведомлений: " + e.getMessage());
            sendMsg(chatId, "❌ Ошибка при изменении настроек уведомлений");
        }
    }

    private void showRegistrations(Long chatId) {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        StringBuilder sb = new StringBuilder();

        try (Connection conn = getConnection()) {
            PreparedStatement stmt = conn.prepareStatement("""
                SELECT lesson_type, display_name 
                FROM registrations 
                WHERE lesson_date = ? 
                ORDER BY lesson_type, created_at
            """);
            stmt.setDate(1, Date.valueOf(tomorrow));
            ResultSet rs = stmt.executeQuery();

            Map<String, List<String>> registrations = new HashMap<>();
            registrations.put("morning", new ArrayList<>());
            registrations.put("evening", new ArrayList<>());

            while (rs.next()) {
                String lessonType = rs.getString("lesson_type");
                String displayName = rs.getString("display_name");
                registrations.get(lessonType).add(displayName);
            }

            sb.append("📋 *Записи на завтра (").append(tomorrow.format(DateTimeFormatter.ofPattern("dd.MM"))).append(")*\n\n");

            sb.append("🌅 *Утренняя практика:*\n");
            if (registrations.get("morning").isEmpty()) {
                sb.append("Записей пока нет\n\n");
            } else {
                int counter = 1;
                for (String name : registrations.get("morning")) {
                    sb.append(counter).append(". ").append(name).append("\n");
                    counter++;
                }
                sb.append("\n");
            }

            sb.append("🌇 *Вечерняя практика:*\n");
            if (registrations.get("evening").isEmpty()) {
                sb.append("Записей пока нет");
            } else {
                int counter = 1;
                for (String name : registrations.get("evening")) {
                    sb.append(counter).append(". ").append(name).append("\n");
                    counter++;
                }
            }

            sb.append("\n\n📊 *Статистика:*\n");
            sb.append("• Утренние: ").append(registrations.get("morning").size()).append(" чел.\n");
            sb.append("• Вечерние: ").append(registrations.get("evening").size()).append(" чел.\n");
            sb.append("• Всего: ").append(registrations.get("morning").size() + registrations.get("evening").size()).append(" чел.");

        } catch (SQLException e) {
            sb.append("❌ Ошибка загрузки записей");
        }

        sendMsg(chatId, sb.toString());
    }

    public void sendTestNotification() {
        System.out.println("🧪 Отправка тестового уведомления...");

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Map<String, String> tomorrowSchedule = getTomorrowSchedule(tomorrow);

        String morningLesson = tomorrowSchedule.get("morning");
        String eveningLesson = tomorrowSchedule.get("evening");

        System.out.println("📅 Расписание на завтра:");
        System.out.println("Утро: " + morningLesson);
        System.out.println("Вечер: " + eveningLesson);

        System.out.println("🔔 Тест утреннего уведомления...");
        sendMorningNotification(morningLesson);

        try {
            Thread.sleep(2000);
            System.out.println("🔔 Тест вечернего уведомления...");
            sendEveningNotification(eveningLesson);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("✅ Тестовые уведомления отправлены с кнопками отмены!");
    }

    private void sendTestNotificationToAdmin(Long chatId) {
        try {
            sendTestNotification();
            sendMsg(chatId, "✅ Тестовые уведомления отправлены в канал! Проверьте @yoga_yollayo11");
        } catch (Exception e) {
            sendMsg(chatId, "❌ Ошибка отправки тестовых уведомлений: " + e.getMessage());
        }
    }

    public void sendManualNotification(String type) {
        System.out.println("🔔 Ручная отправка уведомления: " + type);

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Map<String, String> tomorrowSchedule = getTomorrowSchedule(tomorrow);

        String morningLesson = tomorrowSchedule.get("morning");
        String eveningLesson = tomorrowSchedule.get("evening");

        switch (type) {
            case "morning":
                sendMorningNotification(morningLesson);
                break;
            case "evening":
                sendEveningNotification(eveningLesson);
                break;
            case "no_classes":
                sendNoClassesNotification(morningLesson, eveningLesson);
                break;
            case "all":
                sendTestNotification();
                break;
        }
    }

    private Map<String, String> getTomorrowSchedule(LocalDate tomorrow) {
        Map<String, String> schedule = new HashMap<>();
        DayOfWeek dayOfWeek = tomorrow.getDayOfWeek();

        schedule.put("morning", fixedSchedule.get(dayOfWeek).get("morning"));
        schedule.put("evening", fixedSchedule.get(dayOfWeek).get("evening"));

        return schedule;
    }

    private void sendMorningNotification(String morningLesson) {
        if (morningLesson == null || morningLesson.equals("ОТДЫХ") || morningLesson.equals("Отдых")) {
            sendToChannel("🌅 На завтра утренних занятий нет");
            return;
        }

        String text = "🌅 *Завтрашняя утренняя практика:*\n\n" + morningLesson + "\n\n";
        text += "📍 *Место:* Студия йоги\n\n";
        text += "Записывайтесь на занятие!";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createInlineButton("✅ Записаться", "signup_morning"));
        row.add(createInlineButton("❌ Отменить запись", "cancel_morning"));
        markup.setKeyboard(List.of(row));

        sendToChannel(text, markup);
    }

    private void sendEveningNotification(String eveningLesson) {
        if (eveningLesson == null || eveningLesson.equals("ОТДЫХ") || eveningLesson.equals("Отдых")) {
            sendToChannel("🌇 На завтра вечерних занятий нет");
            return;
        }

        String text = "🌇 *Завтрашняя вечерняя практика:*\n\n" + eveningLesson + "\n\n";
        text += "📍 *Место:* Студия йоги\n\n";
        text += "Записывайтесь на занятие!";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createInlineButton("✅ Записаться", "signup_evening"));
        row.add(createInlineButton("❌ Отменить запись", "cancel_evening"));
        markup.setKeyboard(List.of(row));

        sendToChannel(text, markup);
    }

    private void sendNoClassesNotification(String morningLesson, String eveningLesson) {
        boolean hasMorning = morningLesson != null && !morningLesson.equals("ОТДЫХ") && !morningLesson.equals("Отдых");
        boolean hasEvening = eveningLesson != null && !eveningLesson.equals("ОТДЫХ") && !eveningLesson.equals("Отдых");

        if (!hasMorning && !hasEvening) {
            String text = "Не могу стоять, пока другие работают... Пойду полежу...)\n\nУра, завтра занятий нет! Отдыхаем и восстанавливаемся! 💫";
            sendToChannel(text);
        } else if (!hasMorning) {
            sendToChannel("🌅 На завтра утренних занятий нет");
        } else if (!hasEvening) {
            sendToChannel("🌇 На завтра вечерних занятий нет");
        }
    }

    public void sendDailyNotifications() {
        System.out.println("🔔 Запуск sendDailyNotifications...");

        if (channelId == null || channelId.isEmpty()) {
            System.out.println("⚠️ Channel ID не настроен: " + channelId);
            return;
        }

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalTime now = LocalTime.now();

        System.out.println("📅 Завтра: " + tomorrow);
        System.out.println("🕒 Текущее время UTC: " + now);
        System.out.println("🕒 Текущее время МСК: " + now.plusHours(3));

        try (Connection conn = getConnection()) {
            Statement checkStmt = conn.createStatement();
            ResultSet settingsRs = checkStmt.executeQuery("SELECT notifications_enabled FROM bot_settings WHERE id = 1");

            if (!settingsRs.next() || !settingsRs.getBoolean("notifications_enabled")) {
                System.out.println("🔕 Уведомления отключены в настройках");
                return;
            }
            System.out.println("✅ Уведомления включены в настройках");

            Map<String, String> tomorrowSchedule = getTomorrowSchedule(tomorrow);

            String morningLesson = tomorrowSchedule.get("morning");
            String eveningLesson = tomorrowSchedule.get("evening");

            System.out.println("📅 Расписание на завтра:");
            System.out.println("Утро: " + morningLesson);
            System.out.println("Вечер: " + eveningLesson);

            // Определяем тип уведомления по времени
            int hour = now.getHour();
            int minute = now.getMinute();

            System.out.println("⏰ Проверка времени: " + hour + ":" + minute);

            if (hour == 9 && minute == 0) { // 12:00 МСК
                System.out.println("🌅 Отправка утреннего уведомления...");
                sendMorningNotification(morningLesson);
            } else if (hour == 15 && minute == 0) { // 18:00 МСК
                System.out.println("🌇 Отправка вечернего уведомления...");
                sendEveningNotification(eveningLesson);
            } else if (hour == 11 && minute == 0) { // 14:00 МСК
                System.out.println("📝 Отправка уведомления об отсутствии занятий...");
                sendNoClassesNotification(morningLesson, eveningLesson);
            } else {
                System.out.println("⏰ Не время для уведомлений");
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка отправки уведомлений: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendToChannel(String text) {
        sendToChannel(text, null);
    }

    private void sendToChannel(String text, InlineKeyboardMarkup markup) {
        if (channelId == null || channelId.isEmpty()) {
            System.out.println("⚠️ Channel ID не настроен");
            return;
        }

        SendMessage message = new SendMessage(channelId, text);
        message.setParseMode("Markdown");

        if (markup != null) {
            message.setReplyMarkup(markup);
        }

        try {
            execute(message);
            System.out.println("✅ Уведомление отправлено в канал");
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка отправки в канал: " + e.getMessage());
        }
    }

    private void handleUserSignup(org.telegram.telegrambots.meta.api.objects.CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long userId = callbackQuery.getFrom().getId();
        String username = callbackQuery.getFrom().getUserName();
        String firstName = callbackQuery.getFrom().getFirstName();

        String displayName = username != null ? "@" + username : firstName;
        String lessonType = data.substring(7);

        LocalDate tomorrow = LocalDate.now().plusDays(1);

        try (Connection conn = getConnection()) {
            PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT id FROM registrations WHERE user_id = ? AND lesson_date = ? AND lesson_type = ?"
            );
            checkStmt.setLong(1, userId);
            checkStmt.setDate(2, Date.valueOf(tomorrow));
            checkStmt.setString(3, lessonType);
            ResultSet rs = checkStmt.executeQuery();

            if (!rs.next()) {
                PreparedStatement insertStmt = conn.prepareStatement(
                        "INSERT INTO registrations (user_id, username, display_name, lesson_date, lesson_type) VALUES (?, ?, ?, ?, ?)"
                );
                insertStmt.setLong(1, userId);
                insertStmt.setString(2, username);
                insertStmt.setString(3, displayName);
                insertStmt.setDate(4, Date.valueOf(tomorrow));
                insertStmt.setString(5, lessonType);
                insertStmt.executeUpdate();

                String answer = "✅ Вы записаны на " + (lessonType.equals("morning") ? "утреннюю" : "вечернюю") + " практику!";
                answerCallbackQuery(callbackQuery.getId(), answer);
            } else {
                answerCallbackQuery(callbackQuery.getId(), "❌ Вы уже записаны на это занятие!");
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка записи пользователя: " + e.getMessage());
            answerCallbackQuery(callbackQuery.getId(), "❌ Произошла ошибка при записи");
        }
    }

    private void handleUserCancel(org.telegram.telegrambots.meta.api.objects.CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long userId = callbackQuery.getFrom().getId();
        String username = callbackQuery.getFrom().getUserName();
        String firstName = callbackQuery.getFrom().getFirstName();

        String displayName = username != null ? "@" + username : firstName;
        String lessonType = data.substring(7);

        LocalDate tomorrow = LocalDate.now().plusDays(1);

        try (Connection conn = getConnection()) {
            PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT id FROM registrations WHERE user_id = ? AND lesson_date = ? AND lesson_type = ?"
            );
            checkStmt.setLong(1, userId);
            checkStmt.setDate(2, Date.valueOf(tomorrow));
            checkStmt.setString(3, lessonType);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                PreparedStatement deleteStmt = conn.prepareStatement(
                        "DELETE FROM registrations WHERE user_id = ? AND lesson_date = ? AND lesson_type = ?"
                );
                deleteStmt.setLong(1, userId);
                deleteStmt.setDate(2, Date.valueOf(tomorrow));
                deleteStmt.setString(3, lessonType);
                int deletedRows = deleteStmt.executeUpdate();

                if (deletedRows > 0) {
                    String answer = "❌ Запись на " + (lessonType.equals("morning") ? "утреннюю" : "вечернюю") + " практику отменена!";
                    answerCallbackQuery(callbackQuery.getId(), answer);
                    System.out.println("✅ Пользователь " + displayName + " отменил запись на " + lessonType);
                } else {
                    answerCallbackQuery(callbackQuery.getId(), "❌ Не удалось отменить запись");
                }
            } else {
                answerCallbackQuery(callbackQuery.getId(), "❌ Вы не записаны на это занятие!");
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка отмены записи пользователя: " + e.getMessage());
            answerCallbackQuery(callbackQuery.getId(), "❌ Произошла ошибка при отмене записи");
        }
    }

    private void answerCallbackQuery(String callbackQueryId, String text) {
        try {
            org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answer = new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQueryId);
            answer.setText(text);
            answer.setShowAlert(false);
            execute(answer);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка ответа на callback: " + e.getMessage());
        }
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

    private Connection getConnection() throws SQLException {
        System.out.println("🔗 Подключение к БД...");
        return DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
    }

    private InlineKeyboardButton createInlineButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton(text);
        button.setCallbackData(callbackData);
        return button;
    }

    private void initDb() {
        System.out.println("🔄 Инициализация базы данных...");

        try (Connection conn = getConnection()) {
            Statement st = conn.createStatement();

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS lessons (
                    id BIGSERIAL PRIMARY KEY,
                    lesson_date DATE NOT NULL,
                    lesson_type VARCHAR(10) NOT NULL,
                    description TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(lesson_date, lesson_type)
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS registrations (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    username VARCHAR(255),
                    display_name VARCHAR(255) NOT NULL,
                    lesson_date DATE NOT NULL,
                    lesson_type VARCHAR(10) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(user_id, lesson_date, lesson_type)
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS bot_settings (
                    id INTEGER PRIMARY KEY,
                    notifications_enabled BOOLEAN DEFAULT true,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            st.executeUpdate("""
                INSERT INTO bot_settings (id, notifications_enabled) 
                VALUES (1, true) 
                ON CONFLICT (id) DO NOTHING
            """);

            System.out.println("✅ База данных инициализирована");
        } catch (SQLException e) {
            System.err.println("❌ Ошибка инициализации БД: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isAdmin(Long userId) {
        return adminId != null && adminId.equals(userId.toString());
    }

    public void checkServerTime() {
        System.out.println("🕒 Текущее время сервера (UTC): " + LocalDateTime.now());
        System.out.println("🕒 Текущее время Moscow (UTC+3): " + LocalDateTime.now().plusHours(3));
        System.out.println("🕒 Текущий час (UTC): " + LocalDateTime.now().getHour());
        System.out.println("🕒 Текущий час (Moscow): " + LocalDateTime.now().plusHours(3).getHour());
    }
}