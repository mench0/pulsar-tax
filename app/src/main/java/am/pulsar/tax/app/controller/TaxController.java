package am.pulsar.tax.app.controller;

import am.pulsar.tax.app.entity.TaxSnapshot;
import am.pulsar.tax.app.service.BusinessService;
import am.pulsar.tax.app.service.SummaryService;
import am.pulsar.tax.app.service.TaxCalculationService;
import am.pulsar.tax.app.service.calculation.TaxDiff;
import am.pulsar.tax.app.service.calculation.TaxEngine.Calculation;
import am.pulsar.tax.app.service.calculation.TaxRules;
import am.pulsar.tax.app.utils.ClockUtils;
import am.pulsar.tax.app.utils.RequestUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Налоговые правила, сводка за год, подробный расчёт с объяснением и снимки расчёта. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TaxController {

    BusinessService businessService;
    SummaryService summaryService;
    TaxCalculationService calculationService;

    /** Справочник доступен без входа: он нужен ещё до регистрации. */
    @GetMapping("/rules")
    public Map<String, Object> rules(@RequestParam(required = false) String year) {
        return TaxRules.toJson(RequestUtils.year(year));
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam(required = false) String year) {
        return summaryService.summary(businessService.getCurrent(), RequestUtils.year(year), ClockUtils.today());
    }

    /** Расчёт за квартал (quarter=1..4) или за год (без quarter): операции, правила, шаги и замечания. */
    @GetMapping("/tax/calculation")
    public Calculation calculation(@RequestParam(required = false) String year,
                                   @RequestParam(required = false) Integer quarter) {
        return calculationService.calculate(businessService.getCurrent(), RequestUtils.year(year), quarter);
    }

    public record SnapshotRequest(Integer year, Integer quarter, String reason) {
    }

    @PostMapping("/tax/snapshots")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> snapshot(@RequestBody SnapshotRequest request) {
        int year = request.year() == null ? ClockUtils.today().getYear() : request.year();
        return info(calculationService.snapshot(businessService.getCurrent(), RequestUtils.year(String.valueOf(year)),
                request.quarter(), request.reason()));
    }

    @GetMapping("/tax/snapshots")
    public List<Map<String, Object>> snapshots(@RequestParam(required = false) String year) {
        return calculationService.snapshots(businessService.getCurrent(), RequestUtils.year(year)).stream().map(this::info).toList();
    }

    /** Сохранённый расчёт целиком. */
    @GetMapping("/tax/snapshots/{id}")
    public Calculation snapshot(@PathVariable Long id) {
        return calculationService.read(calculationService.snapshot(businessService.getCurrent(), id));
    }

    /** Почему сумма изменилась: снимок против текущего расчёта. */
    @GetMapping("/tax/snapshots/{id}/diff")
    public TaxDiff.Result diff(@PathVariable Long id) {
        return calculationService.diff(businessService.getCurrent(), id);
    }

    private Map<String, Object> info(TaxSnapshot s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("year", s.getYear());
        m.put("quarter", s.getQuarter());
        m.put("reason", s.getReason());
        m.put("accrued", s.getAccrued());
        m.put("createdAt", s.getCreatedAt());
        return m;
    }
}
