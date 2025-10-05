package org.example;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {

    private final YogaBot yogaBot;

    public NotificationScheduler(YogaBot yogaBot) {
        this.yogaBot = yogaBot;
    }

    // Утреннее уведомление в 12:00 МСК = 9:00 UTC
    @Scheduled(cron = "0 0 9 * * ?")
    public void sendMorningNotification() {
        System.out.println("⏰ Отправка утреннего уведомления в 12:00 МСК...");
        yogaBot.sendDailyNotifications();
    }

    // Уведомление об отсутствии занятий в 14:00 МСК = 11:00 UTC
    @Scheduled(cron = "0 0 11 * * ?")
    public void sendNoClassesNotification() {
        System.out.println("⏰ Проверка отсутствия занятий в 14:00 МСК...");
        yogaBot.sendDailyNotifications();
    }

    // Вечернее уведомление в 18:00 МСК = 15:00 UTC
    @Scheduled(cron = "0 0 15 * * ?")
    public void sendEveningNotification() {
        System.out.println("⏰ Отправка вечернего уведомления в 18:00 МСК...");
        yogaBot.sendDailyNotifications();
    }

    // Дополнительный пинг каждые 5 минут для отладки
    @Scheduled(cron = "0 */5 * * * ?")
    public void debugTime() {
        System.out.println("🐛 DEBUG: Текущее время UTC: " + java.time.LocalDateTime.now());
        System.out.println("🐛 DEBUG: Текущее время МСК: " + java.time.LocalDateTime.now().plusHours(3));
    }
}