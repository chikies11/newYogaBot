package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class SupabaseService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    public SupabaseService(@Value("${supabase.url}") String supabaseUrl,
                           @Value("${supabase.key}") String supabaseKey) {
        this.objectMapper = new ObjectMapper();

        // Проверяем что переменные не пустые
        if (supabaseUrl == null || supabaseUrl.isEmpty()) {
            throw new IllegalArgumentException("Supabase URL не настроен");
        }
        if (supabaseKey == null || supabaseKey.isEmpty()) {
            throw new IllegalArgumentException("Supabase Key не настроен");
        }

        this.supabaseUrl = supabaseUrl;
        this.supabaseKey = supabaseKey;

        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + supabaseKey)
                .defaultHeader("apikey", supabaseKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Prefer", "return=minimal")
                .build();

        log.info("✅ SupabaseService инициализирован с URL: {}", supabaseUrl);
    }

    // === МЕТОДЫ ДЛЯ РАБОТЫ С РАСПИСАНИЕМ ===

    public void saveSchedule(DayOfWeek dayOfWeek, String lessonType, String description, Long adminId) {
        try {
            String url = supabaseUrl + "/rest/v1/lessons";

            Map<String, Object> data = new HashMap<>();
            data.put("day_of_week", dayOfWeek.toString());
            data.put("lesson_type", lessonType);
            data.put("description", description);

            webClient.post()
                    .uri(url)
                    .header("Prefer", "resolution=merge-duplicates")
                    .bodyValue(data)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnSuccess(response -> log.info("✅ Расписание сохранено: {} {} - {}", dayOfWeek, lessonType, description))
                    .doOnError(error -> log.error("❌ Ошибка сохранения расписания", error))
                    .block();

        } catch (Exception e) {
            log.error("❌ Ошибка сохранения расписания в Supabase", e);
        }
    }

    public Map<DayOfWeek, Map<String, String>> loadSchedule() {
        Map<DayOfWeek, Map<String, String>> schedule = new HashMap<>();

        try {
            String url = supabaseUrl + "/rest/v1/lessons?select=*";

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode jsonNode = objectMapper.readTree(response);

            if (jsonNode.isArray()) {
                for (JsonNode node : jsonNode) {
                    try {
                        DayOfWeek dayOfWeek = DayOfWeek.valueOf(node.get("day_of_week").asText());
                        String lessonType = node.get("lesson_type").asText();
                        String description = node.get("description").asText();

                        schedule.computeIfAbsent(dayOfWeek, k -> new HashMap<>())
                                .put(lessonType, description);
                    } catch (IllegalArgumentException e) {
                        log.warn("⚠️ Неизвестный день недели в БД: {}", node.get("day_of_week"));
                    }
                }
            }

            log.info("✅ Загружено расписание из Supabase: {} записей", schedule.size());

        } catch (Exception e) {
            log.error("❌ Ошибка загрузки расписания из Supabase", e);
        }

        return schedule;
    }

    public void initializeDefaultSchedule() {
        try {
            // Проверяем, есть ли уже записи
            String checkUrl = supabaseUrl + "/rest/v1/lessons?select=count";

            String countResponse = webClient.get()
                    .uri(checkUrl)
                    .header("Prefer", "count=exact")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode countNode = objectMapper.readTree(countResponse);
            int count = countNode.size();

            if (count > 0) {
                log.info("✅ В Supabase уже есть расписание ({} записей), пропускаем инициализацию", count);
                return;
            }

            log.info("🔄 Инициализация дефолтного расписания в Supabase...");

            // Инициализируем дефолтное расписание
            Map<DayOfWeek, Map<String, String>> defaultSchedule = createDefaultSchedule();

            for (Map.Entry<DayOfWeek, Map<String, String>> dayEntry : defaultSchedule.entrySet()) {
                DayOfWeek dayOfWeek = dayEntry.getKey();
                Map<String, String> lessons = dayEntry.getValue();

                for (Map.Entry<String, String> lessonEntry : lessons.entrySet()) {
                    saveSchedule(dayOfWeek, lessonEntry.getKey(), lessonEntry.getValue(), 0L);
                }
            }

            log.info("✅ Инициализировано дефолтное расписание в Supabase");

        } catch (Exception e) {
            log.error("❌ Ошибка инициализации дефолтного расписания в Supabase", e);
        }
    }

    private Map<DayOfWeek, Map<String, String>> createDefaultSchedule() {
        Map<DayOfWeek, Map<String, String>> schedule = new HashMap<>();

        // Понедельник
        Map<String, String> monday = new HashMap<>();
        monday.put("morning", "8:00 - 11:30 - Майсор класс");
        monday.put("evening", "17:00 - 20:30 - Майсор класс");
        schedule.put(DayOfWeek.MONDAY, monday);

        // Вторник
        Map<String, String> tuesday = new HashMap<>();
        tuesday.put("morning", "8:00 - 11:30 - Майсор класс");
        tuesday.put("evening", "18:30 - 20:00 - Майсор класс");
        schedule.put(DayOfWeek.TUESDAY, tuesday);

        // Среда
        Map<String, String> wednesday = new HashMap<>();
        wednesday.put("morning", "8:00 - 11:30 - Майсор класс");
        wednesday.put("evening", "17:00 - 20:30 - Майсор класс");
        schedule.put(DayOfWeek.WEDNESDAY, wednesday);

        // Четверг
        Map<String, String> thursday = new HashMap<>();
        thursday.put("morning", "8:00 - 11:30 - Майсор класс");
        thursday.put("evening", "17:00 - 20:30 - Майсор класс");
        schedule.put(DayOfWeek.THURSDAY, thursday);

        // Пятница
        Map<String, String> friday = new HashMap<>();
        friday.put("morning", "8:00 - 11:30 - Майсор класс");
        friday.put("evening", "17:00 - 20:30 - Майсор класс");
        schedule.put(DayOfWeek.FRIDAY, friday);

        // Суббота
        Map<String, String> saturday = new HashMap<>();
        saturday.put("morning", "ОТДЫХ");
        saturday.put("evening", "ОТДЫХ");
        schedule.put(DayOfWeek.SATURDAY, saturday);

        // Воскресенье
        Map<String, String> sunday = new HashMap<>();
        sunday.put("morning", "10:00 - 11:30 LED-КЛАСС");
        sunday.put("evening", "Отдых");
        schedule.put(DayOfWeek.SUNDAY, sunday);

        return schedule;
    }

    // === МЕТОДЫ ДЛЯ РАБОТЫ С ЗАПИСЯМИ ===

    public boolean registerUser(Long userId, String username, String displayName,
                                LocalDate lessonDate, String lessonType) {
        try {
            String url = supabaseUrl + "/rest/v1/registrations";

            Map<String, Object> data = new HashMap<>();
            data.put("user_id", userId);
            data.put("username", username);
            data.put("display_name", displayName);
            data.put("lesson_date", lessonDate.toString());
            data.put("lesson_type", lessonType);

            String response = webClient.post()
                    .uri(url)
                    .header("Prefer", "resolution=ignore-duplicates")
                    .bodyValue(data)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            boolean success = response != null && !response.contains("error");
            log.info("✅ Пользователь {} записан на {} {}", displayName, lessonDate, lessonType);
            return success;

        } catch (Exception e) {
            log.error("❌ Ошибка регистрации пользователя", e);
            return false;
        }
    }

    public boolean cancelRegistration(Long userId, LocalDate lessonDate, String lessonType) {
        try {
            String url = supabaseUrl + "/rest/v1/registrations?user_id=eq." + userId +
                    "&lesson_date=eq." + lessonDate + "&lesson_type=eq." + lessonType;

            String response = webClient.delete()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            boolean success = response != null;
            log.info("✅ Пользователь {} отменил запись на {} {}", userId, lessonDate, lessonType);
            return success;

        } catch (Exception e) {
            log.error("❌ Ошибка отмены регистрации", e);
            return false;
        }
    }

    public Map<String, List<String>> getRegistrationsForDate(LocalDate date) {
        Map<String, List<String>> registrations = new HashMap<>();
        registrations.put("morning", new ArrayList<>());
        registrations.put("evening", new ArrayList<>());

        try {
            String url = supabaseUrl + "/rest/v1/registrations?lesson_date=eq." + date + "&select=lesson_type,display_name&order=created_at";

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode jsonNode = objectMapper.readTree(response);

            if (jsonNode.isArray()) {
                for (JsonNode node : jsonNode) {
                    String lessonType = node.get("lesson_type").asText();
                    String displayName = node.get("display_name").asText();

                    if (registrations.containsKey(lessonType)) {
                        registrations.get(lessonType).add(displayName);
                    }
                }
            }

            log.info("✅ Загружены записи на {}: утро={}, вечер={}",
                    date, registrations.get("morning").size(), registrations.get("evening").size());

        } catch (Exception e) {
            log.error("❌ Ошибка получения записей на дату: {}", date, e);
        }

        return registrations;
    }

    // === МЕТОДЫ ДЛЯ РАБОТЫ С СООБЩЕНИЯМИ ===

    public void saveMessageId(Integer messageId, String lessonType, LocalDate lessonDate, String messageText) {
        try {
            String url = supabaseUrl + "/rest/v1/channel_messages";

            Map<String, Object> data = new HashMap<>();
            data.put("message_id", messageId);
            data.put("lesson_type", lessonType);
            data.put("lesson_date", lessonDate.toString());
            data.put("message_text", messageText);

            webClient.post()
                    .uri(url)
                    .header("Prefer", "resolution=merge-duplicates")
                    .bodyValue(data)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnSuccess(response -> log.info("💾 Сохранен ID сообщения: {} для {} занятия на {}",
                            messageId, lessonType, lessonDate))
                    .doOnError(error -> log.error("❌ Ошибка сохранения ID сообщения", error))
                    .block();

        } catch (Exception e) {
            log.error("❌ Ошибка сохранения ID сообщения в Supabase", e);
        }
    }

    public List<Map<String, Object>> getMessagesForDeletion(LocalDate date, String lessonType) {
        try {
            String url = supabaseUrl + "/rest/v1/channel_messages?lesson_date=eq." + date +
                    "&lesson_type=eq." + lessonType + "&select=message_id,message_text";

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode jsonNode = objectMapper.readTree(response);
            List<Map<String, Object>> messages = new ArrayList<>();

            if (jsonNode.isArray()) {
                for (JsonNode node : jsonNode) {
                    Map<String, Object> message = new HashMap<>();
                    message.put("message_id", node.get("message_id").asInt());
                    message.put("message_text", node.get("message_text").asText());
                    messages.add(message);
                }
            }

            return messages;

        } catch (Exception e) {
            log.error("❌ Ошибка получения сообщений для удаления", e);
            return new ArrayList<>();
        }
    }

    public void deleteMessageRecord(Integer messageId, LocalDate date, String lessonType) {
        try {
            String url = supabaseUrl + "/rest/v1/channel_messages?message_id=eq." + messageId +
                    "&lesson_date=eq." + date + "&lesson_type=eq." + lessonType;

            webClient.delete()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnSuccess(response -> log.info("✅ Запись сообщения {} удалена из БД", messageId))
                    .doOnError(error -> log.error("❌ Ошибка удаления записи сообщения", error))
                    .block();

        } catch (Exception e) {
            log.error("❌ Ошибка удаления записи сообщения из Supabase", e);
        }
    }

    // === МЕТОДЫ ДЛЯ УПРАВЛЕНИЯ УВЕДОМЛЕНИЯМИ ===

    /**
     * Инициализация базы данных
     */
    public void initializeDatabase() {
        try {
            log.info("🔄 Инициализация базы данных Supabase...");
            // Проверяем и создаем таблицы если нужно
            initializeDefaultSchedule();
            log.info("✅ База данных Supabase инициализирована");
        } catch (Exception e) {
            log.error("❌ Ошибка инициализации базы данных Supabase", e);
        }
    }

    /**
     * Получение статуса уведомлений в текстовом формате
     */
    public String getNotificationsStatus() {
        try {
            boolean enabled = areNotificationsEnabled();
            return enabled ? "ВКЛЮЧЕНЫ ✅" : "ВЫКЛЮЧЕНЫ ❌";
        } catch (Exception e) {
            log.error("❌ Ошибка получения статуса уведомлений", e);
            return "ОШИБКА ❌";
        }
    }

    /**
     * Принудительное включение уведомлений
     */
    public boolean forceEnableNotifications() {
        return setNotificationsState(true);
    }

    /**
     * Принудительное выключение уведомлений
     */
    public boolean forceDisableNotifications() {
        return setNotificationsState(false);
    }

    /**
     * Установка состояния уведомлений
     */
    private boolean setNotificationsState(boolean enabled) {
        try {
            log.info("🔄 Установка состояния уведомлений: {}", enabled);

            String url = supabaseUrl + "/rest/v1/bot_settings?on_conflict=id";

            Map<String, Object> data = Map.of(
                    "id", 1,
                    "notifications_enabled", enabled,
                    "updated_at", OffsetDateTime.now().toString()
            );

            String response = webClient.post()
                    .uri(url)
                    .header("Prefer", "resolution=merge-duplicates")
                    .bodyValue(data)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorReturn("ERROR")
                    .block();

            boolean success = response != null && !response.contains("error");

            if (success) {
                log.info("✅ Уведомления {} {}", enabled ? "ВКЛЮЧЕНЫ" : "ВЫКЛЮЧЕНЫ");
            } else {
                log.error("❌ Ошибка установки состояния уведомлений. Ответ: {}", response);
            }

            return success;

        } catch (Exception e) {
            log.error("❌ Критическая ошибка установки состояния уведомлений: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Проверка включены ли уведомления
     */
    public boolean areNotificationsEnabled() {
        try {
            String url = supabaseUrl + "/rest/v1/bot_settings?id=eq.1&select=notifications_enabled";

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorReturn("[]")
                    .block();

            log.info("🔍 Ответ от Supabase: {}", response);

            if (response != null && response.startsWith("[") && response.length() > 2) {
                JsonNode jsonNode = objectMapper.readTree(response);
                if (jsonNode.isArray() && jsonNode.size() > 0) {
                    boolean enabled = jsonNode.get(0).get("notifications_enabled").asBoolean();
                    log.info("✅ Статус уведомлений из БД: {}", enabled);
                    return enabled;
                }
            }

            // Если записи нет - создаем её
            log.info("📝 Запись не найдена, создаем новую с уведомлениями ВКЛ");
            setNotificationsState(true);
            return true;

        } catch (Exception e) {
            log.error("❌ Ошибка проверки настроек уведомлений: {}", e.getMessage());
            // Возвращаем true по умолчанию
            return true;
        }
    }

    /**
     * Переключение уведомлений (старый метод для обратной совместимости)
     */
    public boolean toggleNotifications() {
        try {
            boolean currentState = areNotificationsEnabled();
            boolean newState = !currentState;

            boolean success = setNotificationsState(newState);

            if (success) {
                log.info("✅ Уведомления переключены: {} -> {}",
                        currentState ? "ВКЛ" : "ВЫКЛ",
                        newState ? "ВКЛ" : "ВЫКЛ");
            }

            return success;

        } catch (Exception e) {
            log.error("❌ Ошибка переключения уведомлений: {}", e.getMessage());
            return areNotificationsEnabled();
        }
    }
}