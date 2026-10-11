package am.pulsar.tax;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Клиент API с собственной cookie сессии — как отдельный браузер. */
final class ApiClient {

    static final JsonMapper JSON = JsonMapper.builder().build();
    /** Подменять Host разрешает -Djdk.httpclient.allowRestrictedHeaders=host (pom.xml). */
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final int port;
    String cookie;
    String bearer;

    ApiClient(int port) {
        this.port = port;
    }

    HttpResponse<String> call(String method, String path, String body, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null && headers.length == 0) b.header("Content-Type", "application/json");
        if (headers.length > 0) b.headers(headers);
        if (cookie != null) b.header("Cookie", cookie);
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        for (String c : r.headers().allValues("Set-Cookie")) {
            String pair = c.substring(0, c.indexOf(';'));
            cookie = pair.endsWith("=") ? null : pair;
        }
        return r;
    }

    JsonNode json(String method, String path, String body) throws Exception {
        HttpResponse<String> r = call(method, path, body);
        if (r.statusCode() >= 300) throw new AssertionError(method + " " + path + " → " + r.statusCode() + " " + r.body());
        return JSON.readTree(r.body());
    }

    int status(String method, String path, String body, String... headers) throws Exception {
        return call(method, path, body, headers).statusCode();
    }

    String error(String method, String path, String body, String... headers) throws Exception {
        return JSON.readTree(call(method, path, body, headers).body()).path("error").asString();
    }

    ApiClient register(String email) throws Exception {
        assertEquals(201, status("POST", "/api/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"пароль-для-теста\"}"));
        return this;
    }
}
