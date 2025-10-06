package org.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        log.info("🔗 Инициализация базы данных с connection pooling...");
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            // Проверяем соединение
            jdbcTemplate.execute("SELECT 1");

            // Создаем таблицы если нужно
            createTablesIfNotExists();

            log.info("✅ База данных инициализирована с connection pooling");

        } catch (Exception e) {
            log.error("❌ Ошибка инициализации базы данных", e);
        }
    }

    @Transactional
    public void logAdminAction(Long adminId, String action, String details) {
        try {
            jdbcTemplate.update("""
                CREATE TABLE IF NOT EXISTS admin_actions (
                    id BIGSERIAL PRIMARY KEY,
                    admin_id BIGINT NOT NULL,
                    action VARCHAR(100) NOT NULL,
                    details TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            jdbcTemplate.update("""
                INSERT INTO admin_actions (admin_id, action, details) 
                VALUES (?, ?, ?)
            """, adminId, action, details);

            log.info("👨‍💼 Действие админа {}: {} - {}", adminId, action, details);
        } catch (Exception e) {
            log.error("❌ Ошибка логирования действия админа", e);
        }
    }

    private void createTablesIfNotExists() {
        try {
            // Удаляем старую таблицу если она существует со старой структурой
            try {
                jdbcTemplate.execute("DROP TABLE IF EXISTS lessons");
                log.info("🗑️ Удалена старая таблица lessons");
            } catch (Exception e) {
                log.info("ℹ️ Старой таблицы lessons не существует или уже удалена");
            }

            // СОЗДАЕМ ТАБЛИЦУ С ПРАВИЛЬНОЙ СТРУКТУРОЙ
            jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS lessons (
                id BIGSERIAL PRIMARY KEY,
                day_of_week VARCHAR(20) NOT NULL,
                lesson_type VARCHAR(10) NOT NULL,
                description TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(day_of_week, lesson_type)
            )
        """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS registrations (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    username VARCHAR(255),
                    display_name VARCHAR(255) NOT NULL,
                    lesson_date DATE NOT NULL,
                    lesson_type VARCHAR(10) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(user_id, lesson_date, lesson_type)
                )
            """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS bot_settings (
                    id INTEGER PRIMARY KEY,
                    notifications_enabled BOOLEAN DEFAULT true,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Инициализируем настройки по умолчанию
            jdbcTemplate.update("""
                INSERT INTO bot_settings (id, notifications_enabled) 
                VALUES (1, true) 
                ON CONFLICT (id) DO NOTHING
            """);

            log.info("✅ Таблицы базы данных проверены/созданы");

        } catch (Exception e) {
            log.error("❌ Ошибка создания таблиц", e);
        }
    }

    // === МЕТОДЫ ДЛЯ РАСПИСАНИЯ ===

    @Transactional
    public void saveSchedule(DayOfWeek dayOfWeek, String lessonType, String description, Long adminId) {
        try {
            String oldDescription = null;
            try {
                oldDescription = jdbcTemplate.queryForObject(
                        "SELECT description FROM lessons WHERE day_of_week = ? AND lesson_type = ?",
                        String.class, dayOfWeek.toString(), lessonType);
            } catch (EmptyResultDataAccessException e) {
                // Если записи нет - это нормально, оставляем oldDescription = null
                oldDescription = null;
            }

            jdbcTemplate.update("""
                INSERT INTO lessons (day_of_week, lesson_type, description) 
                VALUES (?, ?, ?)
                ON CONFLICT (day_of_week, lesson_type) 
                DO UPDATE SET description = ?, updated_at = CURRENT_TIMESTAMP
            """, dayOfWeek.toString(), lessonType, description, description);

            // Логируем действие админа
            String action = "CHANGE_SCHEDULE";
            String details = String.format("День: %s, Время: %s, Было: %s, Стало: %s",
                    dayOfWeek, lessonType, oldDescription != null ? oldDescription : "пусто", description);

            logAdminAction(adminId, action, details);

            log.info("✅ Расписание сохранено админом {}: {} {} - {}", adminId, dayOfWeek, lessonType, description);
        } catch (Exception e) {
            log.error("❌ Ошибка сохранения расписания", e);
        }
    }

    public Map<DayOfWeek, Map<String, String>> loadSchedule() {
        Map<DayOfWeek, Map<String, String>> schedule = new HashMap<>();

        try {
            // Просто пытаемся загрузить данные, если таблицы нет - вернется пустой результат
            jdbcTemplate.query("SELECT day_of_week, lesson_type, description FROM lessons",
                    rs -> {
                        while (rs.next()) {
                            try {
                                DayOfWeek dayOfWeek = DayOfWeek.valueOf(rs.getString("day_of_week"));
                                String lessonType = rs.getString("lesson_type");
                                String description = rs.getString("description");

                                schedule.computeIfAbsent(dayOfWeek, k -> new HashMap<>())
                                        .put(lessonType, description);
                            } catch (IllegalArgumentException e) {
                                log.warn("⚠️ Неизвестный день недели в БД: {}", rs.getString("day_of_week"));
                            }
                        }
                        return null;
                    });

            jdbcTemplate.query("SELECT day_of_week, lesson_type, description FROM lessons",
                    rs -> {
                        while (rs.next()) {
                            try {
                                DayOfWeek dayOfWeek = DayOfWeek.valueOf(rs.getString("day_of_week"));
                                String lessonType = rs.getString("lesson_type");
                                String description = rs.getString("description");

                                schedule.computeIfAbsent(dayOfWeek, k -> new HashMap<>())
                                        .put(lessonType, description);
                            } catch (IllegalArgumentException e) {
                                log.warn("⚠️ Неизвестный день недели в БД: {}", rs.getString("day_of_week"));
                            }
                        }
                        return null;
                    });

            log.info("✅ Загружено расписание из БД: {} записей", schedule.size());
        } catch (Exception e) {
            log.error("❌ Ошибка загрузки расписания", e);
        }

        return schedule;
    }

    public List<Map<String, Object>> getAdminLogs(int limit) {
        try {
            // Сначала создаем таблицу если её нет
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS admin_actions (
                    id BIGSERIAL PRIMARY KEY,
                    admin_id BIGINT NOT NULL,
                    action VARCHAR(100) NOT NULL,
                    details TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            return jdbcTemplate.queryForList("""
                SELECT admin_id, action, details, created_at 
                FROM admin_actions 
                ORDER BY created_at DESC 
                LIMIT ?
            """, limit);
        } catch (Exception e) {
            log.error("❌ Ошибка получения логов админа", e);
            return new ArrayList<>();
        }
    }

    public void initializeDefaultSchedule() {
        try {
            // Проверяем, есть ли уже записи
            Integer count = null;
            try {
                count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lessons", Integer.class);
                log.info("✅ В БД найдено {} записей расписания", count);
            } catch (Exception e) {
                log.warn("⚠️ Таблица lessons не существует, создаем...");
                createTablesIfNotExists();
                count = 0;
            }

            if (count != null && count > 0) {
                log.info("✅ В БД уже есть расписание, пропускаем инициализацию");
                return;
            }

            log.info("🔄 Инициализация дефолтного расписания в БД...");

            // Инициализируем дефолтное расписание
            Map<DayOfWeek, Map<String, String>> defaultSchedule = createDefaultSchedule();

            for (Map.Entry<DayOfWeek, Map<String, String>> dayEntry : defaultSchedule.entrySet()) {
                DayOfWeek dayOfWeek = dayEntry.getKey();
                Map<String, String> lessons = dayEntry.getValue();

                for (Map.Entry<String, String> lessonEntry : lessons.entrySet()) {
                    // Используем временный adminId = 0 для инициализации
                    saveSchedule(dayOfWeek, lessonEntry.getKey(), lessonEntry.getValue(), 0L);
                }
            }

            log.info("✅ Инициализировано дефолтное расписание в БД");

        } catch (Exception e) {
            log.error("❌ Ошибка инициализации дефолтного расписания", e);
        }
    }

    private Map<DayOfWeek, Map<String, String>> createDefaultSchedule() {
        Map<DayOfWeek, Map<String, String>> schedule = new HashMap<>();

        // Понедельник
        Map<String, String> monday = new HashMap<>();
        monday.put("morning", "8:00 - 11:30 - Майсор класс");
        monday.put("evening", "17:00 - 20:00 - Майсор класс");
        schedule.put(DayOfWeek.MONDAY, monday);

        // Вторник
        Map<String, String> tuesday = new HashMap<>();
        tuesday.put("morning", "8:00 - 11:30 - Майсор класс");
        tuesday.put("evening", "Отдых");
        schedule.put(DayOfWeek.TUESDAY, tuesday);

        // Среда
        Map<String, String> wednesday = new HashMap<>();
        wednesday.put("morning", "8:00 - 11:30 - Майсор класс");
        wednesday.put("evening", "18:30 - 20:00 - Майсор класс");
        schedule.put(DayOfWeek.WEDNESDAY, wednesday);

        // Четверг
        Map<String, String> thursday = new HashMap<>();
        thursday.put("morning", "8:00 - 11:30 - Майсор класс");
        thursday.put("evening", "17:00 - 20:00 - Майсор класс");
        schedule.put(DayOfWeek.THURSDAY, thursday);

        // Пятница
        Map<String, String> friday = new HashMap<>();
        friday.put("morning", "8:00 - 11:30 - Майсор класс");
        friday.put("evening", "17:00 - 20:00 - Майсор класс");
        schedule.put(DayOfWeek.FRIDAY, friday);

        // Суббота
        Map<String, String> saturday = new HashMap<>();
        saturday.put("morning", "ОТДЫХ");
        saturday.put("evening", "ОТДЫХ");
        schedule.put(DayOfWeek.SATURDAY, saturday);

        // Воскресенье
        Map<String, String> sunday = new HashMap<>();
        sunday.put("morning", "10:00 - 11:30 LED-КЛАСС\n11:30 - 12:00 Конференция (По необходимости)");
        sunday.put("evening", "Отдых");
        schedule.put(DayOfWeek.SUNDAY, sunday);

        return schedule;
    }

    // === СУЩЕСТВУЮЩИЕ МЕТОДЫ (остаются без изменений) ===

    public boolean areNotificationsEnabled() {
        try {
            Boolean enabled = jdbcTemplate.queryForObject(
                    "SELECT notifications_enabled FROM bot_settings WHERE id = 1",
                    Boolean.class
            );
            return enabled != null && enabled;
        } catch (EmptyResultDataAccessException e) {
            return true; // default value
        }
    }

    @Transactional
    public boolean toggleNotifications() {
        boolean currentState = areNotificationsEnabled();
        boolean newState = !currentState;

        jdbcTemplate.update("""
            INSERT INTO bot_settings (id, notifications_enabled) 
            VALUES (1, ?) 
            ON CONFLICT (id) DO UPDATE SET notifications_enabled = ?
        """, newState, newState);

        return newState;
    }

    @Transactional
    public boolean registerUser(Long userId, String username, String displayName,
                                LocalDate lessonDate, String lessonType) {
        try {
            int rows = jdbcTemplate.update("""
                INSERT INTO registrations (user_id, username, display_name, lesson_date, lesson_type) 
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (user_id, lesson_date, lesson_type) DO NOTHING
            """, userId, username, displayName, lessonDate, lessonType);

            return rows > 0;
        } catch (Exception e) {
            log.error("❌ Ошибка регистрации пользователя", e);
            return false;
        }
    }

    @Transactional
    public boolean cancelRegistration(Long userId, LocalDate lessonDate, String lessonType) {
        try {
            int rows = jdbcTemplate.update("""
                DELETE FROM registrations 
                WHERE user_id = ? AND lesson_date = ? AND lesson_type = ?
            """, userId, lessonDate, lessonType);

            return rows > 0;
        } catch (Exception e) {
            log.error("❌ Ошибка отмены регистрации", e);
            return false;
        }
    }

    public boolean isUserRegistered(Long userId, LocalDate lessonDate, String lessonType) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM registrations 
                WHERE user_id = ? AND lesson_date = ? AND lesson_type = ?
            """, Integer.class, userId, lessonDate, lessonType);

            return count != null && count > 0;
        } catch (Exception e) {
            log.error("❌ Ошибка проверки регистрации", e);
            return false;
        }
    }

    public Map<String, List<String>> getRegistrationsForDate(LocalDate date) {
        try {
            return jdbcTemplate.query("""
                SELECT lesson_type, display_name 
                FROM registrations 
                WHERE lesson_date = ? 
                ORDER BY lesson_type, created_at
            """, new Object[]{date}, rs -> {
                Map<String, List<String>> registrations = new HashMap<>();
                registrations.put("morning", new ArrayList<>());
                registrations.put("evening", new ArrayList<>());

                while (rs.next()) {
                    String lessonType = rs.getString("lesson_type");
                    String displayName = rs.getString("display_name");
                    if (registrations.containsKey(lessonType)) {
                        registrations.get(lessonType).add(displayName);
                    }
                }

                return registrations;
            });
        } catch (Exception e) {
            log.error("❌ Ошибка получения записей на дату: {}", date, e);
            Map<String, List<String>> emptyResult = new HashMap<>();
            emptyResult.put("morning", new ArrayList<>());
            emptyResult.put("evening", new ArrayList<>());
            return emptyResult;
        }
    }

    public int getRegistrationCount(LocalDate date, String lessonType) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM registrations 
                WHERE lesson_date = ? AND lesson_type = ?
            """, Integer.class, date, lessonType);

            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("❌ Ошибка получения количества регистраций", e);
            return 0;
        }
    }
}