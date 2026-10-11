package am.pulsar.tax.dto.enums;

/** Режим налогообложения. GENERAL (НДС и подоходный налог) не рассчитывается. */
public enum TaxRegime {
    MICRO, TURNOVER, GENERAL
}
