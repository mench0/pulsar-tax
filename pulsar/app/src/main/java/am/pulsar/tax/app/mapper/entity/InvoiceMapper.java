package am.pulsar.tax.app.mapper.entity;import am.pulsar.tax.app.entity.Invoice;
import am.pulsar.tax.app.entity.InvoiceItem;
import am.pulsar.tax.dto.InvoiceDto.InvoiceItemDto;
import am.pulsar.tax.dto.InvoiceDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class InvoiceMapper implements EntityMapper<Invoice, InvoiceDto> {

    @Override
    @Mapping(target = "total", expression = "java(entity.total())")
    @Mapping(target = "eInvoiceNumber", source = "EInvoiceNumber")
    public abstract InvoiceDto toDto(Invoice entity);

    @Mapping(target = "sum", expression = "java(item.sum())")
    protected abstract InvoiceItemDto toItemDto(InvoiceItem item);
}
