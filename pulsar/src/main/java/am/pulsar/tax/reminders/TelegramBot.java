package am.pulsar.tax.reminders;

import am.pulsar.tax.identity.AccountService;
import am.pulsar.tax.identity.User;
import am.pulsar.tax.shared.PulsarProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Бот Telegram для напоминаний о сроках. Включается переменной PULSAR_TELEGRAM_TOKEN.
 *
 * Привязка: в настройках пользователь получает ссылку t.me/бот?start=КОД, бот получает «/start КОД»
 * через getUpdates (long polling, публичный адрес не нужен) и запоминает чат.
 */
@Component
public class TelegramBot {

    private static final Logger log = LoggerFactory.getLogger(TelegramBot.class);

    private final boolean enabled;
    private final String base;
    private final AccountService accounts;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile String username;
    private volatile boolean running;
    private long offset;

    public TelegramBot(PulsarProperties props, AccountService accounts, JsonMapper json) {
        this.enabled = props.telegramEnabled();
        this.base = props.telegramApi() + "/bot" + props.telegramToken() + "/";
        this.accounts = accounts;
        this.json = json;
    }

    public boolean enabled() {
        return enabled;
    }

    /** Имя бота для ссылки t.me; null, пока бот не ответил на getMe. */
    public String username() {
        return username;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled || running) return;
        running = true;
        Thread.ofVirtual().name("telegram").start(this::loop);
    }

    @jakarta.annotation.PreDestroy
    public void stop() {
        running = false;
    }

    private void loop() {
        while (running) {
            try {
                if (username == null) {
                    String name = call("getMe", Map.of(), 15).path("result").path("username").asString("");
                    if (!name.isEmpty()) username = name;
                }
                pollOnce(25);
            } catch (Exception e) {
                log.warn("Telegram: {}", e.getMessage());
                sleep(10_000);
            }
        }
    }

    /** Один запрос getUpdates: обрабатывает пришедшие сообщения. */
    void pollOnce(int timeoutSeconds) throws Exception {
        JsonNode res = call("getUpdates", Map.of("offset", offset, "timeout", timeoutSeconds,
                "allowed_updates", "[\"message\"]"), timeoutSeconds + 10);
        for (JsonNode update : res.path("result")) {
            offset = update.path("update_id").asLong() + 1;
            JsonNode msg = update.path("message");
            if (!msg.isObject()) continue;
            String text = msg.path("text").isString() ? msg.path("text").asString().trim() : "";
            handle(msg.path("chat").path("id").asLong(), text);
        }
    }

    private void handle(long chatId, String text) throws Exception {
        if (!text.startsWith("/start")) return;
        String code = text.length() > 6 ? text.substring(6).trim() : "";
        if (code.isEmpty()) {
            send(chatId, Deadlines.text("ru", "hello", Map.of()));
            return;
        }
        Optional<User> u = accounts.linkTelegram(code, chatId);
        send(chatId, u.isEmpty() ? Deadlines.text("ru", "badCode", Map.of()) : Deadlines.text(u.get().getLang(), "linked", Map.of()));
    }

    public void send(long chatId, String text) throws Exception {
        call("sendMessage", Map.of("chat_id", chatId, "text", text), 15);
    }

    private JsonNode call(String method, Map<String, Object> params, int timeoutSeconds) throws Exception {
        StringBuilder form = new StringBuilder();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (!form.isEmpty()) form.append('&');
            form.append(e.getKey()).append('=').append(URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + method))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode node = json.readTree(resp.body());
        if (!node.path("ok").asBoolean(false)) {
            throw new IllegalStateException(method + ": " + node.path("description").asString(""));
        }
        return node;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
