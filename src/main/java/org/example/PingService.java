package org.example;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;

@Service
public class PingService {

    private final String appUrl = "https://yogabot-8u6q.onrender.com";
    private final RestTemplate restTemplate = new RestTemplate();

    private LocalDateTime lastSuccessfulPing;

    // Пинг каждые 4 минуты
    @Scheduled(fixedRate = 240000)
    public void pingSelf() {
        try {
            String response = restTemplate.getForObject(appUrl, String.class);
            if (response != null && response.contains("YogaBot")) {
                lastSuccessfulPing = LocalDateTime.now();
                System.out.println("✅ Пинг выполнен: " + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
            } else {
                System.err.println("⚠️ Пинг выполнен, но ответ неожиданный");
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка пинга: " + e.getMessage());
        }
    }

    // Health check каждые 2 минуты
    @Scheduled(fixedRate = 120000)
    public void pingHealth() {
        try {
            String response = restTemplate.getForObject(appUrl + "/health", String.class);
            System.out.println("🏥 Health check: " + (response != null ? "OK" : "FAIL"));
        } catch (Exception e) {
            System.err.println("❌ Health check failed: " + e.getMessage());
        }
    }

    public LocalDateTime getLastSuccessfulPing() {
        return lastSuccessfulPing;
    }
}