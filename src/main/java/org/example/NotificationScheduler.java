package org.example;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Component
public class NotificationScheduler {

    private final YogaBot yogaBot;

    public NotificationScheduler(YogaBot yogaBot) {
        this.yogaBot = yogaBot;
    }

    // Уведомления в 16:00 МСК = 13:00 UTC
    @Scheduled(cron = "0 0 13 * * ?")
    public void sendAllNotifications() {
        LocalDateTime moscowTime = LocalDateTime.now(ZoneId.of("Europe/Moscow"));
        System.out.println("⏰ [SCHEDULER] Отправка всех уведомлений в 16:00 МСК (13:00 UTC)...");
        System.out.println("⏰ [SCHEDULER] Текущее время МСК: " + moscowTime);

        LocalDate tomorrow = LocalDate.now(ZoneId.of("Europe/Moscow")).plusDays(1);
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

        // Если нет занятий вообще - отправляем уведомление об отсутствии
        if (!hasMorning && !hasEvening) {
            System.out.println("📝 Отправка уведомления об отсутствии занятий...");
            yogaBot.sendNoClassesNotification(morningLesson, eveningLesson);
        }

        System.out.println("✅ [SCHEDULER] Все уведомления отправлены!");
    }

    // Тестовый запуск каждые 30 минут для отладки
    @Scheduled(cron = "0 */30 * * * ?")
    public void debugScheduler() {
        LocalDateTime moscowTime = LocalDateTime.now(ZoneId.of("Europe/Moscow"));
        System.out.println("🔔 [SCHEDULER DEBUG] Проверка планировщика...");
        System.out.println("🔔 [SCHEDULER DEBUG] UTC: " + LocalDateTime.now());
        System.out.println("🔔 [SCHEDULER DEBUG] МСК: " + moscowTime);
        System.out.println("🔔 [SCHEDULER DEBUG] Дата МСК: " + moscowTime.toLocalDate());
        System.out.println("🔔 [SCHEDULER DEBUG] Следующие уведомления: 13:00 UTC (16:00 МСК)");
    }
}