package am.pulsar.tax.app.integration.telegram.job;import am.pulsar.tax.app.entity.Business;
import am.pulsar.tax.app.entity.User;
import am.pulsar.tax.app.integration.telegram.service.TelegramBot;
import am.pulsar.tax.app.service.BusinessService;
import am.pulsar.tax.app.service.SummaryService;
import am.pulsar.tax.app.service.UserService;
import am.pulsar.tax.app.service.calculation.Deadlines;
import am.pulsar.tax.app.utils.ClockUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/** Раз в час, после 10:00 по Еревану, рассылает напоминания в Telegram — каждому не чаще раза в день. */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReminderJob {

    static final int REMIND_HOUR = 10;

    TelegramBot bot;
    UserService userService;
    BusinessService businessService;
    SummaryService summaryService;

    @Scheduled(fixedRate = 3_600_000, initialDelay = 60_000)
    public void hourly() {
        ZonedDateTime now = ClockUtils.zonedNow();
        if (bot.enabled() && now.getHour() >= REMIND_HOUR) {
            sendReminders(now.toLocalDate());
        }
    }

    public void sendReminders(LocalDate today) {
        for (User user : userService.getWithTelegram()) {
            if (today.equals(user.getLastReminder())) {
                continue;
            }
            try {
                Business business = businessService.getOrCreateForUser(user.getId());
                String text = Deadlines.reminder(business, user.getLang(), today,
                        year -> summaryService.summary(business, year, today));
                if (text != null) {
                    bot.send(user.getTelegramChatId(), text);
                }
                userService.markReminded(user.getId(), today);
            } catch (Exception e) {
                log.warn("Telegram: напоминание пользователю {} не отправлено: {}", user.getId(), e.getMessage());
            }
        }
    }
}
