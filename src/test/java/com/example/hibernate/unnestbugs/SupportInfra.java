/*
 * Shared bootstrap for the bug reproducer tests. Builds a Hibernate SessionFactory against
 * an in-memory H2 database. Each test class uses this via @BeforeAll / @AfterAll.
 */
package com.example.hibernate.unnestbugs;

import java.sql.Connection;
import java.sql.SQLException;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;

final class SupportInfra {

    /** No-op ConnectionProvider — used by tests that don't need to execute SQL. */
    public static final class StubConnectionProvider implements ConnectionProvider {
        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("StubConnectionProvider: no live database in this test");
        }

        @Override
        public void closeConnection(Connection conn) {}

        @Override
        public boolean supportsAggressiveRelease() {
            return false;
        }

        @Override
        public boolean isUnwrappableAs(Class<?> unwrapType) {
            return false;
        }

        @Override
        public <T> T unwrap(Class<T> unwrapType) {
            throw new UnsupportedOperationException();
        }
    }

    private SupportInfra() {}

    static SessionFactory buildSessionFactory(Class<?>... annotatedClasses) {
        var cfg = new Configuration()
                .setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:unnest-bugs-" + System.nanoTime())
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false");
        for (var c : annotatedClasses) {
            cfg.addAnnotatedClass(c);
        }
        return cfg.buildSessionFactory();
    }

    /**
     * Variant that builds a SessionFactory against {@code PostgreSQLDialect}, with JDBC metadata
     * access disabled so no live PG server is required. Use this when the test depends on
     * {@code @Struct} support (which H2 lacks). HQL→SQM→SQL-AST conversion is exercised; actual
     * SQL execution would still need a real server.
     */
    static SessionFactory buildPostgresSessionFactory(Class<?>... annotatedClasses) {
        var cfg = new Configuration()
                .setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .setProperty("hibernate.boot.allow_jdbc_metadata_access", "false")
                .setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false")
                .setProperty("hibernate.show_sql", "false")
                .setProperty("hibernate.connection.provider_class", StubConnectionProvider.class.getName());
        for (var c : annotatedClasses) {
            cfg.addAnnotatedClass(c);
        }
        return cfg.buildSessionFactory();
    }
}
