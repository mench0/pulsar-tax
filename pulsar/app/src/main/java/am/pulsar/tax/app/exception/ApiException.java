package am.pulsar.tax.app.exception;import lombok.Getter;

/** Ошибка, которую можно показать пользователю, с HTTP-статусом. Ответ: {"error": "текст"}. */
@Getter
public abstract class ApiException extends RuntimeException {

    private final int status;

    protected ApiException(int status, String message) {
        super(message);
        this.status = status;
    }
}
