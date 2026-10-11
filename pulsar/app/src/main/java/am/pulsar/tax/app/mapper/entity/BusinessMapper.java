package am.pulsar.tax.app.mapper.entity;import am.pulsar.tax.app.entity.Business;
import am.pulsar.tax.dto.BusinessDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class BusinessMapper implements EntityMapper<Business, BusinessDto> {
}
