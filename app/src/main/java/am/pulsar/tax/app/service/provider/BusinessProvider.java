package am.pulsar.tax.app.service.provider;import am.pulsar.tax.app.entity.Business;
import am.pulsar.tax.app.mapper.entity.util.OptionalMapper;
import am.pulsar.tax.dto.create.BaseCreateDto;
import am.pulsar.tax.dto.enums.ActivityType;
import am.pulsar.tax.dto.enums.TaxRegime;
import am.pulsar.tax.dto.patch.BusinessPatchDto;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/** Профиль ИП. Создаётся пустым при первом входе (BusinessService), меняется через patch. */
@Mapper(componentModel = "spring", uses = OptionalMapper.class)
public abstract class BusinessProvider extends EntityProvider<Business, BaseCreateDto<?>, BusinessPatchDto> {

    @Override
    protected void initializeEntity(BaseCreateDto<?> createDto, @MappingTarget Business entity) {
        throw new UnsupportedOperationException("Профиль создаётся автоматически");
    }

    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract void patchEntity(BusinessPatchDto patchDto, @MappingTarget Business entity);

    @AfterMapping
    protected void afterPatchEntity(BusinessPatchDto patchDto, @MappingTarget Business target) {
        if (target.getRegime() == null) {
            addValidationError("Укажите «regime»");
        }
        if (target.getActivity() == null) {
            addValidationError("Укажите «activity»");
        }
        if (target.getRegime() == TaxRegime.TURNOVER && target.getActivity() == ActivityType.CUSTOM
                && target.getCustomRate() == null) {
            addValidationError("Для своего вида деятельности укажите ставку налога");
        }
        target.setName(trimToEmpty(target.getName()));
        target.setTin(trimToEmpty(target.getTin()));
        target.setAddress(trimToEmpty(target.getAddress()));
        target.setBankAccount(trimToEmpty(target.getBankAccount()));
    }

    @Override
    protected Class<Business> getEntityClass() {
        return Business.class;
    }
}
