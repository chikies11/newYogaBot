package org.example.service;

import jakarta.annotation.PostConstruct;
import org.example.YogaBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class MessageCleanupService {

    private static final Logger log = LoggerFactory.getLogger(MessageCleanupService.class);

    private final JdbcTemplate jdbcTemplate;
    private final YogaBot yogaBot;

    @Value("${app.channelId:}")
    private String channelId;

    public MessageCleanupService(JdbcTemplate jdbcTemplate, YogaBot yogaBot) {
        this.jdbcTemplate = jdbcTemplate;
        this.yogaBot = yogaBot;
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

    // Удаление вчерашней утренней отбивки в 8:00 МСК
    @Scheduled(cron = "0 0 5 * * ?", zone = "Europe/Moscow") // 8:00 МСК = 5:00 UTC
    public void deleteYesterdayMorningMessages() {
        if (channelId == null || channelId.isEmpty()) {
            log.warn("⚠️ Channel ID не настроен, пропускаем удаление утренних сообщений");
            return;
        }

        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("🗑️ Удаление утренних сообщений за вчера ({}) в 8:00 МСК", yesterday);

        deleteMessagesForDateAndType(yesterday, "morning");
    }

    // Удаление вчерашней вечерней отбивки в 16:00 МСК
    @Scheduled(cron = "0 0 13 * * ?", zone = "Europe/Moscow") // 16:00 МСК = 13:00 UTC
    public void deleteYesterdayEveningMessages() {
        if (channelId == null || channelId.isEmpty()) {
            log.warn("⚠️ Channel ID не настроен, пропускаем удаление вечерних сообщений");
            return;
        }

        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("🗑️ Удаление вечерних сообщений за вчера ({}) в 16:00 МСК", yesterday);

        deleteMessagesForDateAndType(yesterday, "evening");
    }

    private void deleteMessagesForDateAndType(LocalDate date, String lessonType) {
        try {
            List<Map<String, Object>> messages = jdbcTemplate.queryForList("""
                SELECT message_id FROM channel_messages 
                WHERE lesson_date = ? AND lesson_type = ?
            """, date, lessonType);

            if (messages.isEmpty()) {
                log.info("ℹ️ Не найдено сообщений для удаления: {} {}", date, lessonType);
                return;
            }

            log.info("🔍 Найдено {} сообщений для удаления: {} {}", messages.size(), date, lessonType);

            int deletedCount = 0;
            for (Map<String, Object> message : messages) {
                Integer messageId = (Integer) message.get("message_id");
                if (deleteMessageFromChannel(messageId)) {
                    deletedCount++;
                    // Удаляем запись из БД после успешного удаления из канала
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
            boolean result = yogaBot.execute(deleteMessage);

            if (result) {
                log.info("✅ Сообщение {} удалено из канала", messageId);
            } else {
                log.warn("⚠️ Не удалось удалить сообщение {} из канала", messageId);
            }

            return result;
        } catch (TelegramApiException e) {
            if (e.getMessage().contains("message to delete not found")) {
                log.info("ℹ️ Сообщение {} уже удалено из канала", messageId);
                return true; // Считаем успехом, т.к. цель достигнута - сообщения нет
            }
            log.error("❌ Ошибка удаления сообщения {}: {}", messageId, e.getMessage());
            return false;
        }
    }

    // Очистка старых записей из БД (старше 7 дней)
    @Scheduled(cron = "0 0 2 * * ?") // Ежедневно в 2:00 UTC
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