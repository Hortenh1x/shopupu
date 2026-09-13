package com.example.shopupu.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shopupu.support.PostgresContainerSupport;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;

/** Exercises actual PostgreSQL role/ownership semantics on the disposable Testcontainer. */
@SpringBootTest
@ActiveProfiles("test")
class ProductionDatabaseGuardIT extends PostgresContainerSupport {
    @Autowired DataSource dataSource;

    @Test
    void acceptsDmlRoleAndRejectsSchemaCreateOwnershipAndInheritedRoles() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String suffix = UUID.randomUUID().toString().replace("-", "");
                String runtime = "guard_runtime_" + suffix;
                String parent = "guard_parent_" + suffix;
                String table = "guard_table_" + suffix;
                JdbcClient jdbc = JdbcClient.create(new SingleConnectionDataSource(connection, true));
                jdbc.sql("CREATE ROLE " + runtime + " NOLOGIN NOINHERIT").update();
                jdbc.sql("CREATE ROLE " + parent + " NOLOGIN").update();
                jdbc.sql("REVOKE CREATE ON SCHEMA public FROM PUBLIC").update();
                jdbc.sql("GRANT USAGE ON SCHEMA public TO " + runtime).update();
                jdbc.sql("CREATE TABLE public." + table + " (id bigint)").update();
                jdbc.sql("GRANT SELECT, INSERT, UPDATE, DELETE ON public." + table + " TO " + runtime).update();
                ProductionDatabaseGuard guard = new ProductionDatabaseGuard(jdbc);

                jdbc.sql("SET LOCAL ROLE " + runtime).update();
                assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
                jdbc.sql("INSERT INTO public." + table + " VALUES (1)").update();
                jdbc.sql("RESET ROLE").update();

                jdbc.sql("GRANT CREATE ON SCHEMA public TO " + runtime).update();
                jdbc.sql("SET LOCAL ROLE " + runtime).update();
                assertRejected(guard);
                jdbc.sql("RESET ROLE").update();
                jdbc.sql("REVOKE CREATE ON SCHEMA public FROM " + runtime).update();

                jdbc.sql("GRANT " + parent + " TO " + runtime).update();
                jdbc.sql("SET LOCAL ROLE " + runtime).update();
                assertRejected(guard);
                jdbc.sql("RESET ROLE").update();
                jdbc.sql("REVOKE " + parent + " FROM " + runtime).update();

                jdbc.sql("ALTER TABLE public." + table + " OWNER TO " + runtime).update();
                jdbc.sql("SET LOCAL ROLE " + runtime).update();
                assertRejected(guard);
            } finally {
                // Roles, grants, fixture table and SET LOCAL ROLE roll back together.
                connection.rollback();
                connection.setAutoCommit(true);
            }
        }
    }

    @Test
    void rejectsTheTestContainerAdministratorAsAProductionRuntime() {
        assertRejected(new ProductionDatabaseGuard(JdbcClient.create(dataSource)));
    }

    private static void assertRejected(ProductionDatabaseGuard guard) {
        assertThatThrownBy(() -> guard.run(null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dedicated runtime database role");
    }
}
