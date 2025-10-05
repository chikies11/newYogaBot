package org.example.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;

@Component
public class ConnectionPoolMonitor {

    @Autowired
    private DataSource dataSource;

    private static final Logger log = LoggerFactory.getLogger(ConnectionPoolMonitor.class);

    @Scheduled(fixedRate = 60000) // Каждую минуту
    public void logPoolStats() {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();

            log.info("🔄 Connection Pool Stats - Active: {}, Idle: {}, Total: {}, Waiting: {}",
                    pool.getActiveConnections(),
                    pool.getIdleConnections(),
                    pool.getTotalConnections(),
                    pool.getThreadsAwaitingConnection());
        }
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationStart() {
        log.info("✅ HikariCP Connection Pool initialized");
    }
}