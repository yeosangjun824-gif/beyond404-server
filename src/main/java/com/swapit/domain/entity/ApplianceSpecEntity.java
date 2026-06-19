package com.swapit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "appliance_specs")
public class ApplianceSpecEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "spec_id")
    private Long id;

    @Column(name = "brand", length = 50, nullable = false)
    private String brand;

    @Column(name = "appliance_type", length = 30, nullable = false)
    private String applianceType;

    @Column(name = "model_name", length = 100, nullable = false)
    private String modelName;

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "capacity_l", precision = 8, scale = 1)
    private BigDecimal capacityL;

    @Column(name = "capacity_kg", precision = 6, scale = 1)
    private BigDecimal capacityKg;

    @Column(name = "screen_inch", precision = 5, scale = 1)
    private BigDecimal screenInch;

    @Column(name = "size_grade", length = 10)
    private String sizeGrade;

    protected ApplianceSpecEntity() {}

    public String getBrand() { return brand; }
    public String getModelName() { return modelName; }
    public String getSizeGrade() { return sizeGrade; }
    public BigDecimal getCapacityL() { return capacityL; }
    public BigDecimal getCapacityKg() { return capacityKg; }
    public BigDecimal getScreenInch() { return screenInch; }
    public BigDecimal getWeightKg() { return weightKg; }
    public String getApplianceType() { return applianceType; }

    public String buildSizeMetric() {
        if ("tv".equals(applianceType) && screenInch != null) {
            return screenInch.stripTrailingZeros().toPlainString() + "인치";
        }
        if (("refrigerator".equals(applianceType) || "microwave".equals(applianceType)) && capacityL != null) {
            return capacityL.stripTrailingZeros().toPlainString() + "L";
        }
        if (weightKg != null) {
            return weightKg.stripTrailingZeros().toPlainString() + "kg";
        }
        return null;
    }
}
