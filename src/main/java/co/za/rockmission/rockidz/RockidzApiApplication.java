package co.za.rockmission.rockidz;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RockidzApiApplication {

    public static void main(String[] args) {
        applyRailwayDatabaseUrlFallback();
        SpringApplication.run(RockidzApiApplication.class, args);
    }

    private static void applyRailwayDatabaseUrlFallback() {
        // Respect explicit Spring datasource config when provided.
        if (hasText(System.getProperty("spring.datasource.url")) || hasText(System.getenv("SPRING_DATASOURCE_URL"))) {
            return;
        }

        String databaseUrl = System.getenv("DATABASE_URL");
        if (!hasText(databaseUrl)) {
            return;
        }

        try {
            URI uri = URI.create(databaseUrl);
            String scheme = uri.getScheme();
            if (!"postgres".equalsIgnoreCase(scheme) && !"postgresql".equalsIgnoreCase(scheme)) {
                return;
            }

            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String database = uri.getPath();
            if (!hasText(host) || !hasText(database) || "/".equals(database)) {
                return;
            }

            if (database.startsWith("/")) {
                database = database.substring(1);
            }

            String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
            if (hasText(uri.getQuery())) {
                jdbcUrl += "?" + uri.getQuery();
            }

            System.setProperty("spring.datasource.url", jdbcUrl);

            String userInfo = uri.getUserInfo();
            if (hasText(userInfo)) {
                String[] parts = userInfo.split(":", 2);
                if (parts.length > 0 && hasText(parts[0])
                    && !hasText(System.getProperty("spring.datasource.username"))
                    && !hasText(System.getenv("SPRING_DATASOURCE_USERNAME"))) {
                    System.setProperty("spring.datasource.username", decode(parts[0]));
                }
                if (parts.length == 2
                    && !hasText(System.getProperty("spring.datasource.password"))
                    && !hasText(System.getenv("SPRING_DATASOURCE_PASSWORD"))) {
                    System.setProperty("spring.datasource.password", decode(parts[1]));
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed DATABASE_URL and let default Spring config fail clearly.
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
