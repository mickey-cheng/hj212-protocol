package com.github.guocay.hj212.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.guocay.hj212.core.validator.field.CharArray;
import com.github.guocay.hj212.core.validator.field.Date;
import com.github.guocay.hj212.core.validator.field.NumberArray;
import com.github.guocay.hj212.model.verify.groups.VersionGroup;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 污染因子
 * @author aCay
 */
@SuppressWarnings("unused")
public class Pollution {

    /**
     * 污染物采样时间
     */
    @Date(format = "yyyyMMddHHmmssSSS", groups = VersionGroup.V2017.class)
    @Date(format = "yyyyMMddHHmmss", groups = VersionGroup.V2025.class)
    @JsonProperty("SampleTime")
    private String sampleTime;

    /**
     * 监测参数完成测量时间（HJ 212-2025 新增）
     * @since 2025
     */
    @Date(format = "yyyyMMddHHmmss", groups = VersionGroup.V2025.class)
    @JsonProperty("CompleteTime")
    private String completeTime;

    /** 附录 C.27/C.28 中与监测参数关联的采样模式。 */
    @jakarta.validation.constraints.Min(value = 0, groups = VersionGroup.V2025.class)
    @Max(value = 4, groups = VersionGroup.V2025.class)
    @JsonProperty("SampleType")
    private Integer sampleType;

    /**
     * 污染物实时采样数据
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private BigDecimal rtd;
    private String rtdText;

    /**
     * 监测参数实时工况采样数据（HJ 212-2025 新增）
     * @since 2025
     */
    @JsonProperty("WcRtd")
    private BigDecimal wcRtd;

    /**
     * 监测参数实时原始采样数据（HJ 212-2025 新增）
     * @since 2025
     */
    @JsonProperty("RawRtd")
    private BigDecimal rawRtd;

    /**
     * 污染物指定时间内最小值
     */
    @JsonProperty("Min")
    private BigDecimal min;

    /**
     * 监测参数指定时间段内工况数据最小值（HJ 212-2025 新增）
     * @since 2025
     */
    @JsonProperty("WcMin")
    private BigDecimal wcMin;

    /**
     * 污染物指定时间内平均值
     */
    @JsonProperty("Avg")
    private BigDecimal avg;

    /**
     * 监测参数指定时间段内工况数据平均值（HJ 212-2025 新增）
     * @since 2025
     */
    @JsonProperty("WcAvg")
    private BigDecimal wcAvg;

    /**
     * 污染物指定时间内最大值
     */
    @JsonProperty("Max")
    private BigDecimal max;

    /**
     * 监测参数指定时间段内工况数据最大值（HJ 212-2025 新增）
     * @since 2025
     */
    @JsonProperty("WcMax")
    private BigDecimal wcMax;

    /**
     * 监测参数指定时间段内原始数据最小值（HJ 212-2025 新增）
     * @since 2025
     */
    @JsonProperty("RawMin")
    private BigDecimal rawMin;

    /**
     * 监测参数指定时间段内原始数据平均值（HJ 212-2025 新增）
     * @since 2025
     */
    @JsonProperty("RawAvg")
    private BigDecimal rawAvg;

    /**
     * 监测参数指定时间段内原始数据最大值（HJ 212-2025 新增）
     * @since 2025
     */
    @JsonProperty("RawMax")
    private BigDecimal rawMax;

    /**
     * 污染物实时采样折算数据
     */
    @JsonProperty("ZsRtd")
    private BigDecimal zsRtd;

    /**
     * 污染物指定时间内最小折算值
     */
    @JsonProperty("ZsMin")
    private BigDecimal zsMin;

    /**
     * 污染物指定时间内平均折算值
     */
    @JsonProperty("ZsAvg")
    private BigDecimal zsAvg;

    /**
     * 污染物指定时间内最大折算值
     */
    @JsonProperty("ZsMax")
    private BigDecimal zsMax;

    /**
     * 监测污染物实时数据标记
     * <p>HJ 212-2025: 长度由C1扩展为C3</p>
     */
    @CharArray(len = 1, groups = VersionGroup.V2017.class)
    @CharArray(len = 3, groups = VersionGroup.V2025.class)
    @JsonProperty("Flag")
    private String flag;

    /**
     * 监测仪器扩充数据标记
     */
    @CharArray(len = 4, groups = VersionGroup.V2017.class)
    @CharArray(len = 4, groups = VersionGroup.V2025.class)
    @JsonProperty("EFlag")
    private String eFlag;

    /**
     * 污染物指定时间内累计值
     */
    @JsonProperty("Cou")
    private BigDecimal cou;

    /**
     * 标准样品标称值（HJ 212-2025 新增）
     * @since 2025
     */
    @JsonProperty("StandardValue")
    private BigDecimal standardValue;

    /**
     * 标准样品实际测量示值（HJ 212-2025 新增）
     * @since 2025
     */
    @JsonProperty("SampleRd")
    private BigDecimal sampleRd;

    /**
     * 自动标样核查（校准）结果类型（HJ 212-2025 新增）
     * <p>1表示通过，0表示未通过</p>
     * @since 2025
     */
    @jakarta.validation.constraints.Min(value = 0, groups = VersionGroup.V2025.class)
    @Max(value = 1, groups = VersionGroup.V2025.class)
    @JsonProperty("ResultType")
    private Integer resultType;

    /**
     * 自动监测设备唯一标识（HJ 212-2025 新增）
     * <p>从LiveSide移至Pollution层级</p>
     * @since 2025
     */
    @CharArray(len = 24, groups = VersionGroup.V2025.class)
    @JsonProperty("SN")
    private String sn;

    /**
     * 监测参数的现场端信息（HJ 212-2025 新增）
     * <p>存储 xxxxxx-vvvvvv 格式的现场端信息，如 w01001-i12001=1</p>
     * @since 2025
     */
    private Map<String, String> fieldInfo = new LinkedHashMap<>();

    /**
     * 设备运行状态的实时采样值
     */
    @Max(value = 1, groups = VersionGroup.V2005.class)
    @JsonProperty("RS")
    private Integer rs;

    /**
     * 设备指定时间内的运行时间
     */
    @DecimalMax(value = "24", groups = VersionGroup.V2005.class)
    @JsonProperty("RT")
    private BigDecimal rt;

    /**
     * 污染物报警期间内采样值
     */
    @NumberArray(integer = 14, fraction = 2, groups = VersionGroup.V2005.class)
    @JsonProperty("Ala")
    private BigDecimal ala;

    /**
     * 污染物报警上限值
     */
    @NumberArray(integer = 14, fraction = 2, groups = VersionGroup.V2005.class)
    @JsonProperty("UpValue")
    private BigDecimal upValue;

    /**
     * 污染物报警下限值
     */
    @NumberArray(integer = 14, fraction = 2, groups = VersionGroup.V2005.class)
    @JsonProperty("LowValue")
    private BigDecimal lowValue;

    /**
     * 噪声监测日历史数据
     */
    @NumberArray(integer = 14, fraction = 2, groups = VersionGroup.V2005.class)
    @NumberArray(integer = 3, fraction = 1, groups = VersionGroup.V2017.class)
    @JsonProperty("Data")
    private String data;

    /**
     * 噪声昼间历史数据
     */
    @NumberArray(integer = 14, fraction = 2, groups = VersionGroup.V2005.class)
    @NumberArray(integer = 3, fraction = 1, groups = VersionGroup.V2017.class)
    @JsonProperty("DayData")
    private String dayData;

    /**
     * 噪声夜间历史数据
     */
    @NumberArray(integer = 14, fraction = 2, groups = VersionGroup.V2005.class)
    @NumberArray(integer = 3, fraction = 1, groups = VersionGroup.V2017.class)
    @JsonProperty("NightData")
    private String nightData;

    // ===== getter/setter =====

    public String getSampleTime() { return sampleTime; }
    public void setSampleTime(String sampleTime) { this.sampleTime = sampleTime; }

    public String getCompleteTime() { return completeTime; }
    public void setCompleteTime(String completeTime) { this.completeTime = completeTime; }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public BigDecimal getRtd() { return rtd; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public void setRtd(BigDecimal rtd) { this.rtd = rtd; this.rtdText = null; }

    /** 数值调用兼容 getRtd；工况或用电事件的字符值使用本接口读取。 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getRtdText() { return rtdText != null ? rtdText : rtd == null ? null : rtd.toPlainString(); }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public void setRtdText(String value) { setRtdValue(value); }
    @JsonProperty("Rtd")
    public String getRtdValue() { return getRtdText(); }
    @JsonProperty("Rtd")
    public void setRtdValue(String value) {
        rtdText = value;
        rtd = null;
        if (value != null) try { rtd = new BigDecimal(value); }
        catch (NumberFormatException ignored) { /* 字符型监测参数，不强制转为数值。 */ }
    }

    public Integer getSampleType() { return sampleType; }
    public void setSampleType(Integer sampleType) { this.sampleType = sampleType; }

    public BigDecimal getWcRtd() { return wcRtd; }
    public void setWcRtd(BigDecimal wcRtd) { this.wcRtd = wcRtd; }

    public BigDecimal getRawRtd() { return rawRtd; }
    public void setRawRtd(BigDecimal rawRtd) { this.rawRtd = rawRtd; }

    public BigDecimal getMin() { return min; }
    public void setMin(BigDecimal min) { this.min = min; }

    public BigDecimal getWcMin() { return wcMin; }
    public void setWcMin(BigDecimal wcMin) { this.wcMin = wcMin; }

    public BigDecimal getAvg() { return avg; }
    public void setAvg(BigDecimal avg) { this.avg = avg; }

    public BigDecimal getWcAvg() { return wcAvg; }
    public void setWcAvg(BigDecimal wcAvg) { this.wcAvg = wcAvg; }

    public BigDecimal getMax() { return max; }
    public void setMax(BigDecimal max) { this.max = max; }

    public BigDecimal getWcMax() { return wcMax; }
    public void setWcMax(BigDecimal wcMax) { this.wcMax = wcMax; }

    public BigDecimal getRawMin() { return rawMin; }
    public void setRawMin(BigDecimal rawMin) { this.rawMin = rawMin; }

    public BigDecimal getRawAvg() { return rawAvg; }
    public void setRawAvg(BigDecimal rawAvg) { this.rawAvg = rawAvg; }

    public BigDecimal getRawMax() { return rawMax; }
    public void setRawMax(BigDecimal rawMax) { this.rawMax = rawMax; }

    public BigDecimal getZsRtd() { return zsRtd; }
    public void setZsRtd(BigDecimal zsRtd) { this.zsRtd = zsRtd; }

    public BigDecimal getZsMin() { return zsMin; }
    public void setZsMin(BigDecimal zsMin) { this.zsMin = zsMin; }

    public BigDecimal getZsAvg() { return zsAvg; }
    public void setZsAvg(BigDecimal zsAvg) { this.zsAvg = zsAvg; }

    public BigDecimal getZsMax() { return zsMax; }
    public void setZsMax(BigDecimal zsMax) { this.zsMax = zsMax; }

    public String getFlag() { return flag; }
    public void setFlag(String flag) { this.flag = flag; }

    public String geteFlag() { return eFlag; }
    public void seteFlag(String eFlag) { this.eFlag = eFlag; }

    public BigDecimal getCou() { return cou; }
    public void setCou(BigDecimal cou) { this.cou = cou; }

    public BigDecimal getStandardValue() { return standardValue; }
    public void setStandardValue(BigDecimal standardValue) { this.standardValue = standardValue; }

    public BigDecimal getSampleRd() { return sampleRd; }
    public void setSampleRd(BigDecimal sampleRd) { this.sampleRd = sampleRd; }

    public Integer getResultType() { return resultType; }
    public void setResultType(Integer resultType) { this.resultType = resultType; }

    public String getSn() { return sn; }
    public void setSn(String sn) { this.sn = sn; }

    @JsonAnyGetter
    public Map<String, String> getFieldInfo() { return fieldInfo; }

    @JsonAnySetter
    public void setFieldInfo(String key, String value) { this.fieldInfo.put(key, value); }

    public void setFieldInfo(Map<String, String> fieldInfo) { this.fieldInfo = fieldInfo; }

    public Integer getRs() { return rs; }
    public void setRs(Integer rs) { this.rs = rs; }

    public BigDecimal getRt() { return rt; }
    public void setRt(BigDecimal rt) { this.rt = rt; }

    public BigDecimal getAla() { return ala; }
    public void setAla(BigDecimal ala) { this.ala = ala; }

    public BigDecimal getUpValue() { return upValue; }
    public void setUpValue(BigDecimal upValue) { this.upValue = upValue; }

    public BigDecimal getLowValue() { return lowValue; }
    public void setLowValue(BigDecimal lowValue) { this.lowValue = lowValue; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public String getDayData() { return dayData; }
    public void setDayData(String dayData) { this.dayData = dayData; }

    public String getNightData() { return nightData; }
    public void setNightData(String nightData) { this.nightData = nightData; }
}
