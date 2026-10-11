package am.pulsar.tax.app.service.calculation;import am.pulsar.tax.app.entity.Business;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Сроки вне браузера: тексты на языке пользователя, календарь .ics и напоминания.
 * Тексты интерфейса живут во фронтенде (assets/i18n); здесь только то, что сервер отправляет сам.
 */
public final class Deadlines {

    /** За сколько дней до срока напоминать. */
    static final int[] REMIND_DAYS = {7, 3, 1, 0};

    private static final Map<String, Map<String, String>> TEXT = Map.of(
            "ru", Map.of(
                    "TURNOVER_QUARTER", "Расчёт и уплата налога с оборота за {quarter} квартал {year} года",
                    "REGIME_CHOICE", "Заявление о выборе налогового режима на {year} год",
                    "in", "через {n} дн.",
                    "today", "сегодня",
                    "due", "К уплате: {amount} ֏",
                    "linked", "Telegram подключён к Pulsar Tax. Напомню о налоговых сроках за 7, 3 и 1 день.",
                    "badCode", "Код не найден или устарел. Получите новый в настройках Pulsar Tax.",
                    "hello", "Это бот Pulsar Tax. Чтобы получать напоминания, нажмите «Подключить Telegram» в настройках."),
            "en", Map.of(
                    "TURNOVER_QUARTER", "Turnover tax return and payment for Q{quarter} {year}",
                    "REGIME_CHOICE", "Application to choose the tax regime for {year}",
                    "in", "in {n} days",
                    "today", "today",
                    "due", "To pay: {amount} ֏",
                    "linked", "Telegram is connected to Pulsar Tax. I will remind you of tax deadlines 7, 3 and 1 day ahead.",
                    "badCode", "The code is unknown or expired. Get a new one in Pulsar Tax settings.",
                    "hello", "This is the Pulsar Tax bot. To get reminders, press “Connect Telegram” in settings."),
            "hy", Map.of(
                    "TURNOVER_QUARTER", "Շրջանառության հարկի հաշվարկ և վճարում՝ {year} թ. {quarter}-րդ եռամսյակի համար",
                    "REGIME_CHOICE", "Դիմում {year} թ. հարկման ռեժիմի ընտրության մասին",
                    "in", "{n} օրից",
                    "today", "այսօր",
                    "due", "Վճարման ենթակա՝ {amount} ֏",
                    "linked", "Telegram-ը միացված է Pulsar Tax-ին։ Կհիշեցնեմ հարկային ժամկետների մասին 7, 3 և 1 օր առաջ։",
                    "badCode", "Կոդը չի գտնվել կամ ժամկետն անցել է։ Ստացեք նորը Pulsar Tax-ի կարգավորումներում։",
                    "hello", "Սա Pulsar Tax-ի բոտն է։ Հիշեցումներ ստանալու համար կարգավորումներում սեղմեք «Միացնել Telegram»։"));

    private Deadlines() {
    }

    public static String text(String lang, String key, Map<String, ?> params) {
        String t = TEXT.getOrDefault(lang, TEXT.get("ru")).get(key);
        if (t == null) t = TEXT.get("ru").getOrDefault(key, key);
        for (Map.Entry<String, ?> e : params.entrySet()) {
            t = t.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return t;
    }

    /** Будущие сроки с датой: хвост прошлого года (налог за 4 квартал), текущий и следующий годы. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> upcoming(Business profile, LocalDate today) {
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
        for (int y = today.getYear() - 1; y <= today.getYear() + 1; y++) {
            for (Map<String, Object> d : Summary.deadlines(profile, y, today)) {
                if (d.get("date") == null) continue;
                unique.putIfAbsent(d.get("code") + "|" + d.get("date"), d);
            }
        }
        List<Map<String, Object>> list = new ArrayList<>(unique.values());
        list.sort((a, b) -> ((LocalDate) a.get("date")).compareTo((LocalDate) b.get("date")));
        return list;
    }

    static String title(String lang, Map<String, Object> deadline) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) deadline.get("params");
        return text(lang, (String) deadline.get("code"), params);
    }

    /** Календарь в формате iCalendar с напоминанием за 3 дня до каждого срока. */
    public static String ics(Business profile, String lang, LocalDate today) {
        DateTimeFormatter day = DateTimeFormatter.BASIC_ISO_DATE;
        StringBuilder sb = new StringBuilder();
        line(sb, "BEGIN:VCALENDAR");
        line(sb, "VERSION:2.0");
        line(sb, "PRODID:-//Pulsar//Pulsar Tax//" + lang.toUpperCase());
        line(sb, "CALSCALE:GREGORIAN");
        line(sb, "X-WR-CALNAME:Pulsar Tax");
        for (Map<String, Object> d : upcoming(profile, today)) {
            LocalDate date = (LocalDate) d.get("date");
            line(sb, "BEGIN:VEVENT");
            line(sb, "UID:" + d.get("code") + "-" + date + "@pulsar-tax");
            line(sb, "DTSTAMP:" + today.format(day) + "T000000Z");
            line(sb, "DTSTART;VALUE=DATE:" + date.format(day));
            line(sb, "DTEND;VALUE=DATE:" + date.plusDays(1).format(day));
            line(sb, "SUMMARY:" + escape(title(lang, d)));
            line(sb, "BEGIN:VALARM");
            line(sb, "TRIGGER:-P3D");
            line(sb, "ACTION:DISPLAY");
            line(sb, "DESCRIPTION:" + escape(title(lang, d)));
            line(sb, "END:VALARM");
            line(sb, "END:VEVENT");
        }
        line(sb, "END:VCALENDAR");
        return sb.toString();
    }

    /**
     * Текст напоминания на сегодня или null, если сегодня напоминать не о чем.
     * Для налога с оборота добавляется сумма к уплате за квартал из сводки года.
     */
    public static String reminder(Business profile, String lang, LocalDate today,
                                  IntFunction<Map<String, Object>> summaryForYear) {
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> d : upcoming(profile, today)) {
            LocalDate date = (LocalDate) d.get("date");
            long days = date.toEpochDay() - today.toEpochDay();
            boolean remind = false;
            for (int r : REMIND_DAYS) remind |= days == r;
            if (!remind) continue;
            String when = days == 0 ? text(lang, "today", Map.of()) : text(lang, "in", Map.of("n", days));
            StringBuilder line = new StringBuilder("• " + when + " (" + date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    + "): " + title(lang, d));
            if ("TURNOVER_QUARTER".equals(d.get("code"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) d.get("params");
                int year = (Integer) params.get("year");
                int quarter = (Integer) params.get("quarter");
                Map<String, Object> summary = summaryForYear.apply(year);
                @SuppressWarnings("unchecked")
                Map<String, Object> q = ((List<Map<String, Object>>) summary.get("quarters")).get(quarter - 1);
                if (q.get("due") != null) {
                    line.append("\n  ").append(text(lang, "due", Map.of("amount", ((BigDecimal) q.get("due")).toPlainString())));
                }
            }
            lines.add(line.toString());
        }
        return lines.isEmpty() ? null : "Pulsar Tax\n" + String.join("\n", lines);
    }

    /** Строка iCalendar, перенесённая по 75 байт UTF-8, как требует RFC 5545. */
    private static void line(StringBuilder sb, String s) {
        int bytes = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int len = cp < 0x80 ? 1 : cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
            if (bytes + len > 74) {
                sb.append("\r\n ");
                bytes = 1;
            }
            sb.appendCodePoint(cp);
            bytes += len;
            i += Character.charCount(cp);
        }
        sb.append("\r\n");
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
    }
}
