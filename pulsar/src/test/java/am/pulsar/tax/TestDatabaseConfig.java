package am.pulsar.tax;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.io.IOException;

/**
 * PostgreSQL для тестов: настоящий сервер, поднятый на время прогона во временной папке.
 * Само приложение работает только с внешней базой (PULSAR_DB_URL).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestDatabaseConfig {

    @Bean(destroyMethod = "close")
    EmbeddedPostgres testPostgres() throws IOException {
        return EmbeddedPostgres.builder().start();
    }

    @Bean
    DataSource dataSource(EmbeddedPostgres pg) {
        return pg.getPostgresDatabase();
    }
}
