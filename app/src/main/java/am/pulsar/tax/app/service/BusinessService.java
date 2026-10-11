package am.pulsar.tax.app.service;import am.pulsar.tax.app.entity.Business;
import am.pulsar.tax.app.repository.BusinessRepository;
import am.pulsar.tax.app.service.provider.BusinessProvider;
import am.pulsar.tax.app.utils.AuthenticationHolder;
import am.pulsar.tax.dto.patch.BusinessPatchDto;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Профиль ИП: один на пользователя, создаётся с настройками по умолчанию при первом обращении. */
@Service
@Validated
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BusinessService {

    BusinessRepository repository;
    BusinessProvider provider;

    @Transactional
    public Business getOrCreateForUser(Long userId) {
        return repository.findByOwnerId(userId).orElseGet(() -> {
            repository.insertIfAbsent(userId);
            return repository.findByOwnerId(userId).orElseThrow();
        });
    }

    @Transactional(readOnly = true)
    public Business getCurrent() {
        return repository.findById(AuthenticationHolder.getCurrentBusinessIdOrElseThrow()).orElseThrow();
    }

    /** Изменяет профиль текущего пользователя; id в DTO подставляет контроллер. */
    @Transactional
    public Business patchCurrent(@Valid BusinessPatchDto patchDto) {
        Business business = getCurrent();
        provider.patch(patchDto, business);
        return repository.saveAndFlush(business);
    }
}
