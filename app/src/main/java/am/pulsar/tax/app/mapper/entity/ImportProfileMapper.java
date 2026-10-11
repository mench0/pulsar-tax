package am.pulsar.tax.app.mapper.entity;import am.pulsar.tax.app.entity.ImportProfile;
import am.pulsar.tax.dto.ImportProfileDto;
import am.pulsar.tax.dto.create.ImportProfileCreateDto;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

/** Колонки выписки хранятся в базе одним JSON (import_profiles.mapping). */
@Mapper(componentModel = "spring")
public abstract class ImportProfileMapper {

    @Autowired
    protected JsonMapper jsonMapper;

    public ImportProfileDto toDto(ImportProfile entity) {
        return jsonMapper.readValue(entity.getMapping(), ImportProfileDto.class);
    }

    public abstract ImportProfileDto toDto(ImportProfileCreateDto createDto);

    public String toJson(ImportProfileCreateDto createDto) {
        return jsonMapper.writeValueAsString(toDto(createDto));
    }
}
