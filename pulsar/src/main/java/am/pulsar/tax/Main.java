package am.pulsar.tax;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Точка входа Pulsar Tax.
 *
 * Переменные окружения:
 *   PULSAR_PORT           — порт (по умолчанию 8080)
 *   PULSAR_HOST           — адрес (по умолчанию 127.0.0.1, только этот компьютер)
 *   PULSAR_DATA_DIR       — папка данных (по умолчанию data)
 *   PULSAR_SIGNUP         — open (по умолчанию) или closed: после первого пользователя регистрация закрыта
 *   PULSAR_SECURE_COOKIE  — 1, если сервер работает за HTTPS
 *   PULSAR_TELEGRAM_TOKEN — токен бота от @BotFather; без него напоминания в Telegram выключены
 *   PULSAR_ALLOWED_ORIGINS — адреса фронтенда через запятую, например https://app.pulsar.am
 *   PULSAR_CBA_URL         — адрес API курсов ЦБ РА (по умолчанию https://api.cba.am/exchangerates.asmx)
 *   PULSAR_COOKIE_SAMESITE — Strict (по умолчанию), Lax или None (фронтенд на другом домене, нужен HTTPS)
 */
public final class Main {

    /** Сроки и «сегодня» считаются по времени Еревана, где бы ни стоял сервер. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Yerevan");
    /** Час по Еревану, после которого рассылаются напоминания. */
    static final int REMIND_HOUR = 10;

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(env("PULSAR_PORT", "8080"));
        String host = env("PULSAR_HOST", "127.0.0.1");
        Path dir = Path.of(env("PULSAR_DATA_DIR", "data"));
        Server.Options options = new Server.Options(
                env("PULSAR_SECURE_COOKIE", "0").equals("1"),
                !env("PULSAR_SIGNUP", "open").equals("closed"),
                env("PULSAR_COOKIE_SAMESITE", "Strict"),
                origins(env("PULSAR_ALLOWED_ORIGINS", "")),
                env("PULSAR_CBA_URL", Rates.DEFAULT_BASE));

        // Файл данных версии без учётных записей переходит к первому зарегистрированному.
        Accounts accounts = new Accounts(dir, dir.resolve("pulsar.json"));
        String token = env("PULSAR_TELEGRAM_TOKEN", "");
        Telegram telegram = token.isEmpty() ? null : new Telegram("https://api.telegram.org", token, accounts);

        Server server = new Server(accounts, telegram, options, host, port);
        server.start();
        if (telegram != null) telegram.start();

        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        timer.scheduleAtFixedRate(() -> hourly(dir, telegram), 1, 60, TimeUnit.MINUTES);

        // Вывод латиницей, чтобы консоль с любой кодировкой показала его без искажений.
        System.out.println("Pulsar Tax API: http://" + (host.equals("0.0.0.0") ? "localhost" : host)
                + ":" + server.port());
        System.out.println("Allowed origins: " + (options.allowedOrigins().isEmpty()
                ? "local addresses only" : String.join(", ", options.allowedOrigins())));
        System.out.println("Data dir: " + dir.toAbsolutePath());
        System.out.println("Telegram reminders: " + (telegram == null ? "off" : "on"));
    }

    /** Раз в час: резервная копия за день и, после 10:00 по Еревану, напоминания. */
    private static void hourly(Path dir, Telegram telegram) {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        LocalDate today = now.toLocalDate();
        try {
            Backups.run(dir, today);
        } catch (Exception e) {
            System.err.println("Backup failed: " + e.getMessage());
        }
        if (telegram != null && now.getHour() >= REMIND_HOUR) telegram.sendReminders(today);
    }

    private static Set<String> origins(String list) {
        Set<String> set = new LinkedHashSet<>();
        for (String o : list.split(",")) {
            String t = o.trim();
            while (t.endsWith("/")) t = t.substring(0, t.length() - 1);
            if (!t.isEmpty()) set.add(t);
        }
        return set;
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
