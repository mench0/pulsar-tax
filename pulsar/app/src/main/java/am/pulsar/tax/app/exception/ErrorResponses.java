package am.pulsar.tax.app.exception;import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Ошибка {"error": "текст"} для фильтров, которые отвечают до контроллеров. */
public final class ErrorResponses {

    private ErrorResponses() {
    }

    public static void write(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"error\":\"" + escape(message) + "\"}");
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
