package am.pulsar.tax.app.exception;

/** Внешний сервис (ЦБ РА, Telegram) недоступен (503). */
public class ExternalServiceUnavailableException extends ApiException {

    public ExternalServiceUnavailableException(String message) {
        super(503, message);
    }
}
