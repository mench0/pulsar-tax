package am.pulsar.tax.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Встроенный PostgreSQL, если адрес базы не задан (PULSAR_DB_URL пуст): для разработки и сквозных тестов
 * приложения. Данные — в PULSAR_DATA_DIR/pg и сохраняются между запусками. На сервере задаётся настоящая база.
 */
@Configuration
@Conditional(EmbeddedDatabaseConfig.NoDatabaseUrl.class)
public class EmbeddedDatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedDatabaseConfig.class);

    static class NoDatabaseUrl implements Condition {
        @Override
        public boolean matches(ConditionContext ctx, AnnotatedTypeMetadata metadata) {
            String url = ctx.getEnvironment().getProperty("spring.datasource.url");
            return url == null || url.isBlank();
        }
    }

    @Bean(destroyMethod = "close")
    EmbeddedPostgres embeddedPostgres(PulsarProperties props) throws IOException {
        Path dir = props.dataDir().resolve("pg").toAbsolutePath();
        Files.createDirectories(dir.getParent());
        log.info("PULSAR_DB_URL не задан — встроенный PostgreSQL в {}", dir);
        return EmbeddedPostgres.builder()
                .setDataDirectory(dir)
                .setCleanDataDirectory(false)
                .start();
    }

    @Bean
    DataSource dataSource(EmbeddedPostgres pg) {
        return pg.getPostgresDatabase();
    }
}
