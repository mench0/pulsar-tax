package am.pulsar.tax;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Заглушки внешних API: ЦБ РА и Telegram Bot API. */
final class Stubs {

    static final AtomicInteger cbaCalls = new AtomicInteger();
    static volatile boolean cbaDown;
    /** Сообщения, которые «пользователь» отправит боту. */
    static final BlockingQueue<String> incoming = new LinkedBlockingQueue<>();
    /** Сообщения, отправленные ботом (тело запроса sendMessage). */
    static final List<String> sent = new CopyOnWriteArrayList<>();
    static final HttpServer cba = start(Stubs::cba);
    static final HttpServer telegram = start(Stubs::telegram);
    private static int updateId;

    private Stubs() {
    }

    static String cbaUrl() {
        return "http://127.0.0.1:" + cba.getAddress().getPort() + "/exchangerates.asmx";
    }

    static String telegramUrl() {
        return "http://127.0.0.1:" + telegram.getAddress().getPort();
    }

    private interface Handler {
        String handle(String path, String body) throws Exception;
    }

    private static HttpServer start(Handler h) {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/", ex -> {
                try {
                    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String reply = h.handle(ex.getRequestURI().getPath(), body);
                    int status = reply == null ? 500 : 200;
                    byte[] b = (reply == null ? "down" : reply).getBytes(StandardCharsets.UTF_8);
                    ex.sendResponseHeaders(status, b.length);
                    ex.getResponseBody().write(b);
                } catch (Exception e) {
                    ex.sendResponseHeaders(500, -1);
                } finally {
                    ex.close();
                }
            });
            s.start();
            return s;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String cba(String path, String body) {
        cbaCalls.incrementAndGet();
        if (cbaDown) return null;
        return "<soap:Envelope><soap:Body><ExchangeRatesByDateResponse><ExchangeRatesByDateResult><Rates>"
                + "<ExchangeRate><ISO>USD</ISO><Amount>1</Amount><Rate>387.5</Rate></ExchangeRate>"
                + "<ExchangeRate><ISO>JPY</ISO><Amount>100</Amount><Rate>255.1</Rate></ExchangeRate>"
                + "</Rates></ExchangeRatesByDateResult></ExchangeRatesByDateResponse></soap:Body></soap:Envelope>";
    }

    private static String telegram(String path, String body) throws Exception {
        if (path.endsWith("/getMe")) return "{\"ok\":true,\"result\":{\"username\":\"pulsar_test_bot\"}}";
        if (path.endsWith("/sendMessage")) {
            sent.add(URLDecoder.decode(body, StandardCharsets.UTF_8));
            return "{\"ok\":true,\"result\":{}}";
        }
        if (path.endsWith("/getUpdates")) {
            String text = incoming.poll(200, TimeUnit.MILLISECONDS);
            if (text == null) return "{\"ok\":true,\"result\":[]}";
            synchronized (Stubs.class) {
                updateId++;
                return "{\"ok\":true,\"result\":[{\"update_id\":" + updateId
                        + ",\"message\":{\"chat\":{\"id\":555},\"text\":\"" + text + "\"}}]}";
            }
        }
        return "{\"ok\":false,\"description\":\"unknown\"}";
    }
}
