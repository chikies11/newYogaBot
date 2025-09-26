package org.example;

import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;

@RestController
public class WebhookController {

    private final YogaBot bot;

    public WebhookController(YogaBot bot) {
        this.bot = bot;
    }

    @PostMapping("/")
    public void onUpdateReceived(@RequestBody Update update) {
        bot.onWebhookUpdateReceived(update);
    }
}
