package am.pulsar.tax.controller;

import am.pulsar.tax.client.CbaRates;
import am.pulsar.tax.util.Requests;
import am.pulsar.tax.util.Yerevan;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class RatesController {

    private final CbaRates rates;
    private final Yerevan yerevan;

    public RatesController(CbaRates rates, Yerevan yerevan) {
        this.rates = rates;
        this.yerevan = yerevan;
    }

    @GetMapping("/api/rates")
    Map<String, Object> rates(@RequestParam(required = false) String date) {
        LocalDate today = yerevan.today();
        LocalDate d = Requests.date(date, today);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", d);
        m.put("source", "cba.am");
        m.put("rates", rates.on(d, today));
        return m;
    }
}
