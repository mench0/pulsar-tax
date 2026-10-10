package am.pulsar.tax;

import am.pulsar.tax.Model.Income;
import am.pulsar.tax.Model.Invoice;
import am.pulsar.tax.Model.TaxPayment;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP-сервер: REST API под /api и календарь под /cal. Фронтенд живёт в отдельном репозитории
 * (pulsar-tax-web) и обращается к API с других адресов, поэтому здесь CORS со списком разрешённых адресов.
 */
public final class Server {

    /**
     * Настройки сервера.
     *
     * @param secureCookie   cookie только по HTTPS
     * @param signupOpen     открыта ли регистрация новых пользователей
     * @param sameSite       атрибут SameSite у cookie: Strict, если фронтенд на том же сайте
     *                       (app.example.am и api.example.am), None — если на другом домене
     * @param allowedOrigins адреса фронтенда, которым разрешены запросы с cookie (CORS)
     */
    public record Options(boolean secureCookie, boolean signupOpen, String sameSite, Set<String> allowedOrigins) {
        public static final Options DEFAULT = new Options(false, true, "Strict", Set.of());

        public Options {
            if (!Set.of("Strict", "Lax", "None").contains(sameSite)) {
                throw new IllegalArgumentException("SameSite должен быть Strict, Lax или None: " + sameSite);
            }
            if (sameSite.equals("None") && !secureCookie) {
                throw new IllegalArgumentException("SameSite=None работает только с Secure: включите PULSAR_SECURE_COOKIE=1");
            }
            allowedOrigins = Set.copyOf(allowedOrigins);
        }
    }

    /** Ответ с явным HTTP-статусом. */
    private record Reply(int status, Object body) {
    }

    /** Ответ-файл: CSV, JSON для скачивания или календарь. */
    private record Raw(String type, String body, String filename) {
    }

    private static final int MAX_BODY = 2_000_000;
    private static final int MAX_IMPORT = 5000;
    private static final String COOKIE = "pulsar_session";
    private static final Pattern ID_PATH =
            Pattern.compile("^/api/(incomes|invoices|payments)/(\\d+)(/paid|/einvoice)?$");
    private static final Pattern CAL_PATH = Pattern.compile("^/cal/([0-9a-f]{40})\\.ics$");
    /** Имена, по которым к серверу на 127.0.0.1 обращается сам браузер пользователя. */
    private static final Set<String> LOCAL_HOSTS = Set.of("127.0.0.1", "localhost", "[::1]");

    private final Accounts accounts;
    private final Telegram telegram;
    private final Options options;
    private final HttpServer http;
    /** Сервер слушает только этот компьютер — тогда проверяем заголовок Host против DNS rebinding. */
    private final boolean loopbackOnly;

    public Server(Accounts accounts, Telegram telegram, Options options, String host, int port) throws IOException {
        this.accounts = accounts;
        this.telegram = telegram;
        this.options = options;
        InetSocketAddress address = new InetSocketAddress(host, port);
        this.loopbackOnly = address.getAddress() != null && address.getAddress().isLoopbackAddress();
        this.http = HttpServer.create(address, 0);
        http.createContext("/api/", this::handleApi);
        http.createContext("/cal/", this::handleCalendar);
        http.createContext("/", this::handleRoot);
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
            checkRequestSource(ex);
            if (cors(ex)) return;
            Object result = route(ex.getRequestMethod(), ex.getRequestURI().getPath(), query(ex), ex);
            ex.getResponseHeaders().set("Cache-Control", "no-store");
            if (result == null) {
                send(ex, 204, null, null);
            } else if (result instanceof Raw raw) {
                if (raw.filename != null) {
                    ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + raw.filename + "\"");
                }
                send(ex, 200, raw.type, raw.body);
            } else if (result instanceof Reply r) {
                send(ex, r.status, "application/json; charset=utf-8", Json.write(r.body));
            } else {
                send(ex, 200, "application/json; charset=utf-8", Json.write(result));
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
        // Без входа доступны только вход, регистрация и справочник правил.
        switch (method + " " + path) {
            case "POST /api/auth/register": {
                Map<String, Object> b = body(ex);
                if (!options.signupOpen && !accounts.users().isEmpty()) {
                    throw new ApiException(403, "Регистрация закрыта");
                }
                Accounts.User u = accounts.register(str(b.get("email")), str(b.get("password")));
                if (b.get("lang") instanceof String l && Accounts.LANGUAGES.contains(l)) accounts.setLang(u, l);
                setSession(ex, accounts.newSession(u));
                return new Reply(201, me(u));
            }
            case "POST /api/auth/login": {
                Map<String, Object> b = body(ex);
                String token = accounts.login(str(b.get("email")), str(b.get("password")));
                setSession(ex, token);
                return me(accounts.bySession(token));
            }
            case "POST /api/auth/logout":
                accounts.logout(sessionToken(ex));
                ex.getResponseHeaders().add("Set-Cookie", cookie("", 0));
                return null;
            case "GET /api/rules":
                return TaxRules.toJson(year(q, today()));
            default:
                break;
        }

        Accounts.User user = accounts.bySession(sessionToken(ex));
        if (user == null) throw new ApiException(401, "Войдите в Pulsar Tax");
        Store store = accounts.store(user);

        switch (method + " " + path) {
            case "GET /api/auth/me":
                return me(user);
            case "PUT /api/account": {
                Map<String, Object> b = body(ex);
                accounts.setLang(user, str(b.get("lang")));
                return me(user);
            }
            case "POST /api/account/password": {
                Map<String, Object> b = body(ex);
                accounts.changePassword(user, str(b.get("oldPassword")), str(b.get("newPassword")));
                setSession(ex, accounts.newSession(user));
                return me(user);
            }
            case "POST /api/account/telegram": {
                if (telegram == null) throw new ApiException(400, "Telegram на этом сервере не настроен");
                if (telegram.username() == null) throw new ApiException(503, "Бот Telegram ещё запускается, попробуйте через минуту");
                String code = accounts.telegramCode(user);
                return Map.of("code", code, "link", "https://t.me/" + telegram.username() + "?start=" + code);
            }
            case "DELETE /api/account/telegram":
                accounts.unlinkTelegram(user);
                return me(user);
            case "GET /api/profile":
                return store.profile().toJson();
            case "PUT /api/profile":
                return store.updateProfile(Codec.profile(body(ex))).toJson();
            case "GET /api/summary": {
                LocalDate today = today();
                int year = year(q, today);
                return Summary.build(store.profile(), store.incomes(year), store.payments(year), year, today);
            }
            case "GET /api/incomes":
                return store.incomes(year(q, today())).stream().map(Income::toJson).toList();
            case "POST /api/incomes":
                return new Reply(201, store.addIncome(Codec.income(body(ex))).toJson());
            case "POST /api/incomes/import":
                return importIncomes(store, body(ex));
            case "GET /api/invoices":
                return store.invoices().stream().map(Invoice::toJson).toList();
            case "POST /api/invoices":
                return new Reply(201, store.addInvoice(Codec.invoice(body(ex))).toJson());
            case "GET /api/payments":
                return store.payments(year(q, today())).stream().map(TaxPayment::toJson).toList();
            case "POST /api/payments":
                return new Reply(201, store.addPayment(Codec.taxPayment(body(ex))).toJson());
            case "GET /api/export/data.json":
                return new Raw("application/json; charset=utf-8", Json.write(store.toJson()), "pulsar-tax.json");
            case "GET /api/export/incomes.csv":
                return new Raw("text/csv; charset=utf-8", Csv.incomes(store.allIncomes()), "incomes.csv");
            case "GET /api/export/invoices.csv":
                return new Raw("text/csv; charset=utf-8", Csv.invoices(store.invoices()), "invoices.csv");
            default:
                break;
        }

        Matcher m = ID_PATH.matcher(path);
        if (m.matches()) {
            long id = Long.parseLong(m.group(2));
            String kind = m.group(1);
            String action = m.group(3) == null ? "" : m.group(3);
            switch (kind + action + " " + method) {
                case "incomes DELETE":
                    store.deleteIncome(id);
                    return null;
                case "payments DELETE":
                    store.deletePayment(id);
                    return null;
                case "invoices GET":
                    return store.invoice(id).toJson();
                case "invoices DELETE":
                    store.deleteInvoice(id);
                    return null;
                case "invoices/paid POST": {
                    LocalDate date = q.containsKey("date")
                            ? new Fields(Map.of("date", q.get("date"))).date("date", true) : today();
                    return store.markPaid(id, date).toJson();
                }
                case "invoices/einvoice PUT": {
                    String number = new Fields(body(ex)).str("eInvoiceNumber", 50, false);
                    return store.setEInvoiceNumber(id, number).toJson();
                }
                default:
                    break;
            }
        }
        throw new ApiException(404, "Метод не найден: " + method + " " + path);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> importIncomes(Store store, Map<String, Object> b) {
        List<Object> raw = new Fields(b).list("items");
        if (raw.size() > MAX_IMPORT) throw new ApiException(400, "За раз можно загрузить не больше " + MAX_IMPORT + " строк");
        List<Income> list = new ArrayList<>();
        for (int n = 0; n < raw.size(); n++) {
            if (!(raw.get(n) instanceof Map<?, ?> item)) throw new ApiException(400, "Строка " + (n + 1) + ": неверный формат");
            try {
                list.add(Codec.income((Map<String, Object>) item));
            } catch (ApiException e) {
                throw new ApiException(400, "Строка " + (n + 1) + ": " + e.getMessage());
            }
        }
        int[] r = store.importIncomes(list);
        return Map.of("added", r[0], "skipped", r[1]);
    }

    private Map<String, Object> me(Accounts.User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", u.email());
        m.put("lang", u.lang());
        m.put("telegramEnabled", telegram != null);
        m.put("telegramLinked", u.telegramChatId() != null);
        m.put("calendarPath", "/cal/" + u.calendarToken() + ".ics");
        return m;
    }

    private static LocalDate today() {
        return LocalDate.now(Main.ZONE);
    }

    private static String str(Object v) {
        return v instanceof String s ? s : "";
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

    // ---------- Сессия ----------

    private void setSession(HttpExchange ex, String token) {
        ex.getResponseHeaders().add("Set-Cookie", cookie(token, Accounts.SESSION_DAYS * 86400));
    }

    private String cookie(String value, long maxAge) {
        return COOKIE + "=" + value + "; Path=/; Max-Age=" + maxAge + "; HttpOnly; SameSite=" + options.sameSite
                + (options.secureCookie ? "; Secure" : "");
    }

    private static String sessionToken(HttpExchange ex) {
        for (String header : ex.getRequestHeaders().getOrDefault("Cookie", List.of())) {
            for (String part : header.split(";")) {
                String p = part.trim();
                if (p.startsWith(COOKIE + "=")) return p.substring(COOKIE.length() + 1);
            }
        }
        return null;
    }

    /**
     * Защита от чужих сайтов, открытых в том же браузере.
     * Host: сайт, подменивший DNS на 127.0.0.1 (DNS rebinding), присылает своё имя.
     * Origin: запрос со страницы другого сайта (CSRF) несёт её адрес.
     */
    private void checkRequestSource(HttpExchange ex) {
        String host = ex.getRequestHeaders().getFirst("Host");
        if (loopbackOnly && (host == null || !LOCAL_HOSTS.contains(hostName(host)))) {
            throw new ApiException(403, "Запрос с чужого адреса отклонён");
        }
        String origin = ex.getRequestHeaders().getFirst("Origin");
        boolean safe = ex.getRequestMethod().equals("GET") || ex.getRequestMethod().equals("HEAD");
        if (!safe && origin != null && !originAllowed(origin, host)) {
            throw new ApiException(403, "Запрос с другого сайта отклонён");
        }
    }

    /**
     * Разрешён ли адрес страницы: свой адрес, адрес из PULSAR_ALLOWED_ORIGINS, а при работе
     * только на этом компьютере — любой локальный адрес (фронтенд при разработке на другом порту).
     */
    boolean originAllowed(String origin, String host) {
        if (origin.equals("http://" + host) || origin.equals("https://" + host)) return true;
        if (options.allowedOrigins.contains(origin)) return true;
        if (!loopbackOnly) return false;
        try {
            URI u = URI.create(origin);
            return "http".equals(u.getScheme()) && u.getHost() != null
                    && LOCAL_HOSTS.contains(u.getHost()); // у IPv6 getHost() уже в скобках
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Заголовки CORS для разрешённого фронтенда. Отвечает на предварительный запрос OPTIONS
     * и возвращает true, если ответ уже отправлен.
     */
    private boolean cors(HttpExchange ex) throws IOException {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        String host = ex.getRequestHeaders().getFirst("Host");
        boolean allowed = origin != null && originAllowed(origin, host);
        ex.getResponseHeaders().add("Vary", "Origin");
        if (allowed) {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            ex.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
            ex.getResponseHeaders().set("Access-Control-Expose-Headers", "Content-Disposition");
        }
        if (!ex.getRequestMethod().equals("OPTIONS")) return false;
        if (!allowed) throw new ApiException(403, "Запрос с другого сайта отклонён");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().set("Access-Control-Max-Age", "600");
        send(ex, 204, null, null);
        return true;
    }

    /** Имя без порта: "localhost:8080" → "localhost", "[::1]:8080" → "[::1]". */
    private static String hostName(String host) {
        int colon = host.lastIndexOf(':');
        return colon > host.lastIndexOf(']') ? host.substring(0, colon) : host;
    }

    private static Map<String, Object> body(HttpExchange ex) throws IOException {
        // Только JSON: браузер не отправит такой запрос с чужого сайта без разрешения сервера (CORS preflight).
        String type = ex.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            throw new ApiException(415, "Ожидается Content-Type: application/json");
        }
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

    // ---------- Календарь ----------

    /** Подписка на календарь по секретной ссылке: календари не умеют передавать cookie. */
    private void handleCalendar(HttpExchange ex) throws IOException {
        Matcher m = CAL_PATH.matcher(ex.getRequestURI().getPath());
        Accounts.User u = m.matches() && ex.getRequestMethod().equals("GET") ? accounts.byCalendarToken(m.group(1)) : null;
        if (u == null) {
            send(ex, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }
        ex.getResponseHeaders().set("Content-Disposition", "inline; filename=\"pulsar-tax.ics\"");
        send(ex, 200, "text/calendar; charset=utf-8", Deadlines.ics(accounts.store(u).profile(), u.lang(), today()));
    }

    // ---------- Корень ----------

    /** Фронтенда здесь нет: корень отвечает, что это API, остальное — 404. */
    private void handleRoot(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        if (ex.getRequestURI().getPath().equals("/") && ex.getRequestMethod().equals("GET")) {
            send(ex, 200, "application/json; charset=utf-8", Json.write(Map.of("service", "pulsar-tax-api", "status", "ok")));
        } else {
            sendError(ex, 404, "Not found");
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
