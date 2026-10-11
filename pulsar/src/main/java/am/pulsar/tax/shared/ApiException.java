package am.pulsar.tax.shared;

/** Ошибка, которую можно показать пользователю, с HTTP-статусом. Ответ: {"error": "текст"}. */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, message);
    }
}
