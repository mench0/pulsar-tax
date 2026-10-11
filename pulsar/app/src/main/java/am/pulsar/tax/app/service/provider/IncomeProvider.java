package am.pulsar.tax.app.service.provider;import am.pulsar.tax.app.entity.Income;
import am.pulsar.tax.app.mapper.entity.util.OptionalMapper;
import am.pulsar.tax.dto.create.IncomeCreateDto;
import am.pulsar.tax.dto.enums.Currency;
import am.pulsar.tax.dto.patch.IncomePatchDto;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Доход из DTO. В драмах передаётся amount; в другой валюте — originalAmount и rate (драмов за единицу),
 * а сумма в драмах считается здесь и округляется до драма.
 */
@Mapper(componentModel = "spring", uses = OptionalMapper.class)
public abstract class IncomeProvider extends EntityProvider<Income, IncomeCreateDto, IncomePatchDto> {

    @Override
    @Mapping(target = "amount", ignore = true)
    @Mapping(target = "originalAmount", ignore = true)
    @Mapping(target = "rate", ignore = true)
    @Mapping(target = "currency", ignore = true)
    public abstract void initializeEntity(IncomeCreateDto source, @MappingTarget Income target);

    @AfterMapping
    protected void afterInitializeEntity(IncomeCreateDto source, @MappingTarget Income target) {
        Currency currency = source.getCurrency() == null ? Currency.AMD : source.getCurrency();
        target.setCurrency(currency);
        if (currency == Currency.AMD) {
            if (source.getAmount() == null) {
                addValidationError("Заполните поле «amount»");
                return;
            }
            target.setAmount(source.getAmount());
        } else {
            if (source.getOriginalAmount() == null) {
                addValidationError("Заполните поле «originalAmount»");
                return;
            }
            if (source.getRate() == null) {
                addValidationError("Заполните поле «rate»");
                return;
            }
            if (source.getOriginalAmount().signum() <= 0) {
                addValidationError("Сумма в валюте должна быть больше нуля");
            }
            if (source.getRate().signum() <= 0) {
                addValidationError("Курс должен быть больше нуля");
            }
            target.setOriginalAmount(source.getOriginalAmount());
            target.setRate(source.getRate());
            target.setAmount(source.getOriginalAmount().multiply(source.getRate()).setScale(0, RoundingMode.HALF_UP));
        }
        normalize(target);
        String key = source.getImportKey() == null ? "" : source.getImportKey().trim();
        target.setImportKey(key.isEmpty() ? null : key);
    }

    @Override
    @Mapping(target = "amount", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract void patchEntity(IncomePatchDto patchDto, @MappingTarget Income entity);

    @AfterMapping
    protected void afterPatchEntity(IncomePatchDto patchDto, @MappingTarget Income target) {
        if (patchDto.getAmount() != null) {
            if (target.getCurrency() != Currency.AMD) {
                addValidationError("Сумму в драмах можно менять только у поступлений в драмах");
                return;
            }
            target.setAmount(patchDto.getAmount().orElse(null));
        }
        normalize(target);
    }

    private void normalize(Income target) {
        if (target.getDate() == null) {
            addValidationError("Укажите дату «date»");
        }
        BigDecimal amount = target.getAmount();
        if (amount == null) {
            addValidationError("Заполните поле «amount»");
        } else if (amount.signum() <= 0) {
            addValidationError("Сумма дохода должна быть больше нуля");
        } else if (amount.scale() > 2) {
            addValidationError("Сумма дохода: не больше 2 знаков после запятой");
        } else if (amount.precision() - amount.scale() > 12) {
            addValidationError("Сумма дохода слишком большая");
        }
        target.setClient(trimToEmpty(target.getClient()));
        target.setDescription(trimToEmpty(target.getDescription()));
    }

    @Override
    protected Class<Income> getEntityClass() {
        return Income.class;
    }
}
