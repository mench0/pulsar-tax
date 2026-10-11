package am.pulsar.tax.app.service.provider;import am.pulsar.tax.app.entity.TaxPayment;
import am.pulsar.tax.app.mapper.entity.util.OptionalMapper;
import am.pulsar.tax.dto.create.TaxPaymentCreateDto;
import am.pulsar.tax.dto.patch.TaxPaymentPatchDto;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", uses = OptionalMapper.class)
public abstract class TaxPaymentProvider extends EntityProvider<TaxPayment, TaxPaymentCreateDto, TaxPaymentPatchDto> {

    @Override
    public abstract void initializeEntity(TaxPaymentCreateDto source, @MappingTarget TaxPayment target);

    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract void patchEntity(TaxPaymentPatchDto patchDto, @MappingTarget TaxPayment entity);

    @AfterMapping
    protected void validate(@MappingTarget TaxPayment target) {
        if (target.getDate() == null) {
            addValidationError("Укажите дату «date»");
        }
        if (target.getAmount() == null) {
            addValidationError("Заполните поле «amount»");
        }
    }

    @Override
    protected Class<TaxPayment> getEntityClass() {
        return TaxPayment.class;
    }
}
