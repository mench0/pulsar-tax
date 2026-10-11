package am.pulsar.tax.app.service.provider;import am.pulsar.tax.app.entity.Expense;
import am.pulsar.tax.app.mapper.entity.util.OptionalMapper;
import am.pulsar.tax.dto.create.ExpenseCreateDto;
import am.pulsar.tax.dto.enums.ExpenseCategory;
import am.pulsar.tax.dto.patch.ExpensePatchDto;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", uses = OptionalMapper.class)
public abstract class ExpenseProvider extends EntityProvider<Expense, ExpenseCreateDto, ExpensePatchDto> {

    @Override
    public abstract void initializeEntity(ExpenseCreateDto source, @MappingTarget Expense target);

    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract void patchEntity(ExpensePatchDto patchDto, @MappingTarget Expense entity);

    @AfterMapping
    protected void normalize(@MappingTarget Expense target) {
        if (target.getDate() == null) {
            addValidationError("Укажите дату «date»");
        }
        if (target.getAmount() == null) {
            addValidationError("Заполните поле «amount»");
        }
        if (target.getCategory() == null) {
            target.setCategory(ExpenseCategory.OTHER);
        }
        target.setDescription(trimToEmpty(target.getDescription()));
    }

    @Override
    protected Class<Expense> getEntityClass() {
        return Expense.class;
    }
}
