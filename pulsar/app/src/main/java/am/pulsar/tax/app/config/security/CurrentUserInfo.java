package am.pulsar.tax.app.config.security;import lombok.Value;

/** Вошедший пользователь — принципал Spring Security. */
@Value
public class CurrentUserInfo {

    Long userId;

    /** ИП пользователя: им ограничивается доступ к доходам, расходам, счетам и уплатам. */
    Long businessId;

    /** Вошёл по токену (мобильное приложение), а не по cookie. */
    boolean bearer;
}
