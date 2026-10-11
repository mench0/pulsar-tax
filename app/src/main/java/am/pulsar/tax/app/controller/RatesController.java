package am.pulsar.tax.app.controller;import am.pulsar.tax.app.integration.cba.service.CbaRatesService;
import am.pulsar.tax.app.utils.ClockUtils;
import am.pulsar.tax.app.utils.RequestUtils;
import am.pulsar.tax.dto.RatesDto;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Курсы ЦБ РА на дату. */
@RestController
@RequestMapping("/api/rates")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RatesController {

    CbaRatesService cbaRatesService;

    @GetMapping
    public RatesDto getRates(@RequestParam(required = false) String date) {
        LocalDate day = RequestUtils.date(date);
        return new RatesDto(day, "cba.am", cbaRatesService.on(day, ClockUtils.today()));
    }
}
