package am.pulsar.tax.app.controller;import am.pulsar.tax.app.entity.ImportProfile;
import am.pulsar.tax.app.mapper.entity.ImportProfileMapper;
import am.pulsar.tax.app.service.ImportProfileService;
import am.pulsar.tax.dto.ImportProfileDto;
import am.pulsar.tax.dto.create.ImportProfileCreateDto;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Сохранённые колонки банковских выписок по отпечатку заголовка (его считает приложение). */
@RestController
@RequestMapping("/api/import-profiles")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ImportProfileController {

    ImportProfileService importProfileService;
    ImportProfileMapper importProfileMapper;

    /** Отпечаток → колонки, от старых к новым. */
    @GetMapping
    public Map<String, ImportProfileDto> getAll() {
        Map<String, ImportProfileDto> result = new LinkedHashMap<>();
        for (ImportProfile profile : importProfileService.getAll()) {
            result.put(profile.getSignature(), importProfileMapper.toDto(profile));
        }
        return result;
    }

    @PutMapping("/{signature:[0-9a-z-]{4,64}}")
    public ResponseEntity<Void> save(@PathVariable("signature") String signature, @RequestBody ImportProfileCreateDto createDto) {
        importProfileService.save(signature, createDto);
        return ResponseEntity.noContent().build();
    }
}
