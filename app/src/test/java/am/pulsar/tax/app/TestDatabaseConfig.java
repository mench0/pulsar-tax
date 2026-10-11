package am.pulsar.tax.app;import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import javax.sql.DataSource;

/**
 * PostgreSQL для тестов: настоящий сервер, поднятый на время прогона во временной папке.
 * Само приложение работает только с внешней базой (PULSAR_DB_URL).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestDatabaseConfig {

    @Bean(destroyMethod = "close")
    EmbeddedPostgres testPostgres() throws IOException {
        // Локаль UTF-8, как у PostgreSQL на сервере: иначе lower() не меняет кириллицу и поиск без учёта регистра не работает.
        return EmbeddedPostgres.builder().setLocaleConfig("locale", "C.UTF-8").start();
    }

    @Bean
    DataSource dataSource(EmbeddedPostgres pg) {
        return pg.getPostgresDatabase();
    }
}
