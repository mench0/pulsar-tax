package am.pulsar.tax.app.exception;

/** Слишком много попыток входа (429). */
public class TooManyAttemptsException extends ApiException {

    public TooManyAttemptsException() {
        super(429, "Слишком много попыток. Попробуйте через 15 минут");
    }
}
