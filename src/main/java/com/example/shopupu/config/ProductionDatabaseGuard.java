package com.example.shopupu.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** DDL credentials belong to the deployment process, never the public runtime. */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionDatabaseGuard implements ApplicationRunner {
    private final JdbcClient jdbc;

    @Override
    public void run(ApplicationArguments args) {
        boolean privileged = jdbc.sql("""
                select r.rolsuper or r.rolcreatedb or r.rolcreaterole or r.rolreplication or r.rolbypassrls
                    or has_schema_privilege(current_user, 'public', 'CREATE')
                    or exists (select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
                        where n.nspname = 'public' and c.relowner = r.oid)
                    or exists (select 1 from pg_database d where d.datname = current_database() and d.datdba = r.oid)
                    or exists (select 1 from pg_roles parent where parent.rolname <> current_user
                        and pg_has_role(current_user, parent.oid, 'MEMBER'))
                from pg_roles r where r.rolname = current_user
                """).query(Boolean.class).single();
        if (privileged) throw new IllegalStateException(
                "Production requires a dedicated runtime database role without DDL or role membership; see docs/database-recovery.md");
    }
}
