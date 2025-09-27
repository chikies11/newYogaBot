package org.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;

@RestController
public class WebhookController {

    private final YogaBot bot;

    public WebhookController(YogaBot bot) {
        this.bot = bot;
    }

    @PostMapping("/")
    public ResponseEntity<Void> onUpdateReceived(@RequestBody Update update) {
        bot.onWebhookUpdateReceived(update);
        return ResponseEntity.ok().build();
    }
}