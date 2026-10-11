package am.pulsar.tax.app.controller;import am.pulsar.tax.app.service.BusinessService;
import am.pulsar.tax.app.service.UserService;
import am.pulsar.tax.app.service.calculation.Deadlines;
import am.pulsar.tax.app.utils.ClockUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Подписка на календарь сроков по секретной ссылке: календари не умеют передавать cookie. */
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CalendarController {

    static final MediaType CALENDAR = MediaType.parseMediaType("text/calendar;charset=UTF-8");

    UserService userService;
    BusinessService businessService;

    @GetMapping("/cal/{token:[0-9a-f]{40}}.ics")
    public ResponseEntity<String> calendar(@PathVariable("token") String token) {
        return userService.getByCalendarToken(token)
                .map(user -> ResponseEntity.ok()
                        .contentType(CALENDAR)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"pulsar-tax.ics\"")
                        .body(Deadlines.ics(businessService.getOrCreateForUser(user.getId()), user.getLang(), ClockUtils.today())))
                .orElseGet(() -> ResponseEntity.status(404).contentType(MediaType.TEXT_PLAIN).body("Not found"));
    }
}
