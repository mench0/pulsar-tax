package am.pulsar.tax.app.exception;import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/** Ошибки проверки данных запроса (400). Пользователю показывается первая. */
@Getter
public class CustomValidationException extends ApiException {

    private final List<String> errors = new ArrayList<>();

    public CustomValidationException() {
        super(400, "Неверные данные");
    }

    public CustomValidationException(String message) {
        this();
        errors.add(message);
    }

    public void addValidationError(String message) {
        errors.add(message);
    }

    @Override
    public String getMessage() {
        return errors.isEmpty() ? super.getMessage() : errors.getFirst();
    }
}
