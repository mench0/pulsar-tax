package am.pulsar.tax.app.mapper.entity.util;import org.mapstruct.Mapper;

import java.util.Optional;

@Mapper(componentModel = "spring")
public class OptionalMapper {

    public <T> T map(Optional<T> source) {
        return source.orElse(null);
    }
}
