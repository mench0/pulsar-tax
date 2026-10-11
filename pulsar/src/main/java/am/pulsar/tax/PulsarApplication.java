package am.pulsar.tax;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Pulsar Tax API — модульный монолит.
 *
 * Пакеты по роли классов: controller — REST API; service — бизнес-операции и транзакции; entity — сущности
 * JPA; repository — доступ к базе; domain — чистые расчёты (налоги, сводка, сроки, CSV); client — внешние API
 * (ЦБ РА, Telegram); security — вход, сессии, защита от чужих сайтов; config — настройки и бины; job — задачи
 * по расписанию и при запуске; exception — ошибки и их ответы; util — разбор полей, время Еревана.
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
