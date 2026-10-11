package am.pulsar.tax.app.exception;

/** Такая запись уже есть (409). */
public class DuplicateException extends ApiException {

    public DuplicateException(String message) {
        super(409, message);
    }
}
