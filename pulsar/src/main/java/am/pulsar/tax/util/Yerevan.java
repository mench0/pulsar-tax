package am.pulsar.tax.util;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** «Сегодня» и «сейчас» по Еревану, где бы ни стоял сервер. В тестах часы подменяются. */
@Component
public class Yerevan {

    public static final ZoneId ZONE = ZoneId.of("Asia/Yerevan");

    private final Clock clock;

    public Yerevan() {
        this(Clock.system(ZONE));
    }

    public Yerevan(Clock clock) {
        this.clock = clock.withZone(ZONE);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(clock);
    }

    public Clock clock() {
        return clock;
    }
}
