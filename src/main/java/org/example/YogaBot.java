package org.example;

import org.example.service.DatabaseService;
import org.example.service.MessageCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.service.MessageSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class YogaBot extends TelegramWebhookBot implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(YogaBot.class);

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

    @Override
    public boolean executeDeleteMessage(DeleteMessage deleteMessage) throws TelegramApiException {
        return execute(deleteMessage);
    }

    private final DatabaseService databaseService;
    private final Map<Long, String> userStates = new HashMap<>();
    private final Map<DayOfWeek, Map<String, String>> fixedSchedule = new HashMap<>();
    private final MessageCleanupService messageCleanupService;

    public YogaBot(DatabaseService databaseService,
                   MessageCleanupService messageCleanupService) {
        this.databaseService = databaseService;
        this.messageCleanupService = messageCleanupService;
    }

    @PostConstruct
    public void postConstruct() {
        System.out.println("🔄 Инициализация YogaBot...");
        System.out.println("Admin ID: " + adminId);
        System.out.println("Channel ID: " + channelId);

        // Проверка админских ID
        if (adminId != null) {
            String[] adminIds = adminId.split(",");
            System.out.println("👨‍💼 Настроенные админы:");
            for (String id : adminIds) {
                System.out.println("   - " + id.trim());
            }
        }

        // Принудительная инициализация БД
        databaseService.initializeDatabase();

        initializeFixedSchedule();
        System.out.println("✅ YogaBot инициализирован");
    }

    private void initializeFixedSchedule() {
        System.out.println("🔄 Инициализация расписания...");

        // Сначала инициализируем дефолтное расписание в БД
        databaseService.initializeDefaultSchedule();

        // Затем загружаем из БД
        Map<DayOfWeek, Map<String, String>> savedSchedule = databaseService.loadSchedule();

        System.out.println("📊 Результат загрузки из БД: " + (savedSchedule != null ? savedSchedule.size() : "null") + " дней");

        if (savedSchedule != null && !savedSchedule.isEmpty()) {
            fixedSchedule.putAll(savedSchedule);
            System.out.println("✅ Расписание загружено из БД: " + savedSchedule.size() + " дней");

            // Отладочная информация
            for (Map.Entry<DayOfWeek, Map<String, String>> entry : savedSchedule.entrySet()) {
                System.out.println("   - " + entry.getKey() + ": " + entry.getValue());
            }
        } else {
            // Резервная инициализация
            System.out.println("⚠️ Используется резервное расписание");
            initializeBackupSchedule();
        }

        System.out.println("📋 Итоговый fixedSchedule: " + fixedSchedule.size() + " дней");
        System.out.println("✅ Расписание инициализировано");
    }

    private void initializeBackupSchedule() {
        // Понедельник
        Map<String, String> monday = new HashMap<>();
        monday.put("morning", "8:00 - 11:30 - Майсор класс");
        monday.put("evening", "17:00 - 20:30 - Майсор класс");
        fixedSchedule.put(DayOfWeek.MONDAY, monday);

        // Вторник
        Map<String, String> tuesday = new HashMap<>();
        tuesday.put("morning", "8:00 - 11:30 - Майсор класс");
        tuesday.put("evening", "18:30 - 20:00 - Майсор класс");
        fixedSchedule.put(DayOfWeek.TUESDAY, tuesday);

        // Среда
        Map<String, String> wednesday = new HashMap<>();
        wednesday.put("morning", "8:00 - 11:30 - Майсор класс");
        wednesday.put("evening", "17:00 - 20:30 - Майсор класс");
        fixedSchedule.put(DayOfWeek.WEDNESDAY, wednesday);

        // Четверг
        Map<String, String> thursday = new HashMap<>();
        thursday.put("morning", "8:00 - 11:30 - Майсор класс");
        thursday.put("evening", "17:00 - 20:30 - Майсор класс");
        fixedSchedule.put(DayOfWeek.THURSDAY, thursday);

        // Пятница
        Map<String, String> friday = new HashMap<>();
        friday.put("morning", "8:00 - 11:30 - Майсор класс");
        friday.put("evening", "17:00 - 20:30 - Майсор класс");
        fixedSchedule.put(DayOfWeek.FRIDAY, friday);

        // Суббота
        Map<String, String> saturday = new HashMap<>();
        saturday.put("morning", "ОТДЫХ");
        saturday.put("evening", "ОТДЫХ");
        fixedSchedule.put(DayOfWeek.SATURDAY, saturday);

        // Воскресенье
        Map<String, String> sunday = new HashMap<>();
        sunday.put("morning", "10:00 - 11:30 LED-КЛАСС");
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
        System.out.println("👤 Пользователь " + userId + " является админом: " + isAdminUser);

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
        switch (text) {
            case "/start" -> {
                System.out.println("🚀 Команда /start от пользователя " + userId + " (admin: " + isAdminUser + ")");
                showMainMenu(chatId, isAdminUser);
            }
            case "📅 Расписание" -> {
                System.out.println("📅 Пользователь запросил расписание (admin: " + isAdminUser + ")");
                if (isAdminUser) {
                    System.out.println("👨‍💼 Это админ, показываем меню управления расписанием");
                    showScheduleMenu(chatId);
                } else {
                    System.out.println("👤 Это обычный пользователь, показываем простое расписание");
                    showScheduleForUsers(chatId);
                }
            }
            case "🕒 Проверить время" -> checkAndSendTime(chatId);
            default -> {
                if (isAdminUser) {
                    System.out.println("👨‍💼 Админская команда: " + text);
                    handleAdminMessage(chatId, text, userId);
                } else {
                    sendMsg(chatId, "❌ Команда не найдена. Используйте кнопки меню.");
                }
            }
        }
    }

    private void checkAndSendTime(Long chatId) {
        checkServerTime();

        LocalDateTime utcTime = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime moscowTime = LocalDateTime.now(ZoneId.of("Europe/Moscow"));

        String timeInfo = "🕒 *Информация о времени:*\n\n" +
                "Сервер (UTC): " + utcTime + "\n" +
                "Москва (UTC+3): " + moscowTime + "\n" +
                "Час сервера: " + utcTime.getHour() + "\n" +
                "Час Москвы: " + moscowTime.getHour();

        sendMsg(chatId, timeInfo);
    }

    public void checkServerTime() {
        LocalDateTime utcTime = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime moscowTime = LocalDateTime.now(ZoneId.of("Europe/Moscow"));

        System.out.println("🕒 Текущее время сервера (UTC): " + utcTime);
        System.out.println("🕒 Текущее время Moscow (UTC+3): " + moscowTime);
        System.out.println("🕒 Текущий час (UTC): " + utcTime.getHour());
        System.out.println("🕒 Текущий час (Moscow): " + moscowTime.getHour());
        System.out.println("🕒 Текущая дата Moscow: " + moscowTime.toLocalDate());
    }

    private LocalDate getMoscowDate() {
        return LocalDate.now(ZoneId.of("Europe/Moscow"));
    }

    private void handleCallbackQuery(org.telegram.telegrambots.meta.api.objects.CallbackQuery callbackQuery, boolean isAdminUser) {
        Long chatId = callbackQuery.getMessage().getChatId();
        Long userId = callbackQuery.getFrom().getId();
        String data = callbackQuery.getData();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        System.out.println("🔘 Обработка callback: " + data + " от пользователя " + userId + " (admin: " + isAdminUser + ")");

        // Проверяем права админа для функций изменения расписания
        if (data.startsWith("schedule_") || data.startsWith("day_") || data.startsWith("edit_") ||
                data.startsWith("delete_") || data.equals("back_to_schedule") || data.equals("back_to_main")) {

            if (!isAdminUser) {
                System.out.println("⛔ Попытка доступа к админским функциям без прав: " + userId);
                answerCallbackQuery(callbackQuery.getId(), "❌ Эта функция доступна только администраторам");
                return;
            }
        }

        // Обработка callback'ов
        switch (data) {
            case "schedule_morning" -> {
                if (isAdminUser) {
                    showDaySelection(chatId, "morning");
                } else {
                    answerCallbackQuery(callbackQuery.getId(), "❌ Недостаточно прав");
                }
            }
            case "schedule_evening" -> {
                if (isAdminUser) {
                    showDaySelection(chatId, "evening");
                } else {
                    answerCallbackQuery(callbackQuery.getId(), "❌ Недостаточно прав");
                }
            }
            case "back_to_schedule" -> {
                if (isAdminUser) {
                    showScheduleMenu(chatId);
                } else {
                    answerCallbackQuery(callbackQuery.getId(), "❌ Недостаточно прав");
                }
            }
            case "back_to_main" -> showMainMenu(chatId, isAdminUser);
            default -> {
                if (data.startsWith("day_")) {
                    if (isAdminUser) {
                        handleDaySelection(chatId, data);
                    } else {
                        answerCallbackQuery(callbackQuery.getId(), "❌ Недостаточно прав");
                    }
                } else if (data.startsWith("edit_")) {
                    if (isAdminUser) {
                        handleEditLesson(chatId, data);
                    } else {
                        answerCallbackQuery(callbackQuery.getId(), "❌ Недостаточно прав");
                    }
                } else if (data.startsWith("delete_")) {
                    if (isAdminUser) {
                        handleDeleteLesson(chatId, data, messageId);
                    } else {
                        answerCallbackQuery(callbackQuery.getId(), "❌ Недостаточно прав");
                    }
                } else if (data.startsWith("signup_")) {
                    handleUserSignup(callbackQuery);
                } else if (data.startsWith("cancel_")) {
                    handleUserCancel(callbackQuery);
                } else {
                    System.out.println("⛔ Неизвестный callback: " + data);
                    answerCallbackQuery(callbackQuery.getId(), "❌ Неизвестная команда");
                }
            }
        }
    }

    private void showRegistrationsForDate(Long chatId, LocalDate date) {
        try {
            Map<String, List<String>> registrations = databaseService.getRegistrationsForDate(date);

            StringBuilder sb = new StringBuilder();
            sb.append("📋 *Записи на ").append(date.format(DateTimeFormatter.ofPattern("dd.MM"))).append("*\n\n");

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

        } catch (Exception e) {
            System.err.println("❌ Ошибка получения записей: " + e.getMessage());
            sendMsg(chatId, "❌ Ошибка при загрузке записей");
        }
    }

    private void handleAdminMessage(Long chatId, String text, Long userId) {
        System.out.println("👨‍💼 Обработка админской команды: " + text);

        switch (text) {
            case "📅 Расписание" -> {
                System.out.println("📅 Админ запросил меню расписания - ВЫЗЫВАЕМ showScheduleMenu");
                showScheduleMenu(chatId);
            }
            case "🔔 Уведомления" -> {
                System.out.println("🔔 Админ переключает уведомления");
                toggleNotifications(chatId);
            }
            case "📋 Записи на сегодня" -> {
                System.out.println("📋 Админ запросил записи на сегодня");
                showTodayRegistrations(chatId);
            }
            case "📋 Записи на завтра" -> {
                System.out.println("📋 Админ запросил записи на завтра");
                showRegistrationsForDate(chatId, getMoscowDate().plusDays(1));
            }
            case "🔔 Отбивка на сегодня" -> {
                System.out.println("🔔 Админ отправляет отбивку на сегодня");
                sendTodayNotification();
                sendMsg(chatId, "✅ Отбивка на сегодня отправлена в канал!");
            }
            case "🔔 Отбивка на завтра" -> {
                System.out.println("🔔 Админ отправляет отбивку на завтра");
                sendTestNotification();
                sendMsg(chatId, "✅ Отбивка на завтра отправлена в канал!");
            }
            case "🕒 Проверить время" -> {
                System.out.println("🕒 Админ проверяет время");
                checkAndSendTime(chatId);
            }
            case "🚫 Отмена" -> {
                System.out.println("🚫 Админ отменяет действие");
                userStates.remove(userId);
                showMainMenu(chatId, true);
            }
            default -> {
                System.out.println("📝 Админ вводит текст: " + text);
                handleState(chatId, text, userId);
            }
        }
    }

    private String getActionDescription(String action) {
        switch (action) {
            case "CHANGE_SCHEDULE": return "Изменение расписания";
            case "TOGGLE_NOTIFICATIONS": return "Переключение уведомлений";
            case "SEND_TEST_NOTIFICATION": return "Тест уведомлений";
            default: return action;
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

        KeyboardRow row2 = new KeyboardRow();
        row2.add("🕒 Проверить время");

        keyboard.add(row1);
        keyboard.add(row2);

        // Кнопки только для админов
        if (isAdminUser) {
            System.out.println("👨‍💼 Создаем админские кнопки для пользователя");

            KeyboardRow adminRow1 = new KeyboardRow();
            adminRow1.add("🔔 Уведомления");

            KeyboardRow adminRow2 = new KeyboardRow();
            adminRow2.add("📋 Записи на сегодня");
            adminRow2.add("📋 Записи на завтра");

            KeyboardRow adminRow3 = new KeyboardRow();
            adminRow3.add("🔔 Отбивка на сегодня");
            adminRow3.add("🔔 Отбивка на завтра");

            keyboard.add(adminRow1);
            keyboard.add(adminRow2);
            keyboard.add(adminRow3);
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
    
    private void showScheduleMenu(Long chatId) {
        System.out.println("🎯 НАЧАЛО showScheduleMenu для чата " + chatId);

        try {
            System.out.println("🔄 Получаем расписание...");
            String scheduleText = getWeeklySchedule();
            System.out.println("✅ Расписание получено, длина: " + scheduleText.length());

            String text = "📅 *Расписание на неделю:*\n\n" + scheduleText + "\n\nВыберите раздел для управления:";

            System.out.println("🔧 Создаем inline-кнопки...");

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            List<InlineKeyboardButton> row1 = new ArrayList<>();
            row1.add(createInlineButton("🌅 Утренние занятия", "schedule_morning"));
            row1.add(createInlineButton("🌇 Вечерние занятия", "schedule_evening"));

            List<InlineKeyboardButton> row2 = new ArrayList<>();
            row2.add(createInlineButton("🔙 Назад в меню", "back_to_main"));

            rows.add(row1);
            rows.add(row2);
            markup.setKeyboard(rows);

            System.out.println("✅ Кнопки созданы, отправляем сообщение...");

            SendMessage message = new SendMessage(chatId.toString(), text);
            message.setParseMode("Markdown");
            message.setReplyMarkup(markup);

            System.out.println("🚀 Отправляем сообщение с inline-кнопками...");
            execute(message);
            System.out.println("✅ Меню расписания УСПЕШНО отправлено для чата " + chatId);

        } catch (Exception e) {
            System.err.println("❌ КРИТИЧЕСКАЯ ОШИБКА в showScheduleMenu: " + e.getMessage());
            e.printStackTrace();

            try {
                sendMsg(chatId, "❌ Ошибка при загрузке расписания: " + e.getMessage());
            } catch (Exception ex) {
                System.err.println("❌ Не удалось отправить сообщение об ошибке: " + ex.getMessage());
            }
        }

        System.out.println("🎯 КОНЕЦ showScheduleMenu");
    }

    private String getWeeklySchedule() {
        System.out.println("🔄 Вызов getWeeklySchedule()");
        System.out.println("📊 fixedSchedule size: " + fixedSchedule.size());

        try {
            StringBuilder sb = new StringBuilder();
            LocalDate today = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE (dd.MM)");

            for (int i = 0; i < 7; i++) {
                LocalDate date = today.plusDays(i);
                DayOfWeek dayOfWeek = date.getDayOfWeek();

                System.out.println("📅 Обрабатываем день: " + dayOfWeek);

                // Проверяем, есть ли расписание для этого дня
                if (!fixedSchedule.containsKey(dayOfWeek)) {
                    System.out.println("⚠️ Нет расписания для дня: " + dayOfWeek);
                    continue;
                }

                String dayName = date.format(formatter);
                dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1);

                sb.append("📅 *").append(dayName).append("*\n");

                String morningLesson = fixedSchedule.get(dayOfWeek).get("morning");
                String eveningLesson = fixedSchedule.get(dayOfWeek).get("evening");

                // Проверяем на null
                morningLesson = morningLesson != null ? morningLesson : "Не указано";
                eveningLesson = eveningLesson != null ? eveningLesson : "Не указано";

                sb.append("🌅 *Утро:* ").append(morningLesson).append("\n");
                sb.append("🌇 *Вечер:* ").append(eveningLesson).append("\n\n");

                System.out.println("   - Утро: " + morningLesson);
                System.out.println("   - Вечер: " + eveningLesson);
            }

            String result = sb.toString();
            System.out.println("✅ getWeeklySchedule успешно завершен, длина: " + result.length());
            return result;

        } catch (Exception e) {
            System.err.println("❌ Ошибка в getWeeklySchedule: " + e.getMessage());
            e.printStackTrace();
            return "⚠️ Ошибка загрузки расписания. Попробуйте позже.";
        }
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
            System.out.println("✅ Показан выбор дней для " + lessonType + " для админа " + chatId);
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

        // Кнопки действий - ВОТ ОНИ!
        List<InlineKeyboardButton> actionRow = new ArrayList<>();
        actionRow.add(createInlineButton("✏️ Изменить", "edit_" + dayOfWeek + "_" + lessonType));
        actionRow.add(createInlineButton("🗑️ Удалить", "delete_" + dayOfWeek + "_" + lessonType));

        // Кнопка назад
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
            System.out.println("✅ Показаны действия для дня " + dayName + " " + lessonType + " для админа " + chatId);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка отправки действий для дня: " + e.getMessage());
        }
    }

    private void handleEditLesson(Long chatId, String data) {
        // Дополнительная проверка прав
        if (!isAdmin(chatId)) {
            sendMsg(chatId, "❌ Недостаточно прав для изменения расписания");
            return;
        }

        // data format: "edit_MONDAY_morning"
        String[] parts = data.split("_");
        DayOfWeek dayOfWeek = DayOfWeek.valueOf(parts[1]);
        String lessonType = parts[2];

        String dayName = getRussianDayNameFull(dayOfWeek);
        String typeText = lessonType.equals("morning") ? "утреннего" : "вечернего";
        String currentSchedule = fixedSchedule.get(dayOfWeek).get(lessonType);

        // Сохраняем состояние для обработки ввода
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
        // Дополнительная проверка прав
        if (!isAdmin(chatId)) {
            answerCallbackQuery(data, "❌ Недостаточно прав для удаления занятий");
            return;
        }

        String[] parts = data.split("_");
        DayOfWeek dayOfWeek = DayOfWeek.valueOf(parts[1]);
        String lessonType = parts[2];

        String dayName = getRussianDayNameFull(dayOfWeek);
        String typeText = lessonType.equals("morning") ? "утреннее" : "вечернее";

        String deletedSchedule = "Отдых";
        fixedSchedule.get(dayOfWeek).put(lessonType, deletedSchedule);

        // СОХРАНЯЕМ ИЗМЕНЕНИЕ В БАЗУ ДАННЫХ
        databaseService.saveSchedule(dayOfWeek, lessonType, deletedSchedule, chatId);

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
        // Проверка прав перед сохранением в БД
        if (!isAdmin(chatId)) {
            sendMsg(chatId, "❌ Недостаточно прав для сохранения изменений");
            userStates.remove(chatId);
            return;
        }

        String dayName = getRussianDayNameFull(dayOfWeek);
        String typeText = lessonType.equals("morning") ? "утреннее" : "вечернее";

        // Обновляем в памяти
        fixedSchedule.get(dayOfWeek).put(lessonType, newSchedule);

        // СОХРАНЯЕМ В БАЗУ ДАННЫХ с логированием
        databaseService.saveSchedule(dayOfWeek, lessonType, newSchedule, chatId);

        String text = "✅ *" + typeText + " занятие на " + dayName + " обновлено!*\n\n";
        text += "📝 *Новое расписание:*\n" + newSchedule + "\n\n";
        text += "Изменения сохранены в базе данных и отразятся в уведомлениях.";

        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setParseMode("Markdown");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка отправки подтверждения: " + e.getMessage());
        }

        // Возвращаем к выбору дня
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
                "🔔 *Уведомления включены!*\n\nАвтоматические уведомления будут отправляться в канал:\n• Утренние - в 16:00 МСК\n• Вечерние - в 16:01 МСК\n• Отсутствие занятий - в 16:05 МСК" :
                "🔕 *Уведомления отключены!*\n\nАвтоматические уведомления не будут отправляться в канал.";

        sendMsg(chatId, text);
    }

    public Map<String, String> getScheduleForDate(LocalDate date) {
        Map<String, String> schedule = new HashMap<>();
        DayOfWeek dayOfWeek = date.getDayOfWeek();

        if (fixedSchedule.containsKey(dayOfWeek)) {
            schedule.put("morning", fixedSchedule.get(dayOfWeek).get("morning"));
            schedule.put("evening", fixedSchedule.get(dayOfWeek).get("evening"));
        } else {
            schedule.put("morning", "Не указано");
            schedule.put("evening", "Не указано");
        }

        return schedule;
    }

    private void showTodayRegistrations(Long chatId) {
        LocalDate today = getMoscowDate();
        System.out.println("🔍 Запрос записей на сегодня: " + today);

        Map<String, List<String>> registrations = databaseService.getRegistrationsForDate(today);
        System.out.println("🔍 Найдено записей: утро=" + registrations.get("morning").size() + ", вечер=" + registrations.get("evening").size());

        StringBuilder sb = new StringBuilder();
        sb.append("📋 *Записи на сегодня (").append(today.format(DateTimeFormatter.ofPattern("dd.MM"))).append(")*\n\n");

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

    public void sendTodayMorningNotification() {
        LocalDate today = getMoscowDate();
        Map<String, String> todaySchedule = getScheduleForDate(today); // ИСПРАВЛЕНО
        String morningLesson = todaySchedule.get("morning");

        if (morningLesson == null || morningLesson.equals("ОТДЫХ") || morningLesson.equals("Отдых")) {
            sendToChannel("🌅 На сегодня утренних занятий нет");
            return;
        }

        String text = "🌅 *Сегодняшняя утренняя практика:*\n\n" + morningLesson + "\n\n";
        text += "📍 *Место:* Yoga Shala\n\n";
        text += "Записаться⤵️";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createInlineButton("✅ Записаться", "signup_morning_" + today));
        row.add(createInlineButton("❌ Отменить запись", "cancel_morning_" + today));
        markup.setKeyboard(List.of(row));

        sendToChannel(text, markup);
    }

    public void sendTodayEveningNotification() {
        LocalDate today = getMoscowDate();
        Map<String, String> todaySchedule = getScheduleForDate(today); // ИСПРАВЛЕНО
        String eveningLesson = todaySchedule.get("evening");

        if (eveningLesson == null || eveningLesson.equals("ОТДЫХ") || eveningLesson.equals("Отдых")) {
            sendToChannel("🌇 На сегодня вечерних занятий нет");
            return;
        }

        // Определяем место проведения для вторника
        String location = "Yoga Shala";
        if (today.getDayOfWeek() == DayOfWeek.TUESDAY) {
            location = "Аргуновский";
        }

        String text = "🌇 *Сегодняшняя вечерняя практика:*\n\n" + eveningLesson + "\n\n";
        text += "📍 *Место:* " + location + "\n\n";
        text += "Записаться⤵️";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createInlineButton("✅ Записаться", "signup_evening_" + today));
        row.add(createInlineButton("❌ Отменить запись", "cancel_evening_" + today));
        markup.setKeyboard(List.of(row));

        sendToChannel(text, markup);
    }

    public void sendTodayNotification() {
        System.out.println("🔔 Отправка уведомлений на сегодня...");

        LocalDate today = getMoscowDate();
        Map<String, String> todaySchedule = getScheduleForDate(today); // ИСПРАВЛЕНО
        String morningLesson = todaySchedule.get("morning");
        String eveningLesson = todaySchedule.get("evening");

        boolean hasMorning = morningLesson != null && !morningLesson.equals("ОТДЫХ") && !morningLesson.equals("Отдых");
        boolean hasEvening = eveningLesson != null && !eveningLesson.equals("ОТДЫХ") && !eveningLesson.equals("Отдых");

        System.out.println("📊 На сегодня: утро=" + hasMorning + ", вечер=" + hasEvening);

        if (hasMorning) {
            sendTodayMorningNotification();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (hasEvening) {
            sendTodayEveningNotification();
        }

        if (!hasMorning && !hasEvening) {
            sendToChannel("📝 На сегодня занятий нет! Отдыхаем и восстанавливаемся! 💫");
        }
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
            sendMsg(chatId, "✅ Тестовые уведомления отправлены в канал! Проверьте @Katys_yoga");
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

    public Map<String, String> getTomorrowSchedule(LocalDate tomorrow) {
        Map<String, String> schedule = new HashMap<>();
        DayOfWeek dayOfWeek = tomorrow.getDayOfWeek();

        schedule.put("morning", fixedSchedule.get(dayOfWeek).get("morning"));
        schedule.put("evening", fixedSchedule.get(dayOfWeek).get("evening"));

        return schedule;
    }

    public void sendMorningNotification(String morningLesson) {
        if (morningLesson == null || morningLesson.equals("ОТДЫХ") || morningLesson.equals("Отдых")) {
            String text = "🌅 На завтра утренних занятий нет";
            sendToChannel(text);
            return;
        }

        LocalDate tomorrow = getMoscowDate().plusDays(1);
        String text = "🌅 *Завтрашняя утренняя практика:*\n\n" + morningLesson + "\n\n";
        text += "📍 *Место:* Yoga Shala\n\n"; // ВСЕГДА Yoga Shala для утренних занятий
        text += "Записаться⤵️";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createInlineButton("✅ Записаться", "signup_morning_" + tomorrow));
        row.add(createInlineButton("❌ Отменить запись", "cancel_morning_" + tomorrow));
        markup.setKeyboard(List.of(row));

        sendToChannel(text, markup);
    }

    public void sendEveningNotification(String eveningLesson) {
        if (eveningLesson == null || eveningLesson.equals("ОТДЫХ") || eveningLesson.equals("Отдых")) {
            String text = "🌇 На завтра вечерних занятий нет";
            sendToChannel(text);
            return;
        }

        LocalDate tomorrow = getMoscowDate().plusDays(1);

        // Определяем место проведения: ТОЛЬКО для вечернего занятия во вторник - "Аргуновский"
        String location = "Yoga Shala"; // по умолчанию
        if (tomorrow.getDayOfWeek() == DayOfWeek.TUESDAY) {
            location = "Аргуновский";
        }

        String text = "🌇 *Завтрашняя вечерняя практика:*\n\n" + eveningLesson + "\n\n";
        text += "📍 *Место:* " + location + "\n\n";
        text += "Записаться⤵️";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createInlineButton("✅ Записаться", "signup_evening_" + tomorrow));
        row.add(createInlineButton("❌ Отменить запись", "cancel_evening_" + tomorrow));
        markup.setKeyboard(List.of(row));

        sendToChannel(text, markup);
    }

    public void sendNoClassesNotification(String morningLesson, String eveningLesson) {
        boolean hasMorning = morningLesson != null && !morningLesson.equals("ОТДЫХ") && !morningLesson.equals("Отдых");
        boolean hasEvening = eveningLesson != null && !eveningLesson.equals("ОТДЫХ") && !eveningLesson.equals("Отдых");

        if (!hasMorning && !hasEvening) {
            String text = "Ура, завтра занятий нет! Отдыхаем и восстанавливаемся! 💫";
            sendToChannel(text);
        } else if (!hasMorning) {
            sendToChannel("🌅 На завтра утренних занятий нет");
        } else if (!hasEvening) {
            sendToChannel("🌇 На завтра вечерних занятий нет");
        } else {
            System.out.println("✅ Оба занятия есть, уведомление не требуется");
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
            org.telegram.telegrambots.meta.api.objects.Message sentMessage = execute(message);
            System.out.println("✅ Уведомление отправлено в канал, ID: " + sentMessage.getMessageId());

            // Сохраняем ID сообщения для последующего удаления
            saveMessageInfo(sentMessage, text);

        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка отправки в канал: " + e.getMessage());
        }
    }

    private void saveMessageInfo(org.telegram.telegrambots.meta.api.objects.Message sentMessage, String text) {
        try {
            log.info("💾 Начало сохранения сообщения ID: {}", sentMessage.getMessageId());

            if (messageCleanupService == null) {
                log.error("❌ MessageCleanupService is NULL!");
                return;
            }

            String lessonType = "unknown";
            LocalDate lessonDate = LocalDate.now().plusDays(1);

            // Логируем текст для отладки
            log.info("📝 Текст сообщения для анализа: {}", text.substring(0, Math.min(100, text.length())));

            // Определяем тип занятия
            if (text.contains("утренняя") || text.contains("Утренняя") || text.contains("🌅") ||
                    text.contains("утренних") || text.contains("Утренних") || text.contains("Утренняя")) {
                lessonType = "morning";
                log.info("🔍 Определен тип: morning");
            } else if (text.contains("вечерняя") || text.contains("Вечерняя") || text.contains("🌇") ||
                    text.contains("вечерних") || text.contains("Вечерних") || text.contains("Вечерняя")) {
                lessonType = "evening";
                log.info("🔍 Определен тип: evening");
            } else if (text.contains("занятий нет") || text.contains("Отдыхаем") || text.contains("отдыхаем") ||
                    text.contains("нет занятий")) {
                lessonType = "no_classes";
                log.info("🔍 Определен тип: no_classes");
            } else {
                log.warn("⚠️ Не удалось определить тип занятия для текста: {}", text.substring(0, Math.min(50, text.length())));
            }

            // Определяем дату
            if (text.contains("завтра") || text.contains("Завтра")) {
                lessonDate = LocalDate.now().plusDays(1);
                log.info("📅 Дата: завтра ({})", lessonDate);
            } else if (text.contains("сегодня") || text.contains("Сегодня")) {
                lessonDate = LocalDate.now();
                log.info("📅 Дата: сегодня ({})", lessonDate);
            } else {
                log.info("📅 Дата по умолчанию: завтра ({})", lessonDate);
            }

            log.info("💾 Сохранение: messageId={}, type={}, date={}",
                    sentMessage.getMessageId(), lessonType, lessonDate);

            messageCleanupService.saveMessageId(sentMessage.getMessageId(), lessonType, lessonDate);
            log.info("✅ Сообщение успешно сохранено в БД");

        } catch (Exception e) {
            log.error("❌ Ошибка сохранения информации о сообщении", e);
        }
    }

    private void handleUserSignup(org.telegram.telegrambots.meta.api.objects.CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long userId = callbackQuery.getFrom().getId();
        String username = callbackQuery.getFrom().getUserName();
        String firstName = callbackQuery.getFrom().getFirstName();

        String displayName = username != null ? "@" + username : firstName;

        System.out.println("🔘 Обработка записи: " + data);

        // Парсим данные: "signup_morning_2025-10-13"
        String[] parts = data.split("_");
        if (parts.length < 3) {
            System.out.println("❌ Неверный формат callback данных: " + data);
            answerCallbackQuery(callbackQuery.getId(), "❌ Ошибка формата данных");
            return;
        }

        String lessonType = parts[1];
        LocalDate lessonDate = LocalDate.parse(parts[2]);

        System.out.println("📅 Дата занятия: " + lessonDate + ", тип: " + lessonType);
        System.out.println("📅 Сегодня в Москве: " + getMoscowDate());

        // Проверяем, что дата не прошедшая (по московскому времени)
        if (lessonDate.isBefore(getMoscowDate())) {
            System.out.println("❌ Попытка записи на прошедшее занятие: " + lessonDate);
            answerCallbackQuery(callbackQuery.getId(), "❌ Нельзя записаться на прошедшее занятие!");
            return;
        }

        boolean success = databaseService.registerUser(userId, username, displayName, lessonDate, lessonType);

        String answer = success ?
                "✅ Вы записаны на " + (lessonType.equals("morning") ? "утреннюю" : "вечернюю") + " практику!" :
                "❌ Вы уже записаны на это занятие!";

        answerCallbackQuery(callbackQuery.getId(), answer);
    }

    private void handleUserCancel(org.telegram.telegrambots.meta.api.objects.CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long userId = callbackQuery.getFrom().getId();

        System.out.println("🔘 Обработка отмены: " + data);

        // Парсим данные: "cancel_evening_2025-10-13"
        String[] parts = data.split("_");
        if (parts.length < 3) {
            System.out.println("❌ Неверный формат callback данных: " + data);
            answerCallbackQuery(callbackQuery.getId(), "❌ Ошибка формата данных");
            return;
        }

        String lessonType = parts[1];
        LocalDate lessonDate = LocalDate.parse(parts[2]);

        System.out.println("📅 Дата занятия для отмены: " + lessonDate + ", тип: " + lessonType);
        System.out.println("📅 Сегодня в Москве: " + getMoscowDate());

        // Проверяем, что дата не прошедшая (по московскому времени)
        if (lessonDate.isBefore(getMoscowDate())) {
            System.out.println("❌ Попытка отмены прошедшего занятия: " + lessonDate);
            answerCallbackQuery(callbackQuery.getId(), "❌ Нельзя отменить запись на прошедшее занятие!");
            return;
        }

        boolean success = databaseService.cancelRegistration(userId, lessonDate, lessonType);

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
        if (adminId == null || adminId.isEmpty()) {
            System.out.println("⚠️ Admin ID не настроен");
            return false;
        }

        // Поддерживаем как старый формат (один ID), так и новый (список ID через запятую)
        String[] adminIds = adminId.split(",");
        for (String id : adminIds) {
            if (id.trim().equals(userId.toString())) {
                System.out.println("✅ Пользователь " + userId + " является админом (ID: " + id.trim() + ")");
                return true;
            }
        }

        System.out.println("❌ Пользователь " + userId + " НЕ является админом. Настроенные админы: " + Arrays.toString(adminIds));
        return false;
    }

    // Публичный метод для тестирования
    public void testSaveMessageInfo(org.telegram.telegrambots.meta.api.objects.Message message, String text) {
        saveMessageInfo(message, text);
    }
}