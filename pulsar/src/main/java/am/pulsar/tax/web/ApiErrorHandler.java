package am.pulsar.tax.web;

import am.pulsar.tax.shared.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/** Все ошибки — в виде {"error": "текст"}, как в прежней версии API. */
@RestControllerAdvice
public class ApiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> api(ApiException e) {
        return error(e.status(), e.getMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String, String>> mediaType() {
        return error(415, "Ожидается Content-Type: application/json");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable() {
        return error(400, "Неверный JSON в теле запроса");
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> badParam(Exception e) {
        return error(400, "Неверный параметр запроса");
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    ResponseEntity<Map<String, String>> notFound(HttpServletRequest req) {
        return error(404, "Метод не найден: " + req.getMethod() + " " + req.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> internal(Exception e) {
        log.error("Внутренняя ошибка", e);
        return error(500, "Внутренняя ошибка сервера");
    }

    static ResponseEntity<Map<String, String>> error(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
