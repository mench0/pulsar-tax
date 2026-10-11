package am.pulsar.tax.app.utils;import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** «Сейчас» и «сегодня» по Еревану, где бы ни стоял сервер. В тестах часы подменяются. */
public final class ClockUtils {

    public static final ZoneId ZONE = ZoneId.of("Asia/Yerevan");

    private static Clock clock = Clock.system(ZONE);

    private ClockUtils() {
    }

    public static OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    public static ZonedDateTime zonedNow() {
        return ZonedDateTime.now(clock);
    }

    public static LocalDate today() {
        return LocalDate.now(clock);
    }

    public static void setClock(Clock newClock) {
        clock = newClock.withZone(ZONE);
    }
}
