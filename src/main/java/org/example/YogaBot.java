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

        // Инициализируем фиксированное расписание
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
            case "add_morning" -> showDateSelection(chatId, "morning");
            case "add_evening" -> showDateSelection(chatId, "evening");
            case "delete_morning" -> showCustomLessonsForDeletion(chatId, "morning");
            case "delete_evening" -> showCustomLessonsForDeletion(chatId, "evening");
            case "back_to_schedule" -> showScheduleMenu(chatId);
            case "back_to_main" -> showMainMenu(chatId);
            default -> {
                if (data.startsWith("select_date_")) {
                    handleDateSelection(chatId, data);
                } else if (data.startsWith("delete_lesson_")) {
                    deleteLesson(chatId, data.substring(14), messageId);
                } else if (data.startsWith("signup_")) {
                    handleUserSignup(callbackQuery);
                }
            }
        }
    }

    private void showDateSelection(Long chatId, String lessonType) {
        String typeText = lessonType.equals("morning") ? "утреннюю" : "вечернюю";
        String text = "📅 Выберите дату для " + typeText + " практики:";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM");

        // Создаем кнопки на 7 дней вперед
        for (int i = 0; i < 7; i++) {
            LocalDate date = today.plusDays(i);
            String dateText = date.format(formatter);
            String dayName = getRussianDayName(date.getDayOfWeek());

            List<InlineKeyboardButton> row = new ArrayList<>();
            String callbackData = "select_date_" + date + "_" + lessonType;
            row.add(createInlineButton(dateText + " (" + dayName + ")", callbackData));
            rows.add(row);
        }

        // Кнопка назад
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        backRow.add(createInlineButton("🔙 Назад", "back_to_schedule"));
        rows.add(backRow);

        markup.setKeyboard(rows);

        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка отправки выбора даты: " + e.getMessage());
        }
    }

    private String getRussianDayName(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY: return "Пн";
            case TUESDAY: return "Вт";
            case WEDNESDAY: return "Ср";
            case THURSDAY: return "Чт";
            case FRIDAY: return "Пт";
            case SATURDAY: return "Сб";
            case SUNDAY: return "Вс";
            default: return "";
        }
    }

    private void handleDateSelection(Long chatId, String data) {
        // data format: "select_date_2025-10-05_morning"
        String[] parts = data.split("_");
        LocalDate date = LocalDate.parse(parts[2]);
        String lessonType = parts[3];

        userStates.put(chatId, "adding_" + date + "_" + lessonType);

        String typeText = lessonType.equals("morning") ? "утреннюю" : "вечернюю";
        String dateText = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String text = "✍️ Введите описание для " + typeText + " практики на " + dateText + ":\n\nПример: *Майсор класс*\n\nИли отправьте '🚫 Отмена' для отмены";

        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(createCancelKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка запроса описания: " + e.getMessage());
        }
    }

    private void handleUserSignup(org.telegram.telegrambots.meta.api.objects.CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long userId = callbackQuery.getFrom().getId();
        String username = callbackQuery.getFrom().getUserName();
        String firstName = callbackQuery.getFrom().getFirstName();

        String displayName = username != null ? "@" + username : firstName;
        String lessonType = data.substring(7); // "signup_morning" -> "morning"

        LocalDate tomorrow = LocalDate.now().plusDays(1);

        try (Connection conn = getConnection()) {
            // Проверяем, не записан ли уже пользователь
            PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT id FROM registrations WHERE user_id = ? AND lesson_date = ? AND lesson_type = ?"
            );
            checkStmt.setLong(1, userId);
            checkStmt.setDate(2, Date.valueOf(tomorrow));
            checkStmt.setString(3, lessonType);
            ResultSet rs = checkStmt.executeQuery();

            if (!rs.next()) {
                // Записываем пользователя
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

    private void handleState(Long chatId, String text, Long userId) {
        String state = userStates.get(userId);

        if (state != null && state.startsWith("adding_")) {
            String[] parts = state.split("_");
            LocalDate date = LocalDate.parse(parts[1]);
            String lessonType = parts[2];
            addLesson(chatId, text, date, lessonType);
            userStates.remove(userId);
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

        keyboard.add(row1);
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    private void showScheduleMenu(Long chatId) {
        String scheduleText = getWeeklySchedule();
        String text = "📅 *Расписание на неделю:*\n\n" + scheduleText + "\n\nВыберите действие:";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Кнопки добавления
        List<InlineKeyboardButton> addRow = new ArrayList<>();
        addRow.add(createInlineButton("➕ Утро", "add_morning"));
        addRow.add(createInlineButton("➕ Вечер", "add_evening"));

        // Кнопки удаления
        List<InlineKeyboardButton> deleteRow = new ArrayList<>();
        deleteRow.add(createInlineButton("🗑️ Утро", "delete_morning"));
        deleteRow.add(createInlineButton("🗑️ Вечер", "delete_evening"));

        // Кнопка назад
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        backRow.add(createInlineButton("🔙 Назад", "back_to_main"));

        rows.add(addRow);
        rows.add(deleteRow);
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

        // Загружаем кастомные занятия из БД
        Map<LocalDate, Map<String, String>> customLessons = loadCustomLessons();

        for (int i = 0; i < 7; i++) {
            LocalDate date = today.plusDays(i);
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            String dayName = date.format(formatter);
            dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1);

            sb.append("📅 *").append(dayName).append("*\n");

            // Проверяем есть ли кастомное занятие
            Map<String, String> customDay = customLessons.get(date);
            String morningLesson = customDay != null ? customDay.get("morning") : null;
            String eveningLesson = customDay != null ? customDay.get("evening") : null;

            // Если нет кастомного занятия, используем фиксированное расписание
            if (morningLesson == null) {
                morningLesson = fixedSchedule.get(dayOfWeek).get("morning");
            }
            if (eveningLesson == null) {
                eveningLesson = fixedSchedule.get(dayOfWeek).get("evening");
            }

            sb.append("🌅 *Утро:* ").append(morningLesson).append("\n");
            sb.append("🌇 *Вечер:* ").append(eveningLesson).append("\n\n");
        }

        return sb.toString();
    }

    private Map<LocalDate, Map<String, String>> loadCustomLessons() {
        Map<LocalDate, Map<String, String>> customLessons = new HashMap<>();

        try (Connection conn = getConnection()) {
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("""
                SELECT lesson_date, lesson_type, description 
                FROM lessons 
                WHERE lesson_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '7 days'
                ORDER BY lesson_date, lesson_type
            """);

            while (rs.next()) {
                LocalDate date = rs.getDate("lesson_date").toLocalDate();
                String type = rs.getString("lesson_type");
                String description = rs.getString("description");

                customLessons.computeIfAbsent(date, k -> new HashMap<>()).put(type, description);
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка загрузки кастомных занятий: " + e.getMessage());
        }

        return customLessons;
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

    private void addLesson(Long chatId, String description, LocalDate date, String lessonType) {
        try (Connection conn = getConnection()) {
            // Удаляем существующее занятие на эту дату
            PreparedStatement deleteStmt = conn.prepareStatement(
                    "DELETE FROM lessons WHERE lesson_date = ? AND lesson_type = ?"
            );
            deleteStmt.setDate(1, Date.valueOf(date));
            deleteStmt.setString(2, lessonType);
            deleteStmt.executeUpdate();

            // Добавляем новое занятие
            PreparedStatement insertStmt = conn.prepareStatement(
                    "INSERT INTO lessons (lesson_date, lesson_type, description) VALUES (?, ?, ?)"
            );
            insertStmt.setDate(1, Date.valueOf(date));
            insertStmt.setString(2, lessonType);
            insertStmt.setString(3, description);
            insertStmt.executeUpdate();

            String typeText = lessonType.equals("morning") ? "утреннюю" : "вечернюю";
            String dateText = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            String text = "✅ " + typeText + " практика на " + dateText + " добавлена!\n📝 *" + description + "*";

            SendMessage message = new SendMessage(chatId.toString(), text);
            message.setParseMode("Markdown");
            execute(message);

            showScheduleMenu(chatId);

        } catch (Exception e) {
            System.err.println("❌ Ошибка добавления занятия: " + e.getMessage());
            sendMsg(chatId, "❌ Ошибка при добавлении занятия");
        }
    }

    private void showCustomLessonsForDeletion(Long chatId, String lessonType) {
        StringBuilder sb = new StringBuilder();
        sb.append(lessonType.equals("morning") ? "🌅 Утренние занятия для удаления:\n\n" : "🌇 Вечерние занятия для удаления:\n\n");

        List<String> lessons = new ArrayList<>();

        try (Connection conn = getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id, lesson_date, description FROM lessons WHERE lesson_type = ? AND lesson_date >= CURRENT_DATE ORDER BY lesson_date"
            );
            stmt.setString(1, lessonType);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                LocalDate date = rs.getDate("lesson_date").toLocalDate();
                String description = rs.getString("description");
                long id = rs.getLong("id");

                String lessonText = date.format(DateTimeFormatter.ofPattern("dd.MM")) + " - " + description;
                lessons.add(lessonText);

                sb.append("• ").append(lessonText).append("\n");
            }
        } catch (SQLException e) {
            sendMsg(chatId, "❌ Ошибка загрузки занятий");
            return;
        }

        if (lessons.isEmpty()) {
            sb.append("Нет кастомных занятий для удаления");
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Создаем кнопки для каждого занятия
        try (Connection conn = getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id, lesson_date, description FROM lessons WHERE lesson_type = ? AND lesson_date >= CURRENT_DATE ORDER BY lesson_date LIMIT 10"
            );
            stmt.setString(1, lessonType);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                long id = rs.getLong("id");
                LocalDate date = rs.getDate("lesson_date").toLocalDate();
                String description = rs.getString("description");

                String buttonText = date.format(DateTimeFormatter.ofPattern("dd.MM")) + " - " +
                        (description.length() > 15 ? description.substring(0, 15) + "..." : description);

                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(createInlineButton(buttonText, "delete_lesson_" + id));
                rows.add(row);
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка создания кнопок удаления: " + e.getMessage());
        }

        // Кнопка назад
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        backRow.add(createInlineButton("🔙 Назад", "back_to_schedule"));
        rows.add(backRow);

        markup.setKeyboard(rows);

        SendMessage message = new SendMessage(chatId.toString(), sb.toString());
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка отправки списка занятий: " + e.getMessage());
        }
    }

    private void deleteLesson(Long chatId, String lessonId, Integer messageId) {
        try (Connection conn = getConnection()) {
            PreparedStatement stmt = conn.prepareStatement("DELETE FROM lessons WHERE id = ?");
            stmt.setLong(1, Long.parseLong(lessonId));
            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                // Обновляем сообщение
                String newText = "✅ Занятие удалено!\n\n";
                EditMessageText editMessage = new EditMessageText();
                editMessage.setChatId(chatId.toString());
                editMessage.setMessageId(messageId);
                editMessage.setText(newText);

                try {
                    execute(editMessage);
                } catch (TelegramApiException e) {
                    System.err.println("❌ Ошибка обновления сообщения: " + e.getMessage());
                }

                showScheduleMenu(chatId);
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка удаления занятия: " + e.getMessage());
            sendMsg(chatId, "❌ Ошибка при удалении занятия");
        }
    }

    private void toggleNotifications(Long chatId) {
        try (Connection conn = getConnection()) {
            // Проверяем текущее состояние уведомлений
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT notifications_enabled FROM bot_settings WHERE id = 1");

            boolean currentState = true;
            if (rs.next()) {
                currentState = rs.getBoolean("notifications_enabled");
            }

            // Переключаем состояние
            PreparedStatement updateStmt = conn.prepareStatement(
                    "INSERT INTO bot_settings (id, notifications_enabled) VALUES (1, ?) ON CONFLICT (id) DO UPDATE SET notifications_enabled = ?"
            );
            boolean newState = !currentState;
            updateStmt.setBoolean(1, newState);
            updateStmt.setBoolean(2, newState);
            updateStmt.executeUpdate();

            String status = newState ? "включены" : "отключены";
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
            // Получаем записи на завтра
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

            // Утренние записи
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

            // Вечерние записи
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

        } catch (SQLException e) {
            sb.append("❌ Ошибка загрузки записей");
        }

        sendMsg(chatId, sb.toString());
    }

    // Метод для отправки уведомлений в канал (будет вызываться по расписанию)
    public void sendDailyNotifications() {
        if (channelId == null || channelId.isEmpty()) {
            System.out.println("⚠️ Channel ID не настроен");
            return;
        }

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalTime now = LocalTime.now();

        try (Connection conn = getConnection()) {
            // Проверяем, включены ли уведомления
            Statement checkStmt = conn.createStatement();
            ResultSet settingsRs = checkStmt.executeQuery("SELECT notifications_enabled FROM bot_settings WHERE id = 1");

            if (!settingsRs.next() || !settingsRs.getBoolean("notifications_enabled")) {
                System.out.println("🔕 Уведомления отключены в настройках");
                return;
            }

            // Получаем расписание на завтра (кастомное + фиксированное)
            Map<String, String> tomorrowSchedule = getTomorrowSchedule(tomorrow);

            String morningLesson = tomorrowSchedule.get("morning");
            String eveningLesson = tomorrowSchedule.get("evening");

            // Определяем тип уведомления по времени
            if (now.getHour() == 12 && now.getMinute() == 0) { // 12:00 МСК - утреннее уведомление
                sendMorningNotification(morningLesson);
            } else if (now.getHour() == 18 && now.getMinute() == 0) { // 18:00 МСК - вечернее уведомление
                sendEveningNotification(eveningLesson);
            } else if (now.getHour() == 14 && now.getMinute() == 0) { // 14:00 МСК - уведомление об отсутствии занятий
                sendNoClassesNotification(morningLesson, eveningLesson);
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка отправки уведомлений: " + e.getMessage());
        }
    }

    private Map<String, String> getTomorrowSchedule(LocalDate tomorrow) {
        Map<String, String> schedule = new HashMap<>();
        DayOfWeek dayOfWeek = tomorrow.getDayOfWeek();

        // Сначала проверяем кастомные занятия
        try (Connection conn = getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT lesson_type, description FROM lessons WHERE lesson_date = ?"
            );
            stmt.setDate(1, Date.valueOf(tomorrow));
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                schedule.put(rs.getString("lesson_type"), rs.getString("description"));
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка загрузки кастомного расписания: " + e.getMessage());
        }

        // Если нет кастомных занятий, используем фиксированное расписание
        if (!schedule.containsKey("morning")) {
            schedule.put("morning", fixedSchedule.get(dayOfWeek).get("morning"));
        }
        if (!schedule.containsKey("evening")) {
            schedule.put("evening", fixedSchedule.get(dayOfWeek).get("evening"));
        }

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

            // Таблица для кастомных занятий
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

            // Таблица для записей пользователей
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

            // Таблица настроек бота
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS bot_settings (
                    id INTEGER PRIMARY KEY,
                    notifications_enabled BOOLEAN DEFAULT true,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Инициализируем настройки
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
}