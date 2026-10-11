package am.pulsar.tax.app.service.provider;import am.pulsar.tax.app.entity.Invoice;
import am.pulsar.tax.app.entity.InvoiceItem;
import am.pulsar.tax.app.mapper.entity.util.OptionalMapper;
import am.pulsar.tax.app.utils.ClockUtils;
import am.pulsar.tax.dto.create.InvoiceCreateDto.InvoiceItemInputDto;
import am.pulsar.tax.dto.create.InvoiceCreateDto;
import am.pulsar.tax.dto.patch.InvoicePatchDto;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", uses = OptionalMapper.class)
public abstract class InvoiceProvider extends EntityProvider<Invoice, InvoiceCreateDto, InvoicePatchDto> {

    @Override
    @Mapping(target = "status", ignore = true)
    public abstract void initializeEntity(InvoiceCreateDto source, @MappingTarget Invoice target);

    protected abstract InvoiceItem toItem(InvoiceItemInputDto source);

    @AfterMapping
    protected void afterInitializeEntity(InvoiceCreateDto source, @MappingTarget Invoice target) {
        if (target.getDate() == null) {
            target.setDate(ClockUtils.today());
        }
        target.setNumber(trimToEmpty(target.getNumber()));
        target.getItems().forEach(item -> item.setName(item.getName().trim()));
        normalize(target);
    }

    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract void patchEntity(InvoicePatchDto patchDto, @MappingTarget Invoice entity);

    @AfterMapping
    protected void afterPatchEntity(InvoicePatchDto patchDto, @MappingTarget Invoice target) {
        if (target.getClient() == null || target.getClient().isBlank()) {
            addValidationError("Заполните поле «client»");
        }
        normalize(target);
    }

    private void normalize(Invoice target) {
        target.setClient(trimToEmpty(target.getClient()));
        target.setClientTin(trimToEmpty(target.getClientTin()));
        target.setEInvoiceNumber(trimToEmpty(target.getEInvoiceNumber()));
    }

    @Override
    protected Class<Invoice> getEntityClass() {
        return Invoice.class;
    }
}
