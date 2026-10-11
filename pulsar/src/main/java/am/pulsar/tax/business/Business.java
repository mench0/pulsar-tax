package am.pulsar.tax.business;

import am.pulsar.tax.shared.Enums.ActivityType;
import am.pulsar.tax.shared.Enums.TaxRegime;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Индивидуальный предприниматель (профиль). Пока один на пользователя. */
@Entity
@Table(name = "businesses")
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "owner_id")
    private Long ownerId;
    private String name = "";
    private String tin = "";
    private String address = "";
    @Column(name = "bank_account")
    private String bankAccount = "";
    @Enumerated(EnumType.STRING)
    private TaxRegime regime = TaxRegime.MICRO;
    @Enumerated(EnumType.STRING)
    private ActivityType activity = ActivityType.SERVICES;
    /** Ставка в процентах для ActivityType.CUSTOM. */
    @Column(name = "custom_rate")
    private BigDecimal customRate;

    protected Business() {
    }

    public Business(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getTin() { return tin; }
    public String getAddress() { return address; }
    public String getBankAccount() { return bankAccount; }
    public TaxRegime getRegime() { return regime; }
    public ActivityType getActivity() { return activity; }
    public BigDecimal getCustomRate() { return customRate; }

    public void update(String name, String tin, String address, String bankAccount, TaxRegime regime,
                       ActivityType activity, BigDecimal customRate) {
        this.name = name;
        this.tin = tin;
        this.address = address;
        this.bankAccount = bankAccount;
        this.regime = regime;
        this.activity = activity;
        this.customRate = customRate;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("tin", tin);
        m.put("address", address);
        m.put("bankAccount", bankAccount);
        m.put("regime", regime);
        m.put("activity", activity);
        m.put("customRate", customRate);
        return m;
    }
}
