package org.example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private final Map<Long, Boolean> subscriptions = new HashMap<>();

    @PostConstruct
    public void postConstruct() {
        System.out.println("🔄 Инициализация YogaBot...");
        System.out.println("Database URL: " + (dbUrl != null ? dbUrl.substring(0, Math.min(dbUrl.length(), 50)) + "..." : "null"));
        System.out.println("Database Username: " + dbUsername);
        System.out.println("Bot Username: " + botUsername);
        System.out.println("Bot Token: " + (botToken != null ? "***" + botToken.substring(Math.max(0, botToken.length() - 5)) : "null"));

        if (dbUrl != null && !dbUrl.isEmpty() && dbUsername != null && dbPassword != null) {
            initDb();
        } else {
            System.out.println("⚠️ Database не настроен, пропускаем инициализацию БД");
        }
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
        return botPath != null && !botPath.isEmpty() ? botPath : "/";
    }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        System.out.println("🔄 Начало обработки update: " + update.getUpdateId());

        if (update.hasMessage() && update.getMessage().hasText()) {
            Long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText();
            Long userId = update.getMessage().getFrom().getId();

            System.out.println("💬 Обработка сообщения от " + userId + ": " + text);

            switch (text) {
                case "/start" -> showMainMenu(chatId);
                case "📖 Расписание" -> showSchedule(chatId);
                case "🔔 Уведомления" -> toggleSubscription(chatId, userId);
                case "ℹ️ О нас" -> showAbout(chatId);
                case "📞 Контакты" -> showContacts(chatId);
                default -> {
                    if (text.startsWith("/")) {
                        sendMsg(chatId, "Команда не распознана. Используйте кнопки меню ниже 👇", true);
                    } else {
                        sendMsg(chatId, "Используйте кнопки меню для навигации 👇", true);
                    }
                }
            }

        } else {
            System.out.println("⚠️ Update не содержит текстового сообщения");
        }

        System.out.println("✅ Завершение обработки update: " + update.getUpdateId());
        return null;
    }

    private void showMainMenu(Long chatId) {
        String welcomeText = """
                🧘 *Добро пожаловать в YogaBot!*
                
                Я помогу вам с расписанием занятий и уведомлениями.
                
                Выберите нужный раздел:""";

        SendMessage message = new SendMessage(chatId.toString(), welcomeText);
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

        // Первый ряд
        KeyboardRow row1 = new KeyboardRow();
        row1.add("📖 Расписание");
        row1.add("🔔 Уведомления");

        // Второй ряд
        KeyboardRow row2 = new KeyboardRow();
        row2.add("ℹ️ О нас");
        row2.add("📞 Контакты");

        keyboard.add(row1);
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    private void sendMsg(Long chatId, String text) {
        sendMsg(chatId, text, true);
    }

    private void sendMsg(Long chatId, String text, boolean showKeyboard) {
        if (chatId == null) {
            System.err.println("❌ chatId is null");
            return;
        }

        SendMessage message = new SendMessage(chatId.toString(), text);

        if (showKeyboard) {
            message.setReplyMarkup(createMainMenuKeyboard());
        }

        try {
            execute(message);
            System.out.println("✅ Отправлено сообщение в чат " + chatId);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка отправки сообщения: " + e.getMessage());
        }
    }

    private void showAbout(Long chatId) {
        String aboutText = """
                🧘 *О нашей студии йоги*
                
                Мы предлагаем различные направления йоги для всех уровней подготовки:
                
                • 🕗 *Утренняя йога* - для пробуждения и заряда энергией
                • 🌙 *Вечерняя медитация* - для расслабления и снятия стресса
                • 🌿 *Хатха йога* - классические асаны для гармонии тела и духа
                
                Присоединяйтесь к нашему сообществу!""";

        SendMessage message = new SendMessage(chatId.toString(), aboutText);
        message.setParseMode("Markdown");
        message.setReplyMarkup(createMainMenuKeyboard());

        try {
            execute(message);
            System.out.println("✅ Показана информация о студии для чата " + chatId);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка отправки информации: " + e.getMessage());
        }
    }

    private void showContacts(Long chatId) {
        String contactsText = """
                📞 *Контакты*
                
                *Студия йоги "YogaSpace"*
                
                📍 *Адрес:* ул. Примерная, 123
                🕒 *Время работы:* 7:00 - 22:00
                📱 *Телефон:* +7 (999) 123-45-67
                🌐 *Сайт:* www.yogaspace.ru
                📧 *Email:* info@yogaspace.ru
                
                *Как нас найти:*
                🚇 Метро "Примерная" (5 минут пешком)
                🚍 Автобусы: 123, 456
                🚗 Есть парковка""";

        SendMessage message = new SendMessage(chatId.toString(), contactsText);
        message.setParseMode("Markdown");
        message.setReplyMarkup(createMainMenuKeyboard());

        try {
            execute(message);
            System.out.println("✅ Показаны контакты для чата " + chatId);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка отправки контактов: " + e.getMessage());
        }
    }

    private Connection getConnection() throws SQLException {
        System.out.println("🔗 Подключение к БД...");
        return DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
    }

    private void initDb() {
        System.out.println("🔄 Инициализация базы данных...");

        try (Connection conn = getConnection()) {
            Statement st = conn.createStatement();

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS lessons (
                    id BIGSERIAL PRIMARY KEY,
                    datetime TIMESTAMP NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS signups (
                    id BIGSERIAL PRIMARY KEY,
                    lesson_id BIGINT REFERENCES lessons(id) ON DELETE CASCADE,
                    username TEXT NOT NULL,
                    user_id BIGINT NOT NULL
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS subscriptions (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT UNIQUE NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Проверяем и добавляем тестовые данные
            ResultSet rs = st.executeQuery("SELECT COUNT(*) as count FROM lessons");
            rs.next();
            if (rs.getInt("count") == 0) {
                st.executeUpdate("""
                    INSERT INTO lessons (datetime, title, description) VALUES 
                    (NOW() + INTERVAL '1 day', 'Утренняя йога', 'Заряд энергией на весь день'),
                    (NOW() + INTERVAL '2 days', 'Вечерняя медитация', 'Расслабление и снятие стресса'),
                    (NOW() + INTERVAL '3 days', 'Хатха йога', 'Классические асаны для гармонии')
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
        if (dbUrl == null || dbUrl.isEmpty() || dbUsername == null || dbPassword == null) {
            sendMsg(chatId, "❌ База данных не настроена");
            return;
        }

        try (Connection conn = getConnection()) {
            PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT id FROM subscriptions WHERE user_id = ?"
            );
            checkStmt.setLong(1, userId);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                PreparedStatement deleteStmt = conn.prepareStatement(
                        "DELETE FROM subscriptions WHERE user_id = ?"
                );
                deleteStmt.setLong(1, userId);
                deleteStmt.executeUpdate();
                sendMsg(chatId, "🔕 *Уведомления отключены*\n\nВы больше не будете получать напоминания о занятиях.");
                System.out.println("✅ Пользователь " + userId + " отписался");
            } else {
                PreparedStatement insertStmt = conn.prepareStatement(
                        "INSERT INTO subscriptions (user_id) VALUES (?)"
                );
                insertStmt.setLong(1, userId);
                insertStmt.executeUpdate();
                sendMsg(chatId, "🔔 *Уведомления включены!*\n\nТеперь вы будете получать напоминания о предстоящих занятиях за 2 часа.");
                System.out.println("✅ Пользователь " + userId + " подписался");
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка переключения подписки: " + e.getMessage());
            sendMsg(chatId, "❌ Произошла ошибка при изменении подписки");
        }
    }

    private void showSchedule(Long chatId) {
        if (dbUrl == null || dbUrl.isEmpty() || dbUsername == null || dbPassword == null) {
            // Показываем тестовое расписание если БД не доступна
            showTestSchedule(chatId);
            return;
        }

        try (Connection conn = getConnection()) {
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("""
                SELECT id, datetime, title, description 
                FROM lessons 
                WHERE datetime > CURRENT_TIMESTAMP 
                ORDER BY datetime 
                LIMIT 10
            """);

            StringBuilder sb = new StringBuilder("📖 *Ближайшие занятия:*\n\n");
            int count = 0;

            while (rs.next()) {
                count++;
                sb.append("*")
                        .append(count)
                        .append("*. ")
                        .append(rs.getTimestamp("datetime").toLocalDateTime())
                        .append(" - ")
                        .append(rs.getString("title"))
                        .append("\n📝 ")
                        .append(rs.getString("description"))
                        .append("\n\n");
            }

            if (count == 0) {
                sb.append("На данный момент нет запланированных занятий.\n\nСледите за обновлениями!");
            } else {
                sb.append("🎯 *Для записи на занятие напишите нам в ответном сообщении!*");
            }

            SendMessage message = new SendMessage(chatId.toString(), sb.toString());
            message.setParseMode("Markdown");
            message.setReplyMarkup(createMainMenuKeyboard());

            execute(message);
            System.out.println("✅ Показано расписание для чата " + chatId);

        } catch (Exception e) {
            System.err.println("❌ Ошибка показа расписания: " + e.getMessage());
            showTestSchedule(chatId);
        }
    }

    private void showTestSchedule(Long chatId) {
        String testSchedule = """
                📖 *Ближайшие занятия:*
                
                *1.* Завтра, 10:00 - Утренняя йога
                📝 Заряд энергией на весь день
                
                *2.* Послезавтра, 18:00 - Вечерняя медитация  
                📝 Расслабление и снятие стресса
                
                *3.* Через 3 дня, 19:00 - Хатха йога
                📝 Классические асаны для гармонии
                
                🎯 *Для записи на занятие напишите нам в ответном сообщении!*""";

        SendMessage message = new SendMessage(chatId.toString(), testSchedule);
        message.setParseMode("Markdown");
        message.setReplyMarkup(createMainMenuKeyboard());

        try {
            execute(message);
            System.out.println("✅ Показано тестовое расписание для чата " + chatId);
        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка отправки тестового расписания: " + e.getMessage());
        }
    }

    private boolean isAdmin(Long userId) {
        return adminId != null && adminId.equals(userId.toString());
    }
}