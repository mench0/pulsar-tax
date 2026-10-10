package am.pulsar.tax;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Учётные записи, сессии и данные каждого ИП.
 *
 * Файлы: {dir}/users.json — пользователи и сессии, {dir}/users/{id}.json — данные ИП.
 * Пароли хранятся как PBKDF2-SHA256 с солью, сессии — как SHA-256 от токена из cookie.
 */
public final class Accounts {

    public static final List<String> LANGUAGES = List.of("ru", "en", "hy");
    static final long SESSION_DAYS = 30;
    private static final int PBKDF2_ITERATIONS = 210_000;
    private static final int MAX_FAILURES = 5;
    private static final long LOCK_SECONDS = 15 * 60;
    private static final long LINK_CODE_SECONDS = 15 * 60;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,190}\\.[^@\\s]{2,}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final class User {
        long id;
        String email;
        String salt;
        String hash;
        String calendarToken;
        Long telegramChatId;
        String lang = "ru";
        LocalDate lastReminder;
        /** SHA-256 токена сессии → время окончания (секунды эпохи). */
        final Map<String, Long> sessions = new HashMap<>();

        public long id() {
            return id;
        }

        public String email() {
            return email;
        }

        public String lang() {
            return lang;
        }

        public Long telegramChatId() {
            return telegramChatId;
        }

        public String calendarToken() {
            return calendarToken;
        }
    }

    private record Failures(int count, long lockedUntil) {
    }

    private record LinkCode(long userId, long expires) {
    }

    private final Path dir;
    private final Path legacyData;
    private final List<User> users = new ArrayList<>();
    private final Map<Long, Store> stores = new HashMap<>();
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final Map<String, LinkCode> linkCodes = new ConcurrentHashMap<>();
    private long nextId = 1;

    /**
     * @param dir        папка данных; null — всё в памяти (для тестов)
     * @param legacyData файл данных версии без учётных записей; переходит к первому пользователю
     */
    public Accounts(Path dir, Path legacyData) {
        this.dir = dir;
        this.legacyData = legacyData;
        if (dir != null && Files.exists(usersFile())) load();
    }

    // ---------- Регистрация и вход ----------

    public synchronized User register(String email, String password) {
        String e = normalize(email);
        if (!EMAIL.matcher(e).matches()) throw new ApiException(400, "Неверный адрес почты");
        checkPassword(password);
        if (find(e) != null) throw new ApiException(409, "Пользователь с такой почтой уже есть");
        User u = new User();
        u.id = nextId++;
        u.email = e;
        u.salt = randomHex(16);
        u.hash = hash(password, u.salt);
        u.calendarToken = randomHex(20);
        if (users.isEmpty()) adoptLegacyData(u);
        users.add(u);
        save();
        return u;
    }

    /** Проверяет пароль и создаёт сессию. Возвращает токен для cookie. */
    public String login(String email, String password) {
        String e = normalize(email);
        long now = Instant.now().getEpochSecond();
        Failures f = failures.get(e);
        if (f != null && f.lockedUntil > now) {
            throw new ApiException(429, "Слишком много попыток. Попробуйте через 15 минут");
        }
        synchronized (this) {
            User u = find(e);
            // Хэш считаем и для несуществующей почты, чтобы по времени ответа нельзя было узнать, есть ли она.
            String h = hash(password == null ? "" : password, u != null ? u.salt : "00");
            if (u == null || !MessageDigest.isEqual(h.getBytes(StandardCharsets.US_ASCII),
                    u.hash.getBytes(StandardCharsets.US_ASCII))) {
                int count = f == null ? 1 : f.count + 1;
                failures.put(e, new Failures(count, count >= MAX_FAILURES ? now + LOCK_SECONDS : 0));
                throw new ApiException(401, "Неверная почта или пароль");
            }
            failures.remove(e);
            return newSession(u, now);
        }
    }

    public synchronized String newSession(User u) {
        return newSession(u, Instant.now().getEpochSecond());
    }

    private String newSession(User u, long now) {
        String token = randomHex(32);
        u.sessions.entrySet().removeIf(s -> s.getValue() < now);
        u.sessions.put(sha256(token), now + SESSION_DAYS * 86400);
        save();
        return token;
    }

    public synchronized void logout(String token) {
        if (token == null) return;
        String h = sha256(token);
        for (User u : users) {
            if (u.sessions.remove(h) != null) save();
        }
    }

    /** Пользователь по токену сессии или null. */
    public synchronized User bySession(String token) {
        if (token == null || token.length() > 100) return null;
        String h = sha256(token);
        long now = Instant.now().getEpochSecond();
        for (User u : users) {
            Long until = u.sessions.get(h);
            if (until != null && until > now) return u;
        }
        return null;
    }

    public synchronized User byCalendarToken(String token) {
        for (User u : users) {
            if (MessageDigest.isEqual(u.calendarToken.getBytes(StandardCharsets.US_ASCII),
                    token.getBytes(StandardCharsets.US_ASCII))) {
                return u;
            }
        }
        return null;
    }

    public synchronized void changePassword(User u, String oldPassword, String newPassword) {
        if (!hash(oldPassword == null ? "" : oldPassword, u.salt).equals(u.hash)) {
            throw new ApiException(400, "Текущий пароль указан неверно");
        }
        checkPassword(newPassword);
        u.salt = randomHex(16);
        u.hash = hash(newPassword, u.salt);
        u.sessions.clear(); // выход со всех устройств
        save();
    }

    // ---------- Настройки учётной записи ----------

    public synchronized void setLang(User u, String lang) {
        if (!LANGUAGES.contains(lang)) throw new ApiException(400, "Неизвестный язык: " + lang);
        u.lang = lang;
        save();
    }

    /** Код для привязки Telegram: пользователь отправляет боту /start КОД. */
    public String telegramCode(User u) {
        String code = randomHex(8);
        long now = Instant.now().getEpochSecond();
        linkCodes.entrySet().removeIf(e -> e.getValue().expires < now);
        linkCodes.put(code, new LinkCode(u.id, now + LINK_CODE_SECONDS));
        return code;
    }

    /** Привязывает чат к пользователю по коду. Возвращает пользователя или null, если код неверный. */
    public synchronized User linkTelegram(String code, long chatId) {
        LinkCode c = code == null ? null : linkCodes.remove(code);
        if (c == null || c.expires < Instant.now().getEpochSecond()) return null;
        User u = byId(c.userId);
        if (u == null) return null;
        u.telegramChatId = chatId;
        save();
        return u;
    }

    public synchronized void unlinkTelegram(User u) {
        u.telegramChatId = null;
        save();
    }

    public synchronized void markReminded(User u, LocalDate day) {
        u.lastReminder = day;
        save();
    }

    public synchronized LocalDate lastReminder(User u) {
        return u.lastReminder;
    }

    public synchronized List<User> users() {
        return List.copyOf(users);
    }

    // ---------- Данные ИП ----------

    public synchronized Store store(User u) {
        return stores.computeIfAbsent(u.id, id -> new Store(dir == null ? null : storeFile(id)));
    }

    private Path storeFile(long id) {
        return dir.resolve("users").resolve(id + ".json");
    }

    /** Данные из версии без учётных записей становятся данными первого пользователя. */
    private void adoptLegacyData(User u) {
        if (dir == null || legacyData == null || !Files.exists(legacyData)) return;
        try {
            Path target = storeFile(u.id);
            Files.createDirectories(target.getParent());
            Files.copy(legacyData, target, StandardCopyOption.REPLACE_EXISTING);
            Files.move(legacyData, legacyData.resolveSibling(legacyData.getFileName() + ".migrated"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(500, "Не удалось перенести старые данные: " + e.getMessage());
        }
    }

    // ---------- Файл ----------

    private Path usersFile() {
        return dir.resolve("users.json");
    }

    private User find(String email) {
        for (User u : users) {
            if (u.email.equals(email)) return u;
        }
        return null;
    }

    private User byId(long id) {
        for (User u : users) {
            if (u.id == id) return u;
        }
        return null;
    }

    private void save() {
        if (dir == null) return;
        List<Map<String, Object>> list = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.id);
            m.put("email", u.email);
            m.put("salt", u.salt);
            m.put("hash", u.hash);
            m.put("calendarToken", u.calendarToken);
            m.put("telegramChatId", u.telegramChatId);
            m.put("lang", u.lang);
            m.put("lastReminder", u.lastReminder);
            m.put("sessions", new LinkedHashMap<>(u.sessions));
            list.add(m);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("nextId", nextId);
        root.put("users", list);
        try {
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, "users", ".tmp");
            Files.writeString(tmp, Json.write(root), StandardCharsets.UTF_8);
            Files.move(tmp, usersFile(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new ApiException(500, "Не удалось сохранить учётные записи: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        try {
            Map<String, Object> root = Json.parseObject(Files.readString(usersFile(), StandardCharsets.UTF_8));
            nextId = ((BigDecimal) root.get("nextId")).longValueExact();
            for (Object o : (List<Object>) root.get("users")) {
                Map<String, Object> m = (Map<String, Object>) o;
                User u = new User();
                u.id = ((BigDecimal) m.get("id")).longValueExact();
                u.email = (String) m.get("email");
                u.salt = (String) m.get("salt");
                u.hash = (String) m.get("hash");
                u.calendarToken = (String) m.get("calendarToken");
                Object chat = m.get("telegramChatId");
                u.telegramChatId = chat == null ? null : ((BigDecimal) chat).longValueExact();
                if (m.get("lang") instanceof String l && LANGUAGES.contains(l)) u.lang = l;
                if (m.get("lastReminder") instanceof String d) u.lastReminder = LocalDate.parse(d);
                if (m.get("sessions") instanceof Map<?, ?> s) {
                    for (Map.Entry<?, ?> e : s.entrySet()) {
                        u.sessions.put((String) e.getKey(), ((BigDecimal) e.getValue()).longValueExact());
                    }
                }
                users.add(u);
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Не удалось прочитать " + usersFile() + ": " + e.getMessage(), e);
        }
    }

    // ---------- Криптография ----------

    private static void checkPassword(String password) {
        if (password == null || password.length() < 8) {
            throw new ApiException(400, "Пароль должен быть не короче 8 символов");
        }
        if (password.length() > 200) throw new ApiException(400, "Слишком длинный пароль");
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    static String hash(String password, String saltHex) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), HexFormat.of().parseHex(saltHex),
                    PBKDF2_ITERATIONS, 256);
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(key);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
