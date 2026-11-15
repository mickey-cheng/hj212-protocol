package com.github.guocay.hj212.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.guocay.hj212.model.verify.groups.VersionGroup;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;

import java.math.BigDecimal;

/**
 * 污染治理设施
 * <p>HJ 212-2025: SByyy-RS/RT其中yyy为设备号，取值[0, 999]</p>
 * @author aCay
 */
public class Device {

    /**
     * 污染治理设施运行状态的实时采样值
     * <p>HJ 212-2025: 0：关闭，1：运行</p>
     */
    @Max(value = 5, groups = VersionGroup.V2017.class)
    @jakarta.validation.constraints.Min(value = 0, groups = VersionGroup.V2025.class)
    @Max(value = 1, groups = VersionGroup.V2025.class)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Integer rs;

    /**
     * 污染治理设施一日内的运行时间
     * <p>取值[0, 24]小时</p>
     */
    @DecimalMax(value = "24", groups = VersionGroup.V2017.class)
    @jakarta.validation.constraints.DecimalMin(value = "0", groups = VersionGroup.V2025.class)
    @DecimalMax(value = "24", groups = VersionGroup.V2025.class)
    @JsonProperty("RT")
    private BigDecimal rt;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public int getRs() {
        return rs == null ? 0 : rs;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public void setRs(int rs) {
        this.rs = rs;
    }

    @JsonProperty("RS")
    public Integer getRsValue() { return rs; }
    @JsonProperty("RS")
    public void setRsValue(Integer rs) { this.rs = rs; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean hasRs() { return rs != null; }

    public BigDecimal getRt() {
        return rt;
    }

    public void setRt(BigDecimal rt) {
        this.rt = rt;
    }
}
