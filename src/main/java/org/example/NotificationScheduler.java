package org.example;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {

    private final YogaBot yogaBot;

    public NotificationScheduler(YogaBot yogaBot) {
        this.yogaBot = yogaBot;
    }

    // Утреннее уведомление в 12:00 МСК (9:00 UTC)
    @Scheduled(cron = "0 0 9 * * ?")
    public void sendMorningNotification() {
        System.out.println("⏰ Отправка утреннего уведомления...");
        yogaBot.sendDailyNotifications();
    }

    // Уведомление об отсутствии занятий в 14:00 МСК (11:00 UTC)
    @Scheduled(cron = "0 0 11 * * ?")
    public void sendNoClassesNotification() {
        System.out.println("⏰ Проверка отсутствия занятий...");
        yogaBot.sendDailyNotifications();
    }

    // Вечернее уведомление в 18:00 МСК (15:00 UTC)
    @Scheduled(cron = "0 0 15 * * ?")
    public void sendEveningNotification() {
        System.out.println("⏰ Отправка вечернего уведомления...");
        yogaBot.sendDailyNotifications();
    }
}