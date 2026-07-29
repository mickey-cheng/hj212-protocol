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
    @Max(value = 1, groups = VersionGroup.V2025.class)
    @JsonProperty("RS")
    private int rs;

    /**
     * 污染治理设施一日内的运行时间
     * <p>取值[0, 24]小时</p>
     */
    @DecimalMax(value = "24", groups = VersionGroup.V2017.class)
    @DecimalMax(value = "24", groups = VersionGroup.V2025.class)
    @JsonProperty("RT")
    private BigDecimal rt;

    public int getRs() {
        return rs;
    }

    public void setRs(int rs) {
        this.rs = rs;
    }

    public BigDecimal getRt() {
        return rt;
    }

    public void setRt(BigDecimal rt) {
        this.rt = rt;
    }
}
