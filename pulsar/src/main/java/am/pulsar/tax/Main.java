package am.pulsar.tax;

import java.nio.file.Path;

/**
 * Точка входа Pulsar Tax.
 *
 * Переменные окружения:
 *   PULSAR_PORT — порт (по умолчанию 8080)
 *   PULSAR_HOST — адрес (по умолчанию 127.0.0.1, только этот компьютер)
 *   PULSAR_DATA — файл данных (по умолчанию data/pulsar.json)
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(env("PULSAR_PORT", "8080"));
        String host = env("PULSAR_HOST", "127.0.0.1");
        Path data = Path.of(env("PULSAR_DATA", "data/pulsar.json"));

        Server server = new Server(new Store(data), host, port);
        server.start();
        // Вывод латиницей, чтобы консоль с любой кодировкой показала его без искажений.
        System.out.println("Pulsar Tax: http://" + (host.equals("0.0.0.0") ? "localhost" : host)
                + ":" + server.port());
        System.out.println("Data file: " + data.toAbsolutePath());
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
