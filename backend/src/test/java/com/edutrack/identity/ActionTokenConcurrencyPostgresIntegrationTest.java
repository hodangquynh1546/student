package com.edutrack.identity;

import com.edutrack.security.JwtIssuer;
import com.edutrack.security.TokenCodec;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/** Checks that the repository's row lock makes a reset token single-use under concurrent requests. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActionTokenConcurrencyPostgresIntegrationTest {
    private PostgreSQLContainer<?> container;
    private String baseUrl, username, password, schema;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate db;

    @BeforeAll
    void setUp() throws Exception {
        baseUrl = System.getenv("EDUTRACK_TEST_DATABASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Set EDUTRACK_TEST_DATABASE_URL or provide Docker for PostgreSQL integration tests");
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
            baseUrl = container.getJdbcUrl();
            username = container.getUsername();
            password = container.getPassword();
        } else {
            username = System.getenv().getOrDefault("EDUTRACK_TEST_DATABASE_USER", "edutrack");
            password = System.getenv().getOrDefault("EDUTRACK_TEST_DATABASE_PASSWORD", "");
        }
        schema = "edutrack_token_it_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(baseUrl, username, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        var source = new DriverManagerDataSource(withSchema(baseUrl, schema), username, password);
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load().migrate();
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> source);
        context.register(JpaConfig.class);
        context.refresh();
        db = new JdbcTemplate(source);
    }

    @AfterAll
    void tearDown() throws Exception {
        if (context != null) context.close();
        if (schema != null) {
            try (var connection = DriverManager.getConnection(baseUrl, username, password);
                 var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
        if (container != null) container.stop();
    }

    @Test
    void concurrentResetConsumesTokenOnlyOnce() throws Exception {
        UUID userId = UUID.randomUUID();
        String raw = "reset-" + UUID.randomUUID();
        TokenCodec codec = new TokenCodec();
        db.update("INSERT INTO users(id,email,full_name,role,status,password_hash) VALUES (?,?,?,'STUDENT','ACTIVE',?)",
            userId, userId + "@example.test", "Token test learner", new BCryptPasswordEncoder().encode("initial-password-123"));
        db.update("INSERT INTO action_tokens(id,user_id,purpose,token_hash,expires_at) VALUES (?,?,'RESET_PASSWORD',?,?)",
            UUID.randomUUID(), userId, codec.hash(raw), java.sql.Timestamp.from(Instant.now().plusSeconds(600)));

        CountDownLatch firstInsideEncode = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        PasswordEncoder encoder = new PasswordEncoder() {
            private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();
            @Override public String encode(CharSequence value) {
                if (calls.incrementAndGet() == 1) {
                    firstInsideEncode.countDown();
                    try { if (!releaseFirst.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out releasing reset"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                }
                return delegate.encode(value);
            }
            @Override public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return delegate.matches(rawPassword, encodedPassword);
            }
        };
        var auth = new AuthService(context.getBean(UserAccountRepository.class),
            context.getBean(RefreshSessionRepository.class), context.getBean(ActionTokenRepository.class),
            encoder, mock(JwtIssuer.class), codec, mock(NotificationSender.class), 7, "http://localhost:5173");
        var transaction = new TransactionTemplate(context.getBean(JpaTransactionManager.class));

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> transaction.executeWithoutResult(s -> auth.resetPassword(raw, "first-password-123")));
            assertTrue(firstInsideEncode.await(5, TimeUnit.SECONDS), "First request did not acquire token lock");
            var second = pool.submit(() -> transaction.executeWithoutResult(s -> auth.resetPassword(raw, "second-password-123")));
            // Give the second request time to reach the locked token row before the first commits.
            Thread.sleep(250);
            assertFalse(second.isDone(), "Second request should wait for the first token transaction");
            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            var error = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> second.get(10, TimeUnit.SECONDS));
            assertInstanceOf(ResponseStatusException.class, error.getCause());
            assertEquals(400, ((ResponseStatusException) error.getCause()).getStatusCode().value());
        } finally {
            releaseFirst.countDown();
        }
        String hash = db.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, userId);
        assertTrue(new BCryptPasswordEncoder().matches("first-password-123", hash));
        assertFalse(new BCryptPasswordEncoder().matches("second-password-123", hash));
        assertEquals(1, calls.get());
        assertNotNull(db.queryForObject("SELECT consumed_at FROM action_tokens WHERE token_hash=?",
            java.sql.Timestamp.class, codec.hash(raw)));
    }

    private static String withSchema(String url, String schema) {
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = ActionTokenRepository.class)
    static class JpaConfig {
        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("com.edutrack.identity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(java.util.Map.of("hibernate.hbm2ddl.auto", "validate"));
            return factory;
        }
        @Bean
        JpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }
}
