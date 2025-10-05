package org.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/")
public class WebhookController {

    private final YogaBot bot;
    private final PingService pingService;

    public WebhookController(YogaBot bot, PingService pingService) {
        this.bot = bot;
        this.pingService = pingService;
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
                
                Проверьте канал: @yoga_yollayo11
                
                Должны прийти:
                • 🌅 Утреннее уведомление
                • 🌇 Вечернее уведомление  
                
                Если не приходят - проверьте права бота в канале!
                """);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка: " + e.getMessage());
        }
    }

    @GetMapping("/test-morning")
    public ResponseEntity<String> testMorning() {
        try {
            bot.sendManualNotification("morning");
            return ResponseEntity.ok("🌅 Тестовое утреннее уведомление отправлено! Проверьте канал @yoga_yollayo11");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка: " + e.getMessage());
        }
    }

    @GetMapping("/test-evening")
    public ResponseEntity<String> testEvening() {
        try {
            bot.sendManualNotification("evening");
            return ResponseEntity.ok("🌇 Тестовое вечернее уведомление отправлено! Проверьте канал @yoga_yollayo11");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка: " + e.getMessage());
        }
    }

    @GetMapping("/test-no-classes")
    public ResponseEntity<String> testNoClasses() {
        try {
            bot.sendManualNotification("no_classes");
            return ResponseEntity.ok("📝 Тестовое уведомление об отсутствии занятий отправлено! Проверьте канал @yoga_yollayo11");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка: " + e.getMessage());
        }
    }

    @GetMapping("/force-notification")
    public ResponseEntity<String> forceNotification() {
        try {
            // Принудительно запускаем уведомления
            bot.sendDailyNotifications();
            return ResponseEntity.ok("""
            🔔 Принудительная отправка уведомлений запущена!
            
            Проверьте логи в Render для отладки.
            Должны отправиться уведомления согласно текущему времени.
            """);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Ошибка: " + e.getMessage());
        }
    }

    @GetMapping("/debug-time")
    public ResponseEntity<Map<String, String>> debugTime() {
        // Вызываем метод проверки времени
        bot.checkServerTime();

        Map<String, String> timeInfo = new HashMap<>();
        timeInfo.put("server_time_utc", LocalDateTime.now().toString());
        timeInfo.put("server_time_msk", LocalDateTime.now().plusHours(3).toString());
        timeInfo.put("tomorrow", LocalDate.now().plusDays(1).toString());

        return ResponseEntity.ok(timeInfo);
    }
}