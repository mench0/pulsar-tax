package am.pulsar.tax;

import am.pulsar.tax.Model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Превращает JSON-запросы в объекты модели с проверкой полей. */
final class Codec {

    private Codec() {
    }

    static Profile profile(Map<String, Object> json) {
        Fields f = new Fields(json);
        Profile p = new Profile();
        p.name = f.str("name", 200, false);
        p.tin = f.str("tin", 20, false);
        p.address = f.str("address", 300, false);
        p.bankAccount = f.str("bankAccount", 50, false);
        p.regime = f.enumValue("regime", TaxRegime.class);
        p.activity = f.enumValue("activity", ActivityType.class);
        p.customRate = f.dec("customRate", false);
        if (p.customRate != null && (p.customRate.signum() < 0 || p.customRate.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new ApiException(400, "Ставка должна быть от 0 до 100%");
        }
        if (p.regime == TaxRegime.TURNOVER && p.activity == ActivityType.CUSTOM && p.customRate == null) {
            throw new ApiException(400, "Для своего вида деятельности укажите ставку налога");
        }
        return p;
    }

    /**
     * Доход. В драмах передаётся amount; в другой валюте — originalAmount и rate (драмов за единицу),
     * а сумма в драмах считается здесь и округляется до драма.
     */
    static Income income(Map<String, Object> json) {
        Fields f = new Fields(json);
        Income i = new Income();
        i.date = f.date("date", true);
        i.currency = json.get("currency") == null ? Currency.AMD : f.enumValue("currency", Currency.class);
        if (i.currency == Currency.AMD) {
            i.amount = f.dec("amount", true);
        } else {
            i.originalAmount = f.dec("originalAmount", true);
            i.rate = f.dec("rate", true);
            if (i.originalAmount.signum() <= 0) throw new ApiException(400, "Сумма в валюте должна быть больше нуля");
            if (i.rate.signum() <= 0) throw new ApiException(400, "Курс должен быть больше нуля");
            i.amount = i.originalAmount.multiply(i.rate).setScale(0, RoundingMode.HALF_UP);
        }
        if (i.amount.signum() <= 0) throw new ApiException(400, "Сумма дохода должна быть больше нуля");
        if (i.amount.scale() > 2) throw new ApiException(400, "Сумма дохода: не больше 2 знаков после запятой");
        i.client = f.str("client", 200, false);
        i.description = f.str("description", 500, false);
        String key = f.str("importKey", 100, false);
        i.importKey = key.isEmpty() ? null : key;
        return i;
    }

    @SuppressWarnings("unchecked")
    static Invoice invoice(Map<String, Object> json) {
        Fields f = new Fields(json);
        Invoice inv = new Invoice();
        inv.number = f.str("number", 50, false);
        LocalDate date = f.date("date", false);
        inv.date = date != null ? date : LocalDate.now(Main.ZONE);
        inv.dueDate = f.date("dueDate", false);
        inv.client = f.str("client", 200, true);
        inv.clientTin = f.str("clientTin", 20, false);
        inv.eInvoiceNumber = f.str("eInvoiceNumber", 50, false);
        List<Object> rawItems = f.list("items");
        if (rawItems.isEmpty()) throw new ApiException(400, "Добавьте хотя бы одну позицию в счёт");
        if (rawItems.size() > 200) throw new ApiException(400, "В счёте не больше 200 позиций");
        List<Item> items = new ArrayList<>();
        for (Object o : rawItems) {
            if (!(o instanceof Map)) throw new ApiException(400, "Неверная позиция счёта");
            Fields it = new Fields((Map<String, Object>) o);
            BigDecimal qty = it.dec("quantity", true);
            BigDecimal price = it.dec("price", true);
            if (qty.signum() <= 0) throw new ApiException(400, "Количество должно быть больше нуля");
            if (price.signum() < 0) throw new ApiException(400, "Цена не может быть отрицательной");
            items.add(new Item(it.str("name", 300, true), qty, price));
        }
        inv.items = items;
        return inv;
    }

    static TaxPayment taxPayment(Map<String, Object> json) {
        Fields f = new Fields(json);
        TaxPayment p = new TaxPayment();
        BigDecimal year = f.dec("year", true);
        BigDecimal quarter = f.dec("quarter", true);
        if (year.scale() > 0 || year.intValue() < 2000 || year.intValue() > 2100) {
            throw new ApiException(400, "Неверный год");
        }
        if (quarter.scale() > 0 || quarter.intValue() < 0 || quarter.intValue() > 4) {
            throw new ApiException(400, "Квартал должен быть от 1 до 4 (0 — за год)");
        }
        p.year = year.intValue();
        p.quarter = quarter.intValue();
        p.date = f.date("date", true);
        p.amount = f.dec("amount", true);
        if (p.amount.signum() <= 0) throw new ApiException(400, "Сумма уплаты должна быть больше нуля");
        return p;
    }
}
