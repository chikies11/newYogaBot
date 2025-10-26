package org.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class MessageCleanupService {

    private static final Logger log = LoggerFactory.getLogger(MessageCleanupService.class);

    private final JdbcTemplate jdbcTemplate;
    private final TelegramService telegramService;

    @Value("${app.channelId:}")
    private String channelId;

    public MessageCleanupService(JdbcTemplate jdbcTemplate, TelegramService telegramService) {
        this.jdbcTemplate = jdbcTemplate;
        this.telegramService = telegramService;
    }

    @PostConstruct
    public void init() {
        createMessagesTableIfNotExists();
        log.info("✅ MessageCleanupService инициализирован. Канал: {}", channelId);
    }

    private void createMessagesTableIfNotExists() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS channel_messages (
                    id BIGSERIAL PRIMARY KEY,
                    message_id INTEGER NOT NULL,
                    lesson_type VARCHAR(20) NOT NULL,
                    lesson_date DATE NOT NULL,
                    message_text TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(message_id, lesson_type, lesson_date)
                )
            """);
            log.info("✅ Таблица channel_messages создана/проверена");
        } catch (Exception e) {
            log.error("❌ Ошибка создания таблицы channel_messages", e);
        }
    }

    public void saveMessageId(Integer messageId, String lessonType, LocalDate lessonDate, String messageText) {
        try {
            jdbcTemplate.update("""
                INSERT INTO channel_messages (message_id, lesson_type, lesson_date, message_text) 
                VALUES (?, ?, ?, ?)
                ON CONFLICT (message_id, lesson_type, lesson_date) DO UPDATE SET
                    message_text = EXCLUDED.message_text,
                    created_at = CURRENT_TIMESTAMP
            """, messageId, lessonType, lessonDate, messageText);
            log.info("💾 Сохранен ID сообщения: {} для {} занятия на {} (текст: {})",
                    messageId, lessonType, lessonDate,
                    messageText != null ? messageText.substring(0, Math.min(50, messageText.length())) + "..." : "null");
        } catch (Exception e) {
            log.error("❌ Ошибка сохранения ID сообщения", e);
        }
    }

    // 🔧 ТЕСТОВЫЕ МЕТОДЫ ДЛЯ РУЧНОГО ЗАПУСКА

    public void testMorningDeletion() {
        log.info("🧪 РУЧНОЙ ТЕСТ: Удаление утренних сообщений");
        deleteTodayMorningMessages();
    }

    public void testEveningDeletion() {
        log.info("🧪 РУЧНОЙ ТЕСТ: Удаление вечерних сообщений");
        deleteTodayEveningMessages();
    }

    public void testNoClassesDeletion() {
        log.info("🧪 РУЧНОЙ ТЕСТ: Удаление сообщений об отсутствии занятий");
        deleteTomorrowNoClassesMessages();
    }

    // Удаление утренней отбивки в 8:00 утра в день занятия (спустя 16 часов после отбивки в 16:00)
    @Scheduled(cron = "0 0 8 * * ?", zone = "Europe/Moscow")
    public void deleteTodayMorningMessages() {
        log.info("🔄 ЗАПУСК deleteTodayMorningMessages в {}", LocalDateTime.now());

        if (channelId == null || channelId.isEmpty()) {
            log.error("❌ Channel ID не настроен: {}", channelId);
            return;
        }

        // Удаляем утренние сообщения на СЕГОДНЯШНИЙ день
        LocalDate targetDate = LocalDate.now();
        log.info("🗑️ Удаление утренних сообщений на сегодня ({}) в 8:00 МСК", targetDate);
        deleteMessagesForDateAndType(targetDate, "morning");
    }

    // Удаление вечерней отбивки в 19:00 вечера в день занятия (спустя 27 часов после отбивки в 16:01)
    @Scheduled(cron = "0 0 19 * * ?", zone = "Europe/Moscow")
    public void deleteTodayEveningMessages() {
        log.info("🔄 ЗАПУСК deleteTodayEveningMessages в {}", LocalDateTime.now());

        if (channelId == null || channelId.isEmpty()) {
            log.error("❌ Channel ID не настроен: {}", channelId);
            return;
        }

        // Удаляем вечерние сообщения на СЕГОДНЯШНИЙ день
        LocalDate targetDate = LocalDate.now();
        log.info("🗑️ Удаление вечерних сообщений на сегодня ({}) в 19:00 МСК", targetDate);
        deleteMessagesForDateAndType(targetDate, "evening");
    }

    // Удаление отбивки об отсутствии занятий в 15:55 СЛЕДУЮЩЕГО дня
    @Scheduled(cron = "0 55 15 * * ?", zone = "Europe/Moscow")
    public void deleteTomorrowNoClassesMessages() {
        log.info("🔄 ЗАПУСК deleteTomorrowNoClassesMessages в {}", LocalDateTime.now());

        if (channelId == null || channelId.isEmpty()) {
            log.error("❌ Channel ID не настроен: {}", channelId);
            return;
        }

        // Удаляем сообщения об отсутствии занятий на ЗАВТРАШНИЙ день
        LocalDate targetDate = LocalDate.now().plusDays(1);
        log.info("🗑️ Удаление сообщений об отсутствии занятий на завтра ({}) в 15:55 МСК", targetDate);
        deleteMessagesForDateAndType(targetDate, "no_classes");
    }

    public void deleteMessagesForDateAndType(LocalDate date, String lessonType) {
        try {
            log.info("🔍 Поиск сообщений для удаления: date={}, type={}", date, lessonType);

            List<Map<String, Object>> messages = jdbcTemplate.queryForList("""
                SELECT message_id, message_text FROM channel_messages 
                WHERE lesson_date = ? AND lesson_type = ?
            """, date, lessonType);

            log.info("📋 Найдено сообщений для удаления: {} для {} {}", messages.size(), date, lessonType);

            if (messages.isEmpty()) {
                log.info("ℹ️ Не найдено сообщений для удаления: {} {}", date, lessonType);
                return;
            }

            int deletedCount = 0;
            for (Map<String, Object> message : messages) {
                Integer messageId = (Integer) message.get("message_id");
                String messageText = (String) message.get("message_text");

                log.info("🔍 Удаление сообщения {}: {}", messageId,
                        messageText != null ? messageText.substring(0, Math.min(100, messageText.length())) : "null");

                if (deleteMessageFromChannel(messageId)) {
                    deletedCount++;
                    // Удаляем запись из БД после успешного удаления из канала
                    jdbcTemplate.update("DELETE FROM channel_messages WHERE message_id = ? AND lesson_date = ? AND lesson_type = ?",
                            messageId, date, lessonType);
                    log.info("✅ Сообщение {} удалено из БД", messageId);
                }
            }

            log.info("✅ Удалено {} сообщений из {} для {} {}", deletedCount, messages.size(), date, lessonType);

        } catch (Exception e) {
            log.error("❌ Ошибка удаления сообщений для {} {}", date, lessonType, e);
        }
    }

    private boolean deleteMessageFromChannel(Integer messageId) {
        try {
            log.info("🗑️ Попытка удаления сообщения {} из канала {}", messageId, channelId);
            boolean result = telegramService.deleteMessageFromChannel(messageId);

            if (result) {
                log.info("✅ Сообщение {} удалено из канала", messageId);
            } else {
                log.warn("⚠️ Не удалось удалить сообщение {} из канала", messageId);
            }

            return result;
        } catch (Exception e) {
            log.error("❌ Ошибка удаления сообщения {}: {}", messageId, e.getMessage());
            return false;
        }
    }

    // Очистка старых записей из БД (старше 7 дней)
    @Scheduled(cron = "0 0 2 * * ?", zone = "Europe/Moscow")
    public void cleanupOldRecords() {
        try {
            LocalDate weekAgo = LocalDate.now().minusDays(7);
            int deleted = jdbcTemplate.update("DELETE FROM channel_messages WHERE lesson_date < ?", weekAgo);
            if (deleted > 0) {
                log.info("🧹 Очищено {} старых записей сообщений (старше {})", deleted, weekAgo);
            }
        } catch (Exception e) {
            log.error("❌ Ошибка очистки старых записей", e);
        }
    }

    // Отладочный метод для проверки состояния сообщений
    @Scheduled(fixedRate = 300000) // Каждые 5 минут
    public void debugScheduledTasks() {
        LocalDateTime now = LocalDateTime.now();
        log.info("🕒 Текущее время МСК: {}, Сегодня: {}, Завтра: {}",
                now.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
                LocalDate.now(),
                LocalDate.now().plusDays(1));

        try {
            List<Map<String, Object>> messages = jdbcTemplate.queryForList(
                    "SELECT message_id, lesson_type, lesson_date, created_at FROM channel_messages ORDER BY lesson_date DESC, lesson_type LIMIT 5");
            log.info("📋 Последние 5 сообщений в БД: {}", messages);
        } catch (Exception e) {
            log.error("❌ Ошибка получения отладочной информации", e);
        }
    }

    // Метод для удаления ВСЕХ сообщений на определенную дату (для тестирования)
    public void deleteAllMessagesForDate(LocalDate date) {
        log.info("🧹 Принудительное удаление ВСЕХ сообщений на дату: {}", date);
        deleteMessagesForDateAndType(date, "morning");
        deleteMessagesForDateAndType(date, "evening");
        deleteMessagesForDateAndType(date, "no_classes");
    }
}