package am.pulsar.tax.app.controller;import am.pulsar.tax.app.service.BusinessService;
import am.pulsar.tax.app.service.SummaryService;
import am.pulsar.tax.app.service.calculation.TaxRules;
import am.pulsar.tax.app.utils.ClockUtils;
import am.pulsar.tax.app.utils.RequestUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Налоговые правила года и сводка за год. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TaxController {

    BusinessService businessService;
    SummaryService summaryService;

    /** Справочник доступен без входа: он нужен ещё до регистрации. */
    @GetMapping("/rules")
    public Map<String, Object> rules(@RequestParam(required = false) String year) {
        return TaxRules.toJson(RequestUtils.year(year));
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam(required = false) String year) {
        return summaryService.summary(businessService.getCurrent(), RequestUtils.year(year), ClockUtils.today());
    }
}
