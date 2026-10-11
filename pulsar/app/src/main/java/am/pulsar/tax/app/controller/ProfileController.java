package am.pulsar.tax.app.controller;import am.pulsar.tax.app.mapper.entity.BusinessMapper;
import am.pulsar.tax.app.service.BusinessService;
import am.pulsar.tax.dto.BusinessDto;
import am.pulsar.tax.dto.patch.BusinessPatchDto;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Профиль ИП текущего пользователя. */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProfileController {

    BusinessService businessService;
    BusinessMapper businessMapper;

    @GetMapping
    public BusinessDto get() {
        return businessMapper.toDto(businessService.getCurrent());
    }

    @PutMapping
    public BusinessDto update(@RequestBody BusinessPatchDto patchDto) {
        patchDto.setId(businessService.getCurrent().getId());
        return businessMapper.toDto(businessService.patchCurrent(patchDto));
    }
}
