package am.pulsar.tax.app.mapper.entity;import am.pulsar.tax.app.entity.Expense;
import am.pulsar.tax.dto.ExpenseDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class ExpenseMapper implements EntityMapper<Expense, ExpenseDto> {
}
