package am.pulsar.tax.security;

/** Вошедший пользователь — принципал Spring Security. Токен нужен, чтобы знать, как он вошёл. */
public record AuthUser(long id, boolean bearer) {
}
