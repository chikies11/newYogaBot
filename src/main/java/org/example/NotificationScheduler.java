package org.example;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class NotificationScheduler {

    private final YogaBot yogaBot;

    public NotificationScheduler(YogaBot yogaBot) {
        this.yogaBot = yogaBot;
    }

    // Утреннее уведомление в 12:00 МСК = 9:00 UTC
    @Scheduled(cron = "0 0 9 * * ?")
    public void sendMorningNotification() {
        System.out.println("⏰ [SCHEDULER] Отправка утреннего уведомления в 12:00 МСК (9:00 UTC)...");
        System.out.println("⏰ [SCHEDULER] Текущее время UTC: " + LocalDateTime.now());
        System.out.println("⏰ [SCHEDULER] Текущее время МСК: " + LocalDateTime.now().plusHours(3));
        yogaBot.sendDailyNotifications();
    }

    // Уведомление об отсутствии занятий в 14:00 МСК = 11:00 UTC
    @Scheduled(cron = "0 0 11 * * ?")
    public void sendNoClassesNotification() {
        System.out.println("⏰ [SCHEDULER] Проверка отсутствия занятий в 14:00 МСК (11:00 UTC)...");
        System.out.println("⏰ [SCHEDULER] Текущее время UTC: " + LocalDateTime.now());
        System.out.println("⏰ [SCHEDULER] Текущее время МСК: " + LocalDateTime.now().plusHours(3));
        yogaBot.sendDailyNotifications();
    }

    // Вечернее уведомление в 18:00 МСК = 15:00 UTC
    @Scheduled(cron = "0 0 15 * * ?")
    public void sendEveningNotification() {
        System.out.println("⏰ [SCHEDULER] Отправка вечернего уведомления в 18:00 МСК (15:00 UTC)...");
        System.out.println("⏰ [SCHEDULER] Текущее время UTC: " + LocalDateTime.now());
        System.out.println("⏰ [SCHEDULER] Текущее время МСК: " + LocalDateTime.now().plusHours(3));
        yogaBot.sendDailyNotifications();
    }

    // Тестовый запуск каждые 30 минут для отладки
    @Scheduled(cron = "0 */30 * * * ?")
    public void debugScheduler() {
        System.out.println("🔔 [SCHEDULER DEBUG] Проверка планировщика...");
        System.out.println("🔔 [SCHEDULER DEBUG] UTC: " + LocalDateTime.now());
        System.out.println("🔔 [SCHEDULER DEBUG] МСК: " + LocalDateTime.now().plusHours(3));
        System.out.println("🔔 [SCHEDULER DEBUG] Следующие уведомления:");
        System.out.println("🔔 [SCHEDULER DEBUG] - Утреннее: 9:00 UTC (12:00 МСК)");
        System.out.println("🔔 [SCHEDULER DEBUG] - Отсутствие: 11:00 UTC (14:00 МСК)");
        System.out.println("🔔 [SCHEDULER DEBUG] - Вечернее: 15:00 UTC (18:00 МСК)");
    }
}