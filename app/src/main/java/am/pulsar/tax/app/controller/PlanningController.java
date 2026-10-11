package am.pulsar.tax.app.controller;

import am.pulsar.tax.app.entity.Business;
import am.pulsar.tax.app.service.BusinessService;
import am.pulsar.tax.app.service.PlanningService;
import am.pulsar.tax.app.service.PlanningService.Reserve;
import am.pulsar.tax.app.service.calculation.Planning;
import am.pulsar.tax.app.utils.ClockUtils;
import am.pulsar.tax.app.utils.RequestUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Налоговый резерв и прогноз денежных потоков. */
@RestController
@RequestMapping("/api/planning")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PlanningController {

    BusinessService businessService;
    PlanningService planningService;

    @GetMapping("/reserve")
    public Reserve reserve() {
        return planningService.reserve(businessService.getCurrent(), ClockUtils.today());
    }

    @GetMapping("/forecast")
    public Planning.Forecast forecast(@RequestParam(required = false, defaultValue = "3") int months) {
        return planningService.forecast(businessService.getCurrent(), months, ClockUtils.today());
    }

    /** Остатки, известные только пользователю: сколько отложено на налоги и сколько денег на счетах. */
    public record BalancesRequest(BigDecimal reserveBalance, BigDecimal cashBalance, String asOf) {
    }

    @PutMapping("/balances")
    public Map<String, Object> balances(@RequestBody BalancesRequest r) {
        Business b = planningService.updateBalances(businessService.getCurrent(), r.reserveBalance(), r.cashBalance(),
                r.asOf() == null || r.asOf().isBlank() ? null : RequestUtils.date(r.asOf()), ClockUtils.today());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reserveBalance", b.getReserveBalance());
        m.put("cashBalance", b.getCashBalance());
        m.put("asOf", b.getBalancesAsOf());
        return m;
    }
}
