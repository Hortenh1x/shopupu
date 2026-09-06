package com.example.shopupu.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresContainerSupport {

    /**
     * postgres:18 + pgvector: the V15 migration runs CREATE EXTENSION vector.
     *
     * <p>Deliberately not annotated {@code @Container}: JUnit would stop it after the
     * first IT class and start a fresh one — on a new random port — for the next,
     * while Spring hands that class its cached context whose datasource still points
     * at the old port. Every IT class after the first then failed with "Failed to
     * obtain JDBC Connection". One container per JVM instead; Ryuk removes it on exit.
     */
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("shopupu_test")
            .withUsername("shopupu")
            .withPassword("shopupu");

    @DynamicPropertySource
    // handles registerPostgresProperties. Starts the container lazily, so a machine
    // without Docker still skips the ITs instead of failing at class initialisation.
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
