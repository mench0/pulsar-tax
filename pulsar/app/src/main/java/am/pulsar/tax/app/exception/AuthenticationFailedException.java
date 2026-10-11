package am.pulsar.tax.app.exception;

/** Не удалось войти: нет сессии или неверный пароль (401). */
public class AuthenticationFailedException extends ApiException {

    public AuthenticationFailedException(String message) {
        super(401, message);
    }
}
