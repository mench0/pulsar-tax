package am.pulsar.tax;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Бот Telegram для напоминаний о сроках. Включается переменной PULSAR_TELEGRAM_TOKEN.
 *
 * Привязка: в настройках пользователь получает ссылку t.me/бот?start=КОД, бот получает
 * «/start КОД» через getUpdates (long polling, без публичного адреса) и запоминает чат.
 */
final class Telegram {

    private final String base;
    private final Accounts accounts;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile String username;
    private volatile boolean running;
    private long offset;

    /** @param apiBase https://api.telegram.org (в тестах — локальная заглушка) */
    Telegram(String apiBase, String token, Accounts accounts) {
        this.base = apiBase + "/bot" + token + "/";
        this.accounts = accounts;
    }

    /** Имя бота для ссылки t.me; null, пока бот не ответил на getMe. */
    String username() {
        return username;
    }

    void start() {
        running = true;
        Thread.ofVirtual().name("telegram").start(this::loop);
    }

    void stop() {
        running = false;
    }

    private void loop() {
        while (running) {
            try {
                if (username == null) username = (String) result(call("getMe", Map.of(), 15)).get("username");
                pollOnce(25);
            } catch (Exception e) {
                System.err.println("Telegram: " + e.getMessage());
                sleep(10_000);
            }
        }
    }

    /** Один запрос getUpdates: обрабатывает пришедшие сообщения. */
    @SuppressWarnings("unchecked")
    void pollOnce(int timeoutSeconds) throws Exception {
        Map<String, Object> res = call("getUpdates", Map.of("offset", offset, "timeout", timeoutSeconds,
                "allowed_updates", "[\"message\"]"), timeoutSeconds + 10);
        for (Object o : (List<Object>) res.get("result")) {
            Map<String, Object> update = (Map<String, Object>) o;
            offset = ((BigDecimal) update.get("update_id")).longValueExact() + 1;
            if (!(update.get("message") instanceof Map<?, ?> msg)) continue;
            Object text = msg.get("text");
            Map<String, Object> chat = (Map<String, Object>) msg.get("chat");
            long chatId = ((BigDecimal) chat.get("id")).longValueExact();
            handle(chatId, text instanceof String s ? s.trim() : "");
        }
    }

    private void handle(long chatId, String text) throws Exception {
        if (!text.startsWith("/start")) return;
        String code = text.length() > 6 ? text.substring(6).trim() : "";
        if (code.isEmpty()) {
            send(chatId, Deadlines.text("ru", "hello", Map.of()));
            return;
        }
        Accounts.User u = accounts.linkTelegram(code, chatId);
        send(chatId, u == null ? Deadlines.text("ru", "badCode", Map.of()) : Deadlines.text(u.lang(), "linked", Map.of()));
    }

    void send(long chatId, String text) throws Exception {
        call("sendMessage", Map.of("chat_id", chatId, "text", text), 15);
    }

    /** Рассылает напоминания на сегодня тем, у кого подключён Telegram. Каждому — не чаще раза в день. */
    void sendReminders(LocalDate today) {
        for (Accounts.User u : accounts.users()) {
            if (u.telegramChatId() == null || today.equals(accounts.lastReminder(u))) continue;
            try {
                String text = Deadlines.reminder(accounts.store(u), u.lang(), today);
                if (text != null) send(u.telegramChatId(), text);
                accounts.markReminded(u, today);
            } catch (Exception e) {
                System.err.println("Telegram: напоминание пользователю " + u.id() + " не отправлено: " + e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(Map<String, Object> res) {
        return (Map<String, Object>) res.get("result");
    }

    private Map<String, Object> call(String method, Map<String, Object> params, int timeoutSeconds) throws Exception {
        StringBuilder form = new StringBuilder();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (!form.isEmpty()) form.append('&');
            form.append(e.getKey()).append('=')
                    .append(URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + method))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> json = Json.parseObject(resp.body());
        if (!Boolean.TRUE.equals(json.get("ok"))) {
            throw new IllegalStateException(method + ": " + json.get("description"));
        }
        return json;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
