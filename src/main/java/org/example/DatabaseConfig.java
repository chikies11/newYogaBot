package org.example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;

@Configuration
public class DatabaseConfig {

    @Value("${JDBC_DATABASE_URL:}")
    private String jdbcUrl;

    @Value("${DATABASE_USER:}")
    private String dbUser;

    @Value("${DATABASE_PASSWORD:}")
    private String dbPassword;

    @Bean
    public DataSource dataSource() {
        String url = jdbcUrl;
        if (url.startsWith("postgresql://")) {
            url = url.replace("postgresql://", "jdbc:postgresql://");
        }

        return DataSourceBuilder.create()
                .url(url)
                .username(dbUser)
                .password(dbPassword)
                .driverClassName("org.postgresql.Driver")
                .build();
    }
}