package am.pulsar.tax;

/** Ошибка, которую можно показать пользователю, с HTTP-статусом. */
public class ApiException extends RuntimeException {
    final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }
}
