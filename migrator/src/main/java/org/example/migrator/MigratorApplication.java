package org.example.migrator;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * Консольний застосунок міграцій: застосовує SQL-міграції з db/migration
 * до бази даних, указаної змінними оточення, і завершує роботу.
 * У Kubernetes запускається як Job перед стартом сервера.
 */
public class MigratorApplication {

    public static void main(String[] args) {
        String url = requiredEnv("DB_URL");
        String user = requiredEnv("DB_USER");
        String password = requiredEnv("DB_PASSWORD");
        int connectRetries = Integer.parseInt(System.getenv().getOrDefault("DB_CONNECT_RETRIES", "60"));

        System.out.println("Applying migrations to " + url);

        Flyway flyway = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .connectRetries(connectRetries)
                .load();

        MigrateResult result = flyway.migrate();

        System.out.printf("Migrations executed: %d, current schema version: %s%n",
                result.migrationsExecuted,
                result.targetSchemaVersion != null ? result.targetSchemaVersion : result.initialSchemaVersion);
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            System.err.println("Environment variable " + name + " is not set");
            System.exit(1);
        }
        return value;
    }
}
