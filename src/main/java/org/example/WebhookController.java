package org.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/")
public class WebhookController {

    private final YogaBot bot;

    public WebhookController(YogaBot bot) {
        this.bot = bot;
    }

    @PostMapping
    public ResponseEntity<String> onUpdateReceived(@RequestBody String update) {
        System.out.println("🌐 Получен webhook запрос: " + update);
        return ResponseEntity.ok("OK");
    }

    @GetMapping
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("YogaBot is running! 🤖");
    }
}