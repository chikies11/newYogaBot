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
    private final TelegramWebhookBot telegramBot;

    @Value("${app.channelId:}")
    private String channelId;

    public MessageCleanupService(JdbcTemplate jdbcTemplate, TelegramWebhookBot telegramBot) {
        this.jdbcTemplate = jdbcTemplate;
        this.telegramBot = telegramBot;
    }

    @PostConstruct
    public void init() {
        createMessagesTableIfNotExists();
    }

    private void createMessagesTableIfNotExists() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS channel_messages (
                    id BIGSERIAL PRIMARY KEY,
                    message_id INTEGER NOT NULL,
                    lesson_type VARCHAR(10) NOT NULL,
                    lesson_date DATE NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(message_id, lesson_type, lesson_date)
                )
            """);
            log.info("✅ Таблица channel_messages создана/проверена");
        } catch (Exception e) {
            log.error("❌ Ошибка создания таблицы channel_messages", e);
        }
    }

    public void saveMessageId(Integer messageId, String lessonType, LocalDate lessonDate) {
        try {
            jdbcTemplate.update("""
                INSERT INTO channel_messages (message_id, lesson_type, lesson_date) 
                VALUES (?, ?, ?)
                ON CONFLICT (message_id, lesson_type, lesson_date) DO NOTHING
            """, messageId, lessonType, lessonDate);
            log.info("💾 Сохранен ID сообщения: {} для {} занятия на {}", messageId, lessonType, lessonDate);
        } catch (Exception e) {
            log.error("❌ Ошибка сохранения ID сообщения", e);
        }
    }

    // 🔧 ТЕСТОВЫЕ МЕТОДЫ ДЛЯ РУЧНОГО ЗАПУСКА

    public void testMorningDeletion() {
        log.info("🧪 РУЧНОЙ ТЕСТ: Удаление утренних сообщений");
        deleteYesterdayMorningMessages();
    }

    public void testEveningDeletion() {
        log.info("🧪 РУЧНОЙ ТЕСТ: Удаление вечерних сообщений");
        deleteYesterdayEveningMessages();
    }

    public void testNoClassesDeletion() {
        log.info("🧪 РУЧНОЙ ТЕСТ: Удаление сообщений об отсутствии занятий");
        deleteYesterdayNoClassesMessages();
    }

    // Удаление вчерашней утренней отбивки в 8:00 МСК
    @Scheduled(cron = "0 0 8 * * ?")
    public void deleteYesterdayMorningMessages() {
        log.info("🔄 ЗАПУСК deleteYesterdayMorningMessages в {}", LocalDateTime.now());

        if (channelId == null || channelId.isEmpty()) {
            log.error("❌ Channel ID не настроен: {}", channelId);
            return;
        }

        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("🗑️ Удаление утренних сообщений за вчера ({}) в 8:00 МСК", yesterday);
        deleteMessagesForDateAndType(yesterday, "morning");
    }

    // Удаление вчерашней вечерней отбивки в 16:00 МСК
    @Scheduled(cron = "0 0 16 * * ?")
    public void deleteYesterdayEveningMessages() {
        log.info("🔄 ЗАПУСК deleteYesterdayEveningMessages в {}", LocalDateTime.now());

        if (channelId == null || channelId.isEmpty()) {
            log.error("❌ Channel ID не настроен: {}", channelId);
            return;
        }

        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("🗑️ Удаление вечерних сообщений за вчера ({}) в 16:00 МСК", yesterday);
        deleteMessagesForDateAndType(yesterday, "evening");
    }

    // Удаление вчерашних уведомлений об отсутствии занятий в 17:00 МСК
    @Scheduled(cron = "0 0 17 * * ?")
    public void deleteYesterdayNoClassesMessages() {
        log.info("🔄 ЗАПУСК deleteYesterdayNoClassesMessages в {}", LocalDateTime.now());

        if (channelId == null || channelId.isEmpty()) {
            log.error("❌ Channel ID не настроен: {}", channelId);
            return;
        }

        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("🗑️ Удаление уведомлений об отсутствии занятий за вчера ({}) в 17:00 МСК", yesterday);
        deleteMessagesForDateAndType(yesterday, "no_classes");
    }

    public void deleteMessagesForDateAndType(LocalDate date, String lessonType) {
        try {
            log.info("🔍 Поиск сообщений для удаления: date={}, type={}", date, lessonType);

            List<Map<String, Object>> messages = jdbcTemplate.queryForList("""
                SELECT message_id FROM channel_messages 
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
                if (deleteMessageFromChannel(messageId)) {
                    deletedCount++;
                    jdbcTemplate.update("DELETE FROM channel_messages WHERE message_id = ? AND lesson_date = ? AND lesson_type = ?",
                            messageId, date, lessonType);
                }
            }

            log.info("✅ Удалено {} сообщений из {} для {} {}", deletedCount, messages.size(), date, lessonType);

        } catch (Exception e) {
            log.error("❌ Ошибка удаления сообщений для {} {}", date, lessonType, e);
        }
    }

    private boolean deleteMessageFromChannel(Integer messageId) {
        try {
            DeleteMessage deleteMessage = new DeleteMessage(channelId, messageId);
            boolean result = telegramBot.execute(deleteMessage);

            if (result) {
                log.info("✅ Сообщение {} удалено из канала", messageId);
            } else {
                log.warn("⚠️ Не удалось удалить сообщение {} из канала", messageId);
            }

            return result;
        } catch (TelegramApiException e) {
            if (e.getMessage().contains("message to delete not found")) {
                log.info("ℹ️ Сообщение {} уже удалено из канала", messageId);
                return true;
            }
            log.error("❌ Ошибка удаления сообщения {}: {}", messageId, e.getMessage());
            return false;
        }
    }

    // Очистка старых записей из БД (старше 7 дней)
    @Scheduled(cron = "0 0 2 * * ?")
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
}