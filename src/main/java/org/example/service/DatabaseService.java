package org.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
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

    private void createTablesIfNotExists() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS lessons (
                    id BIGSERIAL PRIMARY KEY,
                    lesson_date DATE NOT NULL,
                    lesson_type VARCHAR(10) NOT NULL,
                    description TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(lesson_date, lesson_type)
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
        return jdbcTemplate.query("""
            SELECT lesson_type, display_name 
            FROM registrations 
            WHERE lesson_date = ? 
            ORDER BY lesson_type, created_at
        """, new Object[]{date}, rs -> {
            Map<String, List<String>> registrations = Map.of(
                    "morning", new java.util.ArrayList<>(),
                    "evening", new java.util.ArrayList<>()
            );

            while (rs.next()) {
                String lessonType = rs.getString("lesson_type");
                String displayName = rs.getString("display_name");
                registrations.get(lessonType).add(displayName);
            }

            return registrations;
        });
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