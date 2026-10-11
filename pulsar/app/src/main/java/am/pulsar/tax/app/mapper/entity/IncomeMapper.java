package am.pulsar.tax.app.mapper.entity;import am.pulsar.tax.app.entity.Income;
import am.pulsar.tax.dto.IncomeDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class IncomeMapper implements EntityMapper<Income, IncomeDto> {
}
