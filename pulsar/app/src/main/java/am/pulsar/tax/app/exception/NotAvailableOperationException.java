package am.pulsar.tax.app.exception;

/** Операция запрещена: например, регистрация закрыта или запрос с чужого сайта (403). */
public class NotAvailableOperationException extends ApiException {

    public NotAvailableOperationException(String message) {
        super(403, message);
    }
}
