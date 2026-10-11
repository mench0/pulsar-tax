package am.pulsar.tax.reminders;

import am.pulsar.tax.business.Business;
import am.pulsar.tax.business.BusinessService;
import am.pulsar.tax.identity.AccountService;
import am.pulsar.tax.identity.User;
import am.pulsar.tax.identity.UserRepository;
import am.pulsar.tax.shared.Yerevan;
import am.pulsar.tax.tax.TaxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/** Раз в час, после 10:00 по Еревану, рассылает напоминания в Telegram — каждому не чаще раза в день. */
@Component
public class ReminderJob {

    static final int REMIND_HOUR = 10;
    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    private final TelegramBot bot;
    private final UserRepository users;
    private final AccountService accounts;
    private final BusinessService businesses;
    private final TaxService tax;
    private final Yerevan yerevan;

    public ReminderJob(TelegramBot bot, UserRepository users, AccountService accounts, BusinessService businesses,
                       TaxService tax, Yerevan yerevan) {
        this.bot = bot;
        this.users = users;
        this.accounts = accounts;
        this.businesses = businesses;
        this.tax = tax;
        this.yerevan = yerevan;
    }

    @Scheduled(fixedRate = 3_600_000, initialDelay = 60_000)
    public void hourly() {
        ZonedDateTime now = yerevan.now();
        if (bot.enabled() && now.getHour() >= REMIND_HOUR) sendReminders(now.toLocalDate());
    }

    public void sendReminders(LocalDate today) {
        for (User u : users.findByTelegramChatIdIsNotNull()) {
            if (today.equals(u.getLastReminder())) continue;
            try {
                Business b = businesses.forUser(u.getId());
                String text = Deadlines.reminder(b, u.getLang(), today, year -> tax.summary(b, year, today));
                if (text != null) bot.send(u.getTelegramChatId(), text);
                accounts.markReminded(u.getId(), today);
            } catch (Exception e) {
                log.warn("Telegram: напоминание пользователю {} не отправлено: {}", u.getId(), e.getMessage());
            }
        }
    }
}
