package am.pulsar.tax;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Pulsar Tax API — модульный монолит.
 *
 * Модули (пакеты): identity — вход и сессии; business — профиль ИП; accounting — доходы, расходы,
 * импорт выписок; tax — налоговые правила, сводка, уплаты; invoicing — счета; reminders — сроки,
 * календарь, Telegram; integrations — внешние API (ЦБ РА); export — выгрузки; legacy — перенос данных
 * старой версии; shared и web — общее.
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
