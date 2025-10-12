package org.example;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Component
public class NotificationScheduler {

    private final YogaBot yogaBot;

    public NotificationScheduler(YogaBot yogaBot) {
        this.yogaBot = yogaBot;
    }

    // Утреннее уведомление в 16:00 МСК = 13:00 UTC
    // Все уведомления в 16:00 МСК = 13:00 UTC с задержками
    @Scheduled(cron = "0 0 16 * * ?")
    public void sendAllNotifications() {
        System.out.println("⏰ [SCHEDULER] Отправка всех уведомлений в 16:00 МСК (13:00 UTC)...");

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Map<String, String> tomorrowSchedule = yogaBot.getTomorrowSchedule(tomorrow);
        String morningLesson = tomorrowSchedule.get("morning");
        String eveningLesson = tomorrowSchedule.get("evening");

        // Проверяем занятия
        boolean hasMorning = morningLesson != null && !morningLesson.equals("ОТДЫХ") && !morningLesson.equals("Отдых");
        boolean hasEvening = eveningLesson != null && !eveningLesson.equals("ОТДЫХ") && !eveningLesson.equals("Отдых");

        System.out.println("📊 На завтра: утро=" + hasMorning + ", вечер=" + hasEvening);

        if (hasMorning) {
            System.out.println("🌅 Отправка утреннего уведомления...");
            yogaBot.sendMorningNotification(morningLesson);

            // Задержка 1 минута перед вечерним
            try {
                System.out.println("⏳ Ждем 1 минуту перед вечерним уведомлением...");
                Thread.sleep(60000); // 60 секунд
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (hasEvening) {
            System.out.println("🌇 Отправка вечернего уведомления...");
            yogaBot.sendEveningNotification(eveningLesson);
        }

        // Если нет занятий вообще - отправляем уведомление об отсутствии через 5 минут
        // Если нет занятий вообще - отправляем уведомление об отсутствии
        if (!hasMorning && !hasEvening) {
            System.out.println("📝 Отправка уведомления об отсутствии занятий...");
            yogaBot.sendNoClassesNotification(morningLesson, eveningLesson);
            // Убрать sleep - незачем ждать если нет других уведомлений
        }
    }

    // Тестовый запуск каждые 30 минут для отладки
    @Scheduled(cron = "0 */30 * * * ?")
    public void debugScheduler() {
        System.out.println("🔔 [SCHEDULER DEBUG] Проверка планировщика...");
        System.out.println("🔔 [SCHEDULER DEBUG] UTC: " + LocalDateTime.now());
        System.out.println("🔔 [SCHEDULER DEBUG] МСК: " + LocalDateTime.now().plusHours(3));
        System.out.println("🔔 [SCHEDULER DEBUG] Следующие уведомления:");
        System.out.println("🔔 [SCHEDULER DEBUG] - Утреннее: 13:00 UTC (16:00 МСК)");
        System.out.println("🔔 [SCHEDULER DEBUG] - Отсутствие: 13:00 UTC (16:00 МСК)");
        System.out.println("🔔 [SCHEDULER DEBUG] - Вечернее: 13:00 UTC (16:00 МСК)");
    }
}