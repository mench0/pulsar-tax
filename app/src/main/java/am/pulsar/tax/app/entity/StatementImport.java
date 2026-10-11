package am.pulsar.tax.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

/** Загруженная выписка: откуда пришли операции и итоги проверки. */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
@Table(name = "statement_imports")
public class StatementImport extends BaseEntity {

    @Column(nullable = false)
    Long businessId;

    @Column(nullable = false)
    @Builder.Default
    String fileName = "";

    @Column(nullable = false)
    @Builder.Default
    String fileSha256 = "";

    @Column(nullable = false)
    @Builder.Default
    String account = "";

    /** JSON сопоставления колонок. */
    @Column(nullable = false)
    @Builder.Default
    String mapping = "{}";

    @Column(nullable = false)
    @Builder.Default
    Integer rowsTotal = 0;

    @Column(nullable = false)
    @Builder.Default
    Integer rowsAdded = 0;

    @Column(nullable = false)
    @Builder.Default
    Integer rowsDuplicate = 0;

    @Column(nullable = false)
    @Builder.Default
    Integer rowsError = 0;

    @Column(nullable = false)
    @Builder.Default
    Integer rowsSkipped = 0;
}
