package am.pulsar.tax.reminders;

import am.pulsar.tax.business.BusinessService;
import am.pulsar.tax.identity.AccountService;
import am.pulsar.tax.shared.Yerevan;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Подписка на календарь сроков по секретной ссылке: календари не умеют передавать cookie. */
@RestController
public class CalendarController {

    private static final MediaType CALENDAR = MediaType.parseMediaType("text/calendar;charset=UTF-8");

    private final AccountService accounts;
    private final BusinessService businesses;
    private final Yerevan yerevan;

    public CalendarController(AccountService accounts, BusinessService businesses, Yerevan yerevan) {
        this.accounts = accounts;
        this.businesses = businesses;
        this.yerevan = yerevan;
    }

    @GetMapping("/cal/{token:[0-9a-f]{40}}.ics")
    ResponseEntity<String> calendar(@PathVariable String token) {
        return accounts.byCalendarToken(token)
                .map(u -> ResponseEntity.ok()
                        .contentType(CALENDAR)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"pulsar-tax.ics\"")
                        .body(Deadlines.ics(businesses.forUser(u.getId()), u.getLang(), yerevan.today())))
                .orElseGet(() -> ResponseEntity.status(404).contentType(MediaType.TEXT_PLAIN).body("Not found"));
    }
}
