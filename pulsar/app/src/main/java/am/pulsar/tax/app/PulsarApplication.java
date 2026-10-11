package am.pulsar.tax.app;import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Pulsar Tax API. Модуль dto — модели запросов и ответов, модуль app — приложение.
 *
 * Пакеты app: controller — REST API (общие операции в AbstractCrudController); service — бизнес-операции
 * (AbstractReadOnlyService, AbstractCrudService), service.provider — создание и изменение сущностей из DTO
 * (EntityProvider, MapStruct), service.calculation — налоговые расчёты, сроки; entity — сущности JPA (BaseEntity);
 * repository — Spring Data JPA и QueryDSL; mapper.entity — сущность → DTO (EntityMapper, MapStruct);
 * mapper.filter — фильтр → предикат QueryDSL (SearchMapper); integration — ЦБ РА и Telegram; config — настройки,
 * Jackson, безопасность; migration — Java-миграции и перенос старых данных; exception — ошибки; utils — общее.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class PulsarApplication {

    public static void main(String[] args) {
        // Порт и адрес задаются только PULSAR_PORT и PULSAR_HOST. Системные свойства главнее переменных
        // окружения, поэтому чужая SERVER_PORT (её ставит, например, flutter test) их не перебьёт.
        pin("PULSAR_PORT", "server.port");
        pin("PULSAR_HOST", "server.address");
        SpringApplication.run(PulsarApplication.class, args);
    }

    private static void pin(String env, String property) {
        String v = System.getenv(env);
        if (v != null && !v.isBlank() && System.getProperty(property) == null) System.setProperty(property, v.trim());
    }
}
