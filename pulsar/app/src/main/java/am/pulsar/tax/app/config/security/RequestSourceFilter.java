package am.pulsar.tax.app.config.security;import am.pulsar.tax.app.config.PulsarProperties;
import am.pulsar.tax.app.exception.ErrorResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * Защита API от чужих сайтов, открытых в том же браузере, и заголовки CORS для своего фронтенда.
 *
 * Host: сайт, подменивший DNS на 127.0.0.1 (DNS rebinding), присылает своё имя — пока сервер слушает
 * только этот компьютер, принимаются лишь локальные имена. Origin: изменяющий запрос со страницы
 * чужого сайта (CSRF) отклоняется. Разрешены свой адрес, PULSAR_ALLOWED_ORIGINS и, при работе только
 * на этом компьютере, любой локальный адрес (фронтенд при разработке на другом порту).
 */
public class RequestSourceFilter extends OncePerRequestFilter {

    private static final Set<String> LOCAL_HOSTS = Set.of("127.0.0.1", "localhost", "[::1]");

    private final Set<String> allowedOrigins;
    private final boolean loopbackOnly;

    public RequestSourceFilter(PulsarProperties props, String serverAddress) {
        this.allowedOrigins = props.origins();
        this.loopbackOnly = isLoopback(serverAddress);
    }

    static boolean isLoopback(String address) {
        if (address == null || address.isBlank()) return false;
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String host = req.getHeader("Host");
        if (loopbackOnly && (host == null || !LOCAL_HOSTS.contains(hostName(host)))) {
            ErrorResponses.write(res, 403, "Запрос с чужого адреса отклонён");
            return;
        }
        String method = req.getMethod();
        String origin = req.getHeader("Origin");
        boolean allowed = origin != null && originAllowed(origin, host);
        boolean safe = method.equals("GET") || method.equals("HEAD");
        if (!safe && origin != null && !allowed) {
            ErrorResponses.write(res, 403, "Запрос с другого сайта отклонён");
            return;
        }
        res.addHeader("Vary", "Origin");
        if (allowed) {
            res.setHeader("Access-Control-Allow-Origin", origin);
            res.setHeader("Access-Control-Allow-Credentials", "true");
            res.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        }
        if (method.equals("OPTIONS")) {
            res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE");
            res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
            res.setHeader("Access-Control-Max-Age", "600");
            res.setStatus(204);
            return;
        }
        chain.doFilter(req, res);
    }

    boolean originAllowed(String origin, String host) {
        if (host != null && (origin.equals("http://" + host) || origin.equals("https://" + host))) return true;
        if (allowedOrigins.contains(origin)) return true;
        if (!loopbackOnly) return false;
        try {
            URI u = URI.create(origin);
            return "http".equals(u.getScheme()) && u.getHost() != null && LOCAL_HOSTS.contains(u.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Имя без порта: "localhost:8080" → "localhost", "[::1]:8080" → "[::1]". */
    static String hostName(String host) {
        int colon = host.lastIndexOf(':');
        return colon > host.lastIndexOf(']') ? host.substring(0, colon) : host;
    }
}
