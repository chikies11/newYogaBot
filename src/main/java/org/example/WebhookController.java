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
        System.out.println("🌐 Получен webhook запрос");
        bot.onWebhookUpdateReceived(update);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("YogaBot is running! 🤖");
    }
}