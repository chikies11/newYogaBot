package org.example;

import org.example.service.DatabaseService;
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

    @Value("${app.channelId:}")
    private String channelId;

    @Value("${app.adminId:}")
    private String adminId;

    private final DatabaseService databaseService;
    private final Map<Long, String> userStates = new HashMap<>();
    private final Map<DayOfWeek, Map<String, String>> fixedSchedule = new HashMap<>();

    public YogaBot(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @PostConstruct
    public void postConstruct() {
        System.out.println("🔄 Инициализация YogaBot...");
        System.out.println("Admin ID: " + adminId);
        System.out.println("Channel ID: " + channelId);

        initializeFixedSchedule();
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

        Long userId = null;
        if (update.hasMessage()) {
            userId = update.getMessage().getFrom().getId();
        } else if (update.hasCallbackQuery()) {
            userId = update.getCallbackQuery().getFrom().getId();
        }

        if (userId == null) {
            System.out.println("⛔ Неизвестный пользователь");
            return null;
        }

        // Проверяем тип доступа
        boolean isAdminUser = isAdmin(userId);

        if (update.hasMessage() && update.getMessage().hasText()) {
            handleMessage(update.getMessage().getChatId(), update.getMessage().getText(), userId, isAdminUser);
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery(), isAdminUser);
        }

        System.out.println("✅ Завершение обработки update: " + update.getUpdateId());
        return null;
    }

    private void handleMessage(Long chatId, String text, Long userId, boolean isAdminUser) {
        System.out.println("💬 Обработка сообщения от " + userId + " (admin: " + isAdminUser + "): " + text);

        // Команды доступные всем пользователям
        // Команды доступные всем пользователям
        switch (text) {
            case "/start" -> showMainMenu(chatId, isAdminUser);
            case "📅 Расписание" -> showScheduleForUsers(chatId);
            case "📋 Мои записи" -> showUserRegistrations(chatId, userId);
            case "🕒 Проверить время" -> checkAndSendTime(chatId);
            default -> {
                if (isAdminUser) {
                    handleAdminMessage(chatId, text, userId);  // ← ВОТ ТУТ ВЫЗОВ
                } else {
                    sendMsg(chatId, "❌ Команда не найдена. Используйте кнопки меню.");
                }
            }
        }
    }

    private void checkAndSendTime(Long chatId) {
        checkServerTime();
        String timeInfo = "🕒 *Информация о времени:*\n\n" +
                "Сервер (UTC): " + LocalDateTime.now() + "\n" +
                "Москва (UTC+3): " + LocalDateTime.now().plusHours(3) + "\n" +
                "Час сервера: " + LocalDateTime.now().getHour() + "\n" +
                "Час Москвы: " + LocalDateTime.now().plusHours(3).getHour();

        sendMsg(chatId, timeInfo);
    }

    private void handleCallbackQuery(org.telegram.telegrambots.meta.api.objects.CallbackQuery callbackQuery, boolean isAdminUser) {
        Long chatId = callbackQuery.getMessage().getChatId();
        Long userId = callbackQuery.getFrom().getId();
        String data = callbackQuery.getData();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        System.out.println("🔘 Обработка callback: " + data);

        // Callback'и только для админов
        if (!isAdminUser) {
            answerCallbackQuery(callbackQuery.getId(), "❌ Эта функция доступна только администраторам");
            return;
        }

        // Админские callback'и
        switch (data) {
            case "schedule_morning" -> showDaySelection(chatId, "morning");
            case "schedule_evening" -> showDaySelection(chatId, "evening");
            case "back_to_schedule" -> showScheduleMenu(chatId);
            case "back_to_main" -> showMainMenu(chatId, true);
            default -> {
                if (data.startsWith("day_")) {
                    handleDaySelection(chatId, data);
                } else if (data.startsWith("edit_")) {
                    handleEditLesson(chatId, data);
                } else if (data.startsWith("delete_")) {
                    handleDeleteLesson(chatId, data, messageId);
                }
            }
        }
    }

    private void handleAdminMessage(Long chatId, String text, Long userId) {
        System.out.println("👨‍💼 Обработка админской команды: " + text);

        switch (text) {
            case "📅 Расписание" -> showScheduleMenu(chatId);
            case "🔔 Уведомления" -> toggleNotifications(chatId);
            case "📋 Все записи" -> showRegistrations(chatId);
            case "🧪 Тест уведомлений" -> sendTestNotificationToAdmin(chatId);
            case "🕒 Проверить время" -> checkAndSendTime(chatId);
            case "🚫 Отмена" -> {
                userStates.remove(userId);
                showMainMenu(chatId, true);
            }
            default -> handleState(chatId, text, userId);
        }
    }

    private void showMainMenu(Long chatId, boolean isAdminUser) {
        String text;
        if (isAdminUser) {
            text = "🧘 *Админ-панель YogaBot*\n\nВыберите раздел для управления:";
        } else {
            text = "🧘 *YogaBot - Запись на занятия*\n\nВыберите действие:";
        }

        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(createMainMenuKeyboard(isAdminUser));

        try {
            execute(message);
            System.out.println("✅ Показано главное меню для чата " + chatId + " (admin: " + isAdminUser + ")");
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка отправки меню: " + e.getMessage());
        }
    }

    private ReplyKeyboardMarkup createMainMenuKeyboard(boolean isAdminUser) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setSelective(true);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        // Кнопки для всех пользователей
        KeyboardRow row1 = new KeyboardRow();
        row1.add("📅 Расписание");
        row1.add("📋 Мои записи");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("🕒 Проверить время");

        keyboard.add(row1);
        keyboard.add(row2);

        // Кнопки только для админов
        if (isAdminUser) {
            KeyboardRow adminRow1 = new KeyboardRow();
            adminRow1.add("🔔 Уведомления");
            adminRow1.add("📋 Все записи");

            KeyboardRow adminRow2 = new KeyboardRow();
            adminRow2.add("🧪 Тест уведомлений");

            keyboard.add(adminRow1);
            keyboard.add(adminRow2);
        }

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    private void showScheduleForUsers(Long chatId) {
        String scheduleText = getWeeklySchedule();
        String text = "📅 *Расписание на неделю:*\n\n" + scheduleText +
                "\n\nЗаписывайтесь на занятия через уведомления в канале!";

        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setParseMode("Markdown");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка отправки расписания: " + e.getMessage());
        }
    }

    private void showUserRegistrations(Long chatId, Long userId) {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        try {
            boolean hasMorning = databaseService.isUserRegistered(userId, tomorrow, "morning");
            boolean hasEvening = databaseService.isUserRegistered(userId, tomorrow, "evening");

            StringBuilder sb = new StringBuilder();
            sb.append("📋 *Ваши записи на завтра (").append(tomorrow.format(DateTimeFormatter.ofPattern("dd.MM"))).append(")*\n\n");

            if (!hasMorning && !hasEvening) {
                sb.append("У вас нет записей на завтра.\n\n");
                sb.append("Чтобы записаться, используйте кнопки в уведомлениях канала @yoga_yollayo11");
            } else {
                if (hasMorning) {
                    sb.append("✅ Записан(а) на утреннюю практику\n");
                }
                if (hasEvening) {
                    sb.append("✅ Записан(а) на вечернюю практику\n");
                }
                sb.append("\nЧтобы отменить запись, используйте кнопки в уведомлениях канала");
            }

            sendMsg(chatId, sb.toString());

        } catch (Exception e) {
            System.err.println("❌ Ошибка получения записей пользователя: " + e.getMessage());
            sendMsg(chatId, "❌ Ошибка при загрузке ваших записей");
        }
    }

    private void handleUserRegistrationCallback(org.telegram.telegrambots.meta.api.objects.CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long userId = callbackQuery.getFrom().getId();
        String username = callbackQuery.getFrom().getUserName();
        String firstName = callbackQuery.getFrom().getFirstName();

        String displayName = username != null ? "@" + username : firstName;
        boolean isSignup = data.startsWith("signup_");
        String lessonType = data.substring(isSignup ? 7 : 7); // "signup_" или "cancel_"
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        boolean success;
        if (isSignup) {
            success = databaseService.registerUser(userId, username, displayName, tomorrow, lessonType);
        } else {
            success = databaseService.cancelRegistration(userId, tomorrow, lessonType);
        }

        String lessonTypeText = lessonType.equals("morning") ? "утреннюю" : "вечернюю";
        String answer;

        if (isSignup) {
            answer = success ?
                    "✅ Вы записаны на " + lessonTypeText + " практику!" :
                    "❌ Вы уже записаны на это занятие!";
        } else {
            answer = success ?
                    "❌ Запись на " + lessonTypeText + " практику отменена!" :
                    "❌ Вы не записаны на это занятие!";
        }

        answerCallbackQuery(callbackQuery.getId(), answer);

        // Логируем действие
        String action = isSignup ? "записался" : "отменил запись";
        System.out.println("👤 Пользователь " + displayName + " " + action + " на " + lessonTypeText + " практику");
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
        boolean newState = databaseService.toggleNotifications();

        String text = newState ?
                "🔔 *Уведомления включены!*\n\nАвтоматические уведомления будут отправляться в канал:\n• Утренние - в 12:00 МСК\n• Вечерние - в 18:00 МСК\n• Отсутствие занятий - в 14:00 МСК" :
                "🔕 *Уведомления отключены!*\n\nАвтоматические уведомления не будут отправляться в канал.";

        sendMsg(chatId, text);
    }

    private void showRegistrations(Long chatId) {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Map<String, List<String>> registrations = databaseService.getRegistrationsForDate(tomorrow);

        StringBuilder sb = new StringBuilder();
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

        // Отправляем уведомление если ЕСТЬ отсутствующие занятия
        if (!hasMorning && !hasEvening) {
            String text = "Не могу стоять, пока другие работают... Пойду полежу...)\n\nУра, завтра занятий нет! Отдыхаем и восстанавливаемся! 💫";
            sendToChannel(text);
        } else if (!hasMorning) {
            sendToChannel("🌅 На завтра утренних занятий нет");
        } else if (!hasEvening) {
            sendToChannel("🌇 На завтра вечерних занятий нет");
        } else {
            // Если оба занятия есть, не отправляем ничего
            System.out.println("✅ Оба занятия есть, уведомление не требуется");
        }
    }

    public void sendDailyNotifications() {
        System.out.println("🔔 Запуск sendDailyNotifications...");

        if (channelId == null || channelId.isEmpty()) {
            System.out.println("⚠️ Channel ID не настроен: " + channelId);
            return;
        }

        if (!databaseService.areNotificationsEnabled()) {
            System.out.println("🔕 Уведомления отключены в настройках");
            return;
        }

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalTime now = LocalTime.now();

        System.out.println("📅 Завтра: " + tomorrow);
        System.out.println("🕒 Текущее время UTC: " + now);
        System.out.println("🕒 Текущее время МСК: " + now.plusHours(3));

        Map<String, String> tomorrowSchedule = getTomorrowSchedule(tomorrow);
        String morningLesson = tomorrowSchedule.get("morning");
        String eveningLesson = tomorrowSchedule.get("evening");

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

        boolean success = databaseService.registerUser(userId, username, displayName, tomorrow, lessonType);

        String answer = success ?
                "✅ Вы записаны на " + (lessonType.equals("morning") ? "утреннюю" : "вечернюю") + " практику!" :
                "❌ Вы уже записаны на это занятие!";

        answerCallbackQuery(callbackQuery.getId(), answer);
    }

    private void handleUserCancel(org.telegram.telegrambots.meta.api.objects.CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long userId = callbackQuery.getFrom().getId();
        String lessonType = data.substring(7);
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        boolean success = databaseService.cancelRegistration(userId, tomorrow, lessonType);

        String answer = success ?
                "❌ Запись на " + (lessonType.equals("morning") ? "утреннюю" : "вечернюю") + " практику отменена!" :
                "❌ Вы не записаны на это занятие!";

        answerCallbackQuery(callbackQuery.getId(), answer);
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

    private InlineKeyboardButton createInlineButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton(text);
        button.setCallbackData(callbackData);
        return button;
    }

    private boolean isAdmin(Long userId) {
        if (adminId == null) return false;

        // Поддерживаем как старый формат (один ID), так и новый (список ID через запятую)
        String[] adminIds = adminId.split(",");
        for (String id : adminIds) {
            if (id.trim().equals(userId.toString())) {
                return true;
            }
        }
        return false;
    }

    public void checkServerTime() {
        System.out.println("🕒 Текущее время сервера (UTC): " + LocalDateTime.now());
        System.out.println("🕒 Текущее время Moscow (UTC+3): " + LocalDateTime.now().plusHours(3));
        System.out.println("🕒 Текущий час (UTC): " + LocalDateTime.now().getHour());
        System.out.println("🕒 Текущий час (Moscow): " + LocalDateTime.now().plusHours(3).getHour());
    }
}