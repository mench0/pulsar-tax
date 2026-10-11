package am.pulsar.tax.app.exception;import am.pulsar.tax.dto.error.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Comparator;

/** Все ошибки — в виде {"error": "текст"}. */
@Slf4j
@RestControllerAdvice
public class ControllerExceptionHandler {

    @ExceptionHandler
    public ResponseEntity<ErrorResponseDto> handle(ApiException e) {
        return error(e.getStatus(), e.getMessage());
    }

    /** Ошибки Bean Validation в сервисах (@Validated): показываем первую по имени поля, чтобы ответ был стабильным. */
    @ExceptionHandler
    public ResponseEntity<ErrorResponseDto> handle(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .min(Comparator.comparing((ConstraintViolation<?> v) -> v.getPropertyPath().toString()))
                .map(ConstraintViolation::getMessage)
                .orElse("Неверные данные");
        return error(400, message);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponseDto> handle(MethodArgumentNotValidException e) {
        return error(400, e.getBindingResult().getAllErrors().getFirst().getDefaultMessage());
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponseDto> handle(HandlerMethodValidationException e) {
        return error(400, e.getAllErrors().getFirst().getDefaultMessage());
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponseDto> handle(HttpMediaTypeNotSupportedException e) {
        return error(415, "Ожидается Content-Type: application/json");
    }

    /** Неразбираемое тело: свои ошибки полей (числа, даты) отдаём как есть, остальное — общим текстом. */
    @ExceptionHandler
    public ResponseEntity<ErrorResponseDto> handle(HttpMessageNotReadableException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ApiException api) {
                return handle(api);
            }
            if (t instanceof InvalidFormatException invalid && invalid.getTargetType() != null
                    && invalid.getTargetType().isEnum()) {
                return error(400, "Неизвестное значение «" + invalid.getValue() + "» для «" + field(invalid) + "»");
            }
        }
        log.debug("Не удалось разобрать тело запроса", e);
        return error(400, "Неверный JSON в теле запроса");
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponseDto> handle(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return error(413, "Файл больше 10 МБ");
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.multipart.MultipartException.class})
    public ResponseEntity<ErrorResponseDto> handleBadParameter(Exception e) {
        return error(400, "Неверный параметр запроса");
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ErrorResponseDto> handleNotFound(HttpServletRequest request) {
        return error(404, "Метод не найден: " + request.getMethod() + " " + request.getRequestURI());
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponseDto> handle(DataIntegrityViolationException e) {
        log.warn("Нарушено ограничение базы: {}", e.getMostSpecificCause().getMessage());
        return error(409, "Такая запись уже есть");
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponseDto> handle(Exception e) {
        log.error("Внутренняя ошибка", e);
        return error(500, "Внутренняя ошибка сервера");
    }

    private static String field(DatabindException e) {
        return e.getPath().isEmpty() ? "?" : e.getPath().getLast().getPropertyName();
    }

    private static ResponseEntity<ErrorResponseDto> error(int status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponseDto(message));
    }
}
