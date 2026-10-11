package am.pulsar.tax.app.controller;

import am.pulsar.tax.app.entity.Business;
import am.pulsar.tax.app.entity.TaxReport;
import am.pulsar.tax.app.service.BusinessService;
import am.pulsar.tax.app.service.ReportService;
import am.pulsar.tax.app.service.ReportService.Readiness;
import am.pulsar.tax.app.utils.ClockUtils;
import am.pulsar.tax.app.utils.RequestUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Подготовка налоговых отчётов к ручной подаче в КГД: проверки, версии, выгрузка, отметка о подаче. */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReportController {

    BusinessService businessService;
    ReportService reportService;
    JsonMapper json;

    /** Готовность данных за период: проверки, источники оборота, расчёт и инструкция по подаче. */
    @GetMapping("/readiness")
    public Readiness readiness(@RequestParam(required = false) String year, @RequestParam(required = false) Integer quarter) {
        return reportService.readiness(businessService.getCurrent(), RequestUtils.year(year), quarter, ClockUtils.today());
    }

    public record PrepareRequest(Integer year, Integer quarter, Boolean acceptWarnings) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> prepare(@RequestBody PrepareRequest request) {
        Business b = businessService.getCurrent();
        int year = request.year() == null ? ClockUtils.today().getYear() : RequestUtils.year(String.valueOf(request.year()));
        return info(b, reportService.prepare(b, year, request.quarter(), Boolean.TRUE.equals(request.acceptWarnings()),
                ClockUtils.today()), false);
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String year) {
        Business b = businessService.getCurrent();
        return reportService.list(b, RequestUtils.year(year)).stream().map(r -> info(b, r, false)).toList();
    }

    /** Отчёт и изменения в данных после его подготовки (stale = true — нужно подготовить заново). */
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        Business b = businessService.getCurrent();
        return info(b, reportService.get(b, id), true);
    }

    public record FiledRequest(String filedOn, String reference, String note) {
    }

    @PostMapping("/{id}/filed")
    public Map<String, Object> filed(@PathVariable Long id, @RequestBody FiledRequest request) {
        Business b = businessService.getCurrent();
        LocalDate date = request.filedOn() == null ? null : RequestUtils.date(request.filedOn());
        return info(b, reportService.markFiled(b, id, date, request.reference(), request.note(), ClockUtils.today()), false);
    }

    @DeleteMapping("/{id}/filed")
    public Map<String, Object> unfiled(@PathVariable Long id) {
        Business b = businessService.getCurrent();
        return info(b, reportService.unmarkFiled(b, id), false);
    }

    @GetMapping("/{id}/export.csv")
    public ResponseEntity<String> csv(@PathVariable Long id) {
        Business b = businessService.getCurrent();
        TaxReport r = reportService.get(b, id);
        String name = "pulsar-" + r.getType().toLowerCase() + "-" + r.getYear() + (r.getQuarter() == null ? "" : "-q" + r.getQuarter())
                + "-v" + r.getVersion() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(reportService.csv(b, r));
    }

    private Map<String, Object> info(Business b, TaxReport r, boolean withChanges) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("type", r.getType());
        m.put("year", r.getYear());
        m.put("quarter", r.getQuarter());
        m.put("version", r.getVersion());
        m.put("status", r.getStatus());
        m.put("snapshotId", r.getSnapshotId());
        m.put("turnover", r.getTurnover());
        m.put("accrued", r.getAccrued());
        m.put("paid", r.getPaid());
        m.put("due", r.getDue());
        m.put("acceptedWarnings", json.readTree(r.getWarnings()));
        m.put("filedOn", r.getFiledOn());
        m.put("filingReference", r.getFilingReference());
        m.put("filingNote", r.getFilingNote());
        m.put("createdAt", r.getCreatedAt());
        if (withChanges) {
            var diff = reportService.changesSince(b, r);
            m.put("stale", diff.changed());
            m.put("changes", diff);
        }
        return m;
    }
}
