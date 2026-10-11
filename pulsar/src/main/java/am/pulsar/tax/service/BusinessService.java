package am.pulsar.tax.service;

import am.pulsar.tax.entity.Business;
import am.pulsar.tax.entity.Enums.ActivityType;
import am.pulsar.tax.entity.Enums.TaxRegime;
import am.pulsar.tax.exception.ApiException;
import am.pulsar.tax.repository.BusinessRepository;
import am.pulsar.tax.util.Fields;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class BusinessService {

    private final BusinessRepository repo;

    public BusinessService(BusinessRepository repo) {
        this.repo = repo;
    }

    /** ИП пользователя; создаётся с настройками по умолчанию при первом обращении. */
    @Transactional
    public Business forUser(long userId) {
        return repo.findByOwnerId(userId).orElseGet(() -> {
            repo.insertIfAbsent(userId);
            return repo.findByOwnerId(userId).orElseThrow();
        });
    }

    @Transactional
    public Business update(long userId, Map<String, Object> json) {
        Fields f = new Fields(json);
        String name = f.str("name", 200, false);
        String tin = f.str("tin", 20, false);
        String address = f.str("address", 300, false);
        String bankAccount = f.str("bankAccount", 50, false);
        TaxRegime regime = f.enumValue("regime", TaxRegime.class);
        ActivityType activity = f.enumValue("activity", ActivityType.class);
        BigDecimal customRate = f.dec("customRate", false);
        if (customRate != null && (customRate.signum() < 0 || customRate.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw ApiException.badRequest("Ставка должна быть от 0 до 100%");
        }
        if (regime == TaxRegime.TURNOVER && activity == ActivityType.CUSTOM && customRate == null) {
            throw ApiException.badRequest("Для своего вида деятельности укажите ставку налога");
        }
        Business b = forUser(userId);
        b.update(name, tin, address, bankAccount, regime, activity, customRate);
        return b;
    }
}
