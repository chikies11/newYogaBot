package org.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.example.service.DatabaseService;
import org.example.service.MessageCleanupService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/")
public class WebhookController {

    private final YogaBot bot;
    private final PingService pingService;
    private final DatabaseService databaseService;
    private final MessageCleanupService messageCleanupService;
    private final JdbcTemplate jdbcTemplate;

    public WebhookController(YogaBot bot, PingService pingService,
                             DatabaseService databaseService,
                             MessageCleanupService messageCleanupService,
                             JdbcTemplate jdbcTemplate) {
        this.bot = bot;
        this.pingService = pingService;
        this.databaseService = databaseService;
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
            // Принудительно пересоздаем таблицы
            databaseService.createTablesIfNotExists();
            databaseService.initializeDefaultSchedule();

            return ResponseEntity.ok("""
                ✅ База данных переинициализирована!
                
                Выполнено:
                • Создание таблиц
                • Инициализация расписания
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

            // Проверяем таблицу lessons
            try {
                List<Map<String, Object>> lessons = jdbcTemplate.queryForList("SELECT COUNT(*) as count FROM lessons");
                debugInfo.put("lessons_count", lessons.get(0).get("count"));
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
}