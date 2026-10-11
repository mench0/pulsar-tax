package am.pulsar.tax.app.mapper.entity;import am.pulsar.tax.app.entity.TaxPayment;
import am.pulsar.tax.dto.TaxPaymentDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class TaxPaymentMapper implements EntityMapper<TaxPayment, TaxPaymentDto> {
}
