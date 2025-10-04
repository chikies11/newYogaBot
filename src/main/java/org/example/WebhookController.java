package org.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;

@RestController
@RequestMapping("/")
public class WebhookController {

    private final YogaBot bot;

    public WebhookController(YogaBot bot) {
        this.bot = bot;
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
                    • 📝 Уведомление об отсутствии занятий (если применимо)
                    
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
}
