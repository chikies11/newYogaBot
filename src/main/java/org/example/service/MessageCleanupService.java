package org.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class MessageCleanupService {

    private static final Logger log = LoggerFactory.getLogger(MessageCleanupService.class);

    private final SupabaseService supabaseService;
    private final TelegramService telegramService;

    @Value("${app.channelId:}")
    private String channelId;

    public MessageCleanupService(SupabaseService supabaseService, TelegramService telegramService) {
        this.supabaseService = supabaseService;
        this.telegramService = telegramService;
    }

    public void saveMessageId(Integer messageId, String lessonType, LocalDate lessonDate, String messageText) {
        supabaseService.saveMessageId(messageId, lessonType, lessonDate, messageText);
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
        deleteTodayNoClassesMessages();
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
    public void deleteTodayNoClassesMessages() {
        log.info("🔄 ЗАПУСК deleteTodayNoClassesMessages в {}", LocalDateTime.now());

        if (channelId == null || channelId.isEmpty()) {
            log.error("❌ Channel ID не настроен: {}", channelId);
            return;
        }

        // Удаляем сообщения об отсутствии занятий на СЕГОДНЯШНИЙ день
        LocalDate targetDate = LocalDate.now();
        log.info("🗑️ Удаление сообщений об отсутствии занятий на сегодня ({}) в 15:55 МСК", targetDate);
        deleteMessagesForDateAndType(targetDate, "no_classes");
    }

    public void deleteMessagesForDateAndType(LocalDate date, String lessonType) {
        try {
            log.info("🔍 Поиск сообщений для удаления: date={}, type={}", date, lessonType);

            List<Map<String, Object>> messages = supabaseService.getMessagesForDeletion(date, lessonType);

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
                    supabaseService.deleteMessageRecord(messageId, date, lessonType);
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
}