package org.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.example.service.MessageCleanupService;
import org.example.service.SupabaseService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/")
public class WebhookController {

    private final YogaBot bot;
    private final PingService pingService;
    private final SupabaseService supabaseService;
    private final MessageCleanupService messageCleanupService;
    private final JdbcTemplate jdbcTemplate;

    public WebhookController(YogaBot bot, PingService pingService,
                             SupabaseService supabaseService,
                             MessageCleanupService messageCleanupService,
                             JdbcTemplate jdbcTemplate) {
        this.bot = bot;
        this.pingService = pingService;
        this.supabaseService = supabaseService;
        this.messageCleanupService = messageCleanupService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping
    public ResponseEntity<Void> onUpdateReceived(@RequestBody Update update) {
        System.out.println("🌐 Получен webhook запрос, update_id: " + update.getUpdateId());

        // Передаем Update в бота для обработки
        bot.onWebhookUpdateReceived(update);

        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("YogaBot is running! 🤖");
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("YogaBot is healthy! 🏥");
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "OK");
        status.put("timestamp", LocalDateTime.now().toString());
        status.put("service", "YogaBot");
        status.put("version", "1.0");

        if (pingService != null) {
            status.put("lastPing", pingService.getLastSuccessfulPing() != null ?
                    pingService.getLastSuccessfulPing().toString() : "N/A");
        }

        return ResponseEntity.ok(status);
    }

    // Тестовые эндпоинты для уведомлений
    @GetMapping("/test-notification")
    public ResponseEntity<String> testNotification() {
        try {
            bot.sendTestNotification();
            return ResponseEntity.ok("""
                🧪 Тестовые уведомления отправлены!
                
                Проверьте канал: @Katys_yoga
                
                Должны прийти:
                • 🌅 Утреннее уведомление
                • 🌇 Вечернее уведомление  
                
                Если не приходят - проверьте права бота в канале!
                """);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка: " + e.getMessage());
        }
    }

    @GetMapping("/send-today")
    public ResponseEntity<String> sendTodayNotification() {
        try {
            bot.sendTodayNotification();
            return ResponseEntity.ok("""
            🔔 Уведомления на сегодня отправлены в канал!
            
            Проверьте канал: @yoga_yollayo11
            
            Должны прийти уведомления на СЕГОДНЯШНИЕ занятия с кнопками записи.
            """);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка: " + e.getMessage());
        }
    }

    @GetMapping("/send-today-morning")
    public ResponseEntity<String> sendTodayMorning() {
        try {
            bot.sendTodayMorningNotification();
            return ResponseEntity.ok("🌅 Уведомление на сегодняшнее утреннее занятие отправлено!");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка: " + e.getMessage());
        }
    }

    @GetMapping("/send-today-evening")
    public ResponseEntity<String> sendTodayEvening() {
        try {
            bot.sendTodayEveningNotification();
            return ResponseEntity.ok("🌇 Уведомление на сегодняшнее вечернее занятие отправлено!");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка: " + e.getMessage());
        }
    }

    @GetMapping("/test-morning")
    public ResponseEntity<String> testMorning() {
        try {
            bot.sendManualNotification("morning");
            return ResponseEntity.ok("🌅 Тестовое утреннее уведомление отправлено! Проверьте канал @Katys_yoga");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка: " + e.getMessage());
        }
    }

    @GetMapping("/test-evening")
    public ResponseEntity<String> testEvening() {
        try {
            bot.sendManualNotification("evening");
            return ResponseEntity.ok("🌇 Тестовое вечернее уведомление отправлено! Проверьте канал @Katys_yoga");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка: " + e.getMessage());
        }
    }

    @GetMapping("/test-no-classes")
    public ResponseEntity<String> testNoClasses() {
        try {
            bot.sendManualNotification("no_classes");
            return ResponseEntity.ok("📝 Тестовое уведомление об отсутствии занятий отправлено! Проверьте канал @Katys_yoga");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка: " + e.getMessage());
        }
    }

    // 🔧 ТЕСТОВЫЕ ЭНДПОИНТЫ ДЛЯ ПРОВЕРКИ УДАЛЕНИЯ ОТБИВОК

    @GetMapping("/reinit-db")
    public ResponseEntity<String> reinitDatabase() {
        try {
            // Принудительно пересоздаем таблицы через SupabaseService
            supabaseService.initializeDatabase();

            return ResponseEntity.ok("""
                ✅ База данных переинициализирована!
                
                Выполнено:
                • Инициализация расписания в Supabase
                """);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка переинициализации БД: " + e.getMessage());
        }
    }

    @GetMapping("/test-delete")
    public ResponseEntity<String> testDelete() {
        try {
            LocalDate testDate = LocalDate.now().minusDays(1);

            // Тестируем удаление вчерашних сообщений
            messageCleanupService.deleteMessagesForDateAndType(testDate, "morning");
            messageCleanupService.deleteMessagesForDateAndType(testDate, "evening");
            messageCleanupService.deleteMessagesForDateAndType(testDate, "no_classes");

            return ResponseEntity.ok("""
                ✅ Тест удаления запущен!
                
                Проверяем удаление за вчера ({})
                • Утренние сообщения
                • Вечерние сообщения  
                • Сообщения об отсутствии занятий
                
                Проверьте логи для деталей.
                """.formatted(testDate));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка теста удаления: " + e.getMessage());
        }
    }

    @GetMapping("/test-delete-morning")
    public ResponseEntity<String> testDeleteMorning() {
        try {
            messageCleanupService.testMorningDeletion();
            return ResponseEntity.ok("✅ Тест удаления утренних сообщений запущен");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка: " + e.getMessage());
        }
    }

    @GetMapping("/test-delete-evening")
    public ResponseEntity<String> testDeleteEvening() {
        try {
            messageCleanupService.testEveningDeletion();
            return ResponseEntity.ok("✅ Тест удаления вечерних сообщений запущен");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка: " + e.getMessage());
        }
    }

    @GetMapping("/test-delete-no-classes")
    public ResponseEntity<String> testDeleteNoClasses() {
        try {
            messageCleanupService.testNoClassesDeletion();
            return ResponseEntity.ok("✅ Тест удаления сообщений об отсутствии занятий запущен");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка: " + e.getMessage());
        }
    }

    @GetMapping("/debug/messages")
    public ResponseEntity<List<Map<String, Object>>> debugMessages() {
        try {
            List<Map<String, Object>> messages = jdbcTemplate.queryForList("""
                SELECT * FROM channel_messages 
                ORDER BY lesson_date DESC, lesson_type
                LIMIT 20
            """);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            System.err.println("❌ Ошибка получения сообщений: " + e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/debug/schedule")
    public ResponseEntity<Map<String, Object>> debugSchedule() {
        try {
            Map<String, Object> debugInfo = new HashMap<>();

            // Проверяем таблицу lessons через Supabase API
            try {
                // Здесь можно добавить проверку через SupabaseService если нужно
                debugInfo.put("lessons_count", "Проверьте через Supabase Dashboard");
            } catch (Exception e) {
                debugInfo.put("lessons_count", "Таблица не существует");
            }

            // Проверяем таблицу channel_messages
            try {
                List<Map<String, Object>> messages = jdbcTemplate.queryForList("SELECT COUNT(*) as count FROM channel_messages");
                debugInfo.put("messages_count", messages.get(0).get("count"));
            } catch (Exception e) {
                debugInfo.put("messages_count", "Таблица не существует");
            }

            debugInfo.put("current_time", LocalDateTime.now().toString());
            debugInfo.put("moscow_time", LocalDateTime.now().atZone(java.time.ZoneId.of("Europe/Moscow")).toString());

            return ResponseEntity.ok(debugInfo);
        } catch (Exception e) {
            System.err.println("❌ Ошибка отладки: " + e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/test-save-message")
    public ResponseEntity<String> testSaveMessage() {
        try {
            // Создаем тестовое сообщение
            org.telegram.telegrambots.meta.api.objects.Message testMessage =
                    new org.telegram.telegrambots.meta.api.objects.Message();

            // Устанавливаем ID через рефлексию (для теста)
            java.lang.reflect.Field field = testMessage.getClass().getDeclaredField("messageId");
            field.setAccessible(true);
            field.set(testMessage, 999999); // тестовый ID

            String testText = "🌅 Завтрашняя утренняя практика:\n\n8:00 - 11:30 - Майсор класс";

            // Вызываем метод сохранения
            bot.testSaveMessageInfo(testMessage, testText);

            return ResponseEntity.ok("""
            ✅ Тест сохранения сообщения запущен!
            
            Проверьте:
            1. Логи - должно быть сообщение о сохранении
            2. /debug/messages - должен появиться новый ID
            
            ID тестового сообщения: 999999
            """);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка теста: " + e.getMessage());
        }
    }

    @GetMapping("/create-test-messages")
    public ResponseEntity<String> createTestMessages() {
        try {
            // Очищаем старые тестовые данные
            jdbcTemplate.update("DELETE FROM channel_messages WHERE message_id >= 100000");

            // Создаем тестовые сообщения для вчера
            LocalDate yesterday = LocalDate.now().minusDays(1);

            jdbcTemplate.update("INSERT INTO channel_messages (message_id, lesson_type, lesson_date) VALUES (?, ?, ?)",
                    100001, "morning", yesterday);
            jdbcTemplate.update("INSERT INTO channel_messages (message_id, lesson_type, lesson_date) VALUES (?, ?, ?)",
                    100002, "evening", yesterday);
            jdbcTemplate.update("INSERT INTO channel_messages (message_id, lesson_type, lesson_date) VALUES (?, ?, ?)",
                    100003, "no_classes", yesterday);

            return ResponseEntity.ok("""
            ✅ Тестовые сообщения созданы!
            
            ID сообщений:
            • 100001 - утреннее
            • 100002 - вечернее  
            • 100003 - нет занятий
            
            Дата: """ + yesterday + """
            
            Теперь вызовите /test-delete для проверки удаления
            """);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка: " + e.getMessage());
        }
    }

    // Добавить в класс WebhookController
    @GetMapping("/debug/messages-full")
    public ResponseEntity<List<Map<String, Object>>> debugMessagesFull() {
        try {
            List<Map<String, Object>> messages = jdbcTemplate.queryForList("""
            SELECT message_id, lesson_type, lesson_date, message_text, created_at 
            FROM channel_messages 
            ORDER BY lesson_date DESC, lesson_type
            LIMIT 20
        """);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            System.err.println("❌ Ошибка получения сообщений: " + e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/force-cleanup")
    public ResponseEntity<String> forceCleanup() {
        try {
            LocalDate today = LocalDate.now();
            LocalDate tomorrow = LocalDate.now().plusDays(1);

            messageCleanupService.deleteMessagesForDateAndType(today, "morning");
            messageCleanupService.deleteMessagesForDateAndType(today, "evening");
            messageCleanupService.deleteMessagesForDateAndType(today, "no_classes");
            messageCleanupService.deleteMessagesForDateAndType(tomorrow, "morning");
            messageCleanupService.deleteMessagesForDateAndType(tomorrow, "evening");

            return ResponseEntity.ok("""
            ✅ Принудительная очистка запущена!
            
            Проверяются даты:
            • Сегодня ({}) - утро, вечер, нет занятий
            • Завтра ({}) - утро, вечер
            
            Проверьте логи для деталей.
            """.formatted(today, tomorrow));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка принудительной очистки: " + e.getMessage());
        }
    }

    // Добавить в класс WebhookController
    @GetMapping("/debug/cleanup-schedule")
    public ResponseEntity<Map<String, String>> debugCleanupSchedule() {
        Map<String, String> schedule = new HashMap<>();

        schedule.put("current_time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        schedule.put("current_date", LocalDate.now().toString());
        schedule.put("tomorrow_date", LocalDate.now().plusDays(1).toString());

        schedule.put("morning_cleanup", "08:00 МСК - удаление утренних отбивок (спустя 16 часов)");
        schedule.put("evening_cleanup", "19:00 МСК - удаление вечерних отбивок (спустя 27 часов)");
        schedule.put("no_classes_cleanup", "15:55 МСК следующего дня - удаление отбивок об отсутствии");

        schedule.put("notification_time", "16:00 МСК - отправка уведомлений о завтрашних занятиях");

        return ResponseEntity.ok(schedule);
    }
}