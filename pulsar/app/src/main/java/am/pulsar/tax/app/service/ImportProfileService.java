package am.pulsar.tax.app.service;import am.pulsar.tax.app.entity.ImportProfile;
import am.pulsar.tax.app.mapper.entity.ImportProfileMapper;
import am.pulsar.tax.app.repository.ImportProfileRepository;
import am.pulsar.tax.app.utils.AuthenticationHolder;
import am.pulsar.tax.app.utils.ClockUtils;
import am.pulsar.tax.dto.create.ImportProfileCreateDto;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** Сохранённые колонки банковских выписок: следующая выписка того же банка разберётся сама. */
@Service
@Validated
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ImportProfileService {

    /** Хватит с запасом на все банки пользователя; самые старые настройки уходят первыми. */
    private static final int MAX_PROFILES = 20;

    ImportProfileRepository repository;
    ImportProfileMapper mapper;

    /** Настройки текущего ИП, от старых к новым. */
    @Transactional(readOnly = true)
    public List<ImportProfile> getAll() {
        return repository.findByBusinessIdOrderByUpdatedAtAsc(AuthenticationHolder.getCurrentBusinessIdOrElseThrow());
    }

    @Transactional
    public void save(String signature, @Valid ImportProfileCreateDto createDto) {
        Long businessId = AuthenticationHolder.getCurrentBusinessIdOrElseThrow();
        ImportProfile profile = repository.findById(new ImportProfile.Key(businessId, signature))
                .orElseGet(() -> ImportProfile.builder().businessId(businessId).signature(signature).build());
        profile.setMapping(mapper.toJson(createDto));
        profile.setUpdatedAt(ClockUtils.now());
        repository.saveAndFlush(profile);
        List<ImportProfile> all = repository.findByBusinessIdOrderByUpdatedAtAsc(businessId);
        if (all.size() > MAX_PROFILES) {
            repository.deleteAll(all.subList(0, all.size() - MAX_PROFILES));
        }
    }
}
