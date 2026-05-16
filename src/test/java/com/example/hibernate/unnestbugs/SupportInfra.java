/*
 * Shared bootstrap for the bug reproducer tests. Builds a Hibernate SessionFactory against a
 * locally-running PostgreSQL. See README for setup; each test class uses this via @BeforeAll
 * / @AfterAll.
 */
package com.example.hibernate.unnestbugs;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

final class SupportInfra {

    private static final String JDBC_URL = System.getProperty(
            "unnestbugs.jdbcUrl", "jdbc:postgresql://localhost:5432/unnestbugs");
    private static final String DB_USER = System.getProperty(
            "unnestbugs.dbUser", System.getProperty("user.name"));
    private static final String DB_PASSWORD = System.getProperty(
            "unnestbugs.dbPassword", "");

    private SupportInfra() {}

    static SessionFactory buildSessionFactory(Class<?>... annotatedClasses) {
        var cfg = new Configuration()
                .setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .setProperty("hibernate.connection.url", JDBC_URL)
                .setProperty("hibernate.connection.username", DB_USER)
                .setProperty("hibernate.connection.password", DB_PASSWORD)
                .setProperty("hibernate.connection.driver_class", "org.postgresql.Driver")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false");
        for (var c : annotatedClasses) {
            cfg.addAnnotatedClass(c);
        }
        return cfg.buildSessionFactory();
    }
}
