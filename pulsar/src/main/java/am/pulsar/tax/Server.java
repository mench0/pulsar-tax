package am.pulsar.tax;

import am.pulsar.tax.Model.Income;
import am.pulsar.tax.Model.Invoice;
import am.pulsar.tax.Model.Profile;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HTTP-сервер: REST API под /api и статический фронтенд из ресурсов /web. */
public final class Server {

    private static final int MAX_BODY = 1_000_000;
    private static final Pattern ID_PATH = Pattern.compile("^/api/(incomes|invoices)/(\\d+)(/paid)?$");
    private static final Map<String, String> MIME = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "svg", "image/svg+xml",
            "ico", "image/x-icon");

    private final Store store;
    private final HttpServer http;

    public Server(Store store, String host, int port) throws IOException {
        this.store = store;
        this.http = HttpServer.create(new InetSocketAddress(host, port), 0);
        http.createContext("/api/", this::handleApi);
        http.createContext("/", this::handleStatic);
        http.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    public void start() {
        http.start();
    }

    public void stop() {
        http.stop(0);
    }

    public int port() {
        return http.getAddress().getPort();
    }

    // ---------- API ----------

    private void handleApi(HttpExchange ex) throws IOException {
        try {
            Object result = route(ex.getRequestMethod(), ex.getRequestURI().getPath(), query(ex), ex);
            if (result == null) {
                send(ex, 204, null, null);
            } else {
                int status = "POST".equals(ex.getRequestMethod()) && !ex.getRequestURI().getPath().endsWith("/paid")
                        ? 201 : 200;
                send(ex, status, "application/json; charset=utf-8", Json.write(result));
            }
        } catch (ApiException e) {
            sendError(ex, e.status, e.getMessage());
        } catch (IllegalArgumentException e) {
            sendError(ex, 400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "Внутренняя ошибка сервера");
        }
    }

    private Object route(String method, String path, Map<String, String> q, HttpExchange ex) throws IOException {
        switch (method + " " + path) {
            case "GET /api/profile":
                return store.profile().toJson();
            case "PUT /api/profile":
                return store.updateProfile(Codec.profile(body(ex))).toJson();
            case "GET /api/summary": {
                LocalDate today = LocalDate.now();
                int year = year(q, today);
                return Summary.build(store.profile(), store.incomes(year), year, today);
            }
            case "GET /api/incomes":
                return store.incomes(year(q, LocalDate.now())).stream().map(Income::toJson).toList();
            case "POST /api/incomes":
                return store.addIncome(Codec.income(body(ex))).toJson();
            case "GET /api/invoices":
                return store.invoices().stream().map(Invoice::toJson).toList();
            case "POST /api/invoices":
                return store.addInvoice(Codec.invoice(body(ex))).toJson();
            default:
                break;
        }

        Matcher m = ID_PATH.matcher(path);
        if (m.matches()) {
            long id = Long.parseLong(m.group(2));
            boolean incomesPath = m.group(1).equals("incomes");
            boolean paid = m.group(3) != null;
            if (incomesPath && !paid && method.equals("DELETE")) {
                store.deleteIncome(id);
                return null;
            }
            if (!incomesPath && !paid && method.equals("GET")) return store.invoice(id).toJson();
            if (!incomesPath && !paid && method.equals("DELETE")) {
                store.deleteInvoice(id);
                return null;
            }
            if (!incomesPath && paid && method.equals("POST")) {
                LocalDate date = q.containsKey("date") ? new Fields(Map.of("date", q.get("date"))).date("date", true)
                        : LocalDate.now();
                return store.markPaid(id, date).toJson();
            }
        }
        throw new ApiException(404, "Метод не найден: " + method + " " + path);
    }

    private static int year(Map<String, String> q, LocalDate today) {
        String y = q.get("year");
        if (y == null) return today.getYear();
        try {
            int year = Integer.parseInt(y);
            if (year < 2000 || year > 2100) throw new NumberFormatException();
            return year;
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Неверный год: " + y);
        }
    }

    private static Map<String, Object> body(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] bytes = in.readNBytes(MAX_BODY + 1);
            if (bytes.length > MAX_BODY) throw new ApiException(413, "Слишком большой запрос");
            return Json.parseObject(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> q = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return q;
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            q.put(URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return q;
    }

    // ---------- Статика ----------

    private void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (!ex.getRequestMethod().equals("GET") && !ex.getRequestMethod().equals("HEAD")) {
            send(ex, 405, "text/plain; charset=utf-8", "Method Not Allowed");
            return;
        }
        if (path.equals("/")) path = "/index.html";
        // Разрешаем только простые имена файлов, без выхода за пределы папки web.
        if (!path.matches("^/[a-zA-Z0-9_-]+\\.[a-z]+$")) {
            send(ex, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }
        String ext = path.substring(path.lastIndexOf('.') + 1);
        try (InputStream in = Server.class.getResourceAsStream("/web" + path)) {
            if (in == null || !MIME.containsKey(ext)) {
                send(ex, 404, "text/plain; charset=utf-8", "Not found");
                return;
            }
            byte[] bytes = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", MIME.get(ext));
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, ex.getRequestMethod().equals("HEAD") ? -1 : bytes.length);
            if (!ex.getRequestMethod().equals("HEAD")) {
                try (OutputStream out = ex.getResponseBody()) {
                    out.write(bytes);
                }
            }
            ex.close();
        }
    }

    // ---------- Ответы ----------

    private static void sendError(HttpExchange ex, int status, String message) throws IOException {
        send(ex, status, "application/json; charset=utf-8", Json.write(Map.of("error", message)));
    }

    private static void send(HttpExchange ex, int status, String type, String body) throws IOException {
        if (body == null) {
            ex.sendResponseHeaders(status, -1);
            ex.close();
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
