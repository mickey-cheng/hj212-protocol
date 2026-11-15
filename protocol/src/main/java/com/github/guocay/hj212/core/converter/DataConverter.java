package com.github.guocay.hj212.core.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guocay.hj212.declaration.Converter;
import com.github.guocay.hj212.model.CpData;
import com.github.guocay.hj212.model.Data;
import com.github.guocay.hj212.model.DataFlag;
import com.github.guocay.hj212.model.Device;
import com.github.guocay.hj212.model.LiveSide;
import com.github.guocay.hj212.model.Pollution;
import com.github.guocay.hj212.model.verify.DataElement;
import com.github.guocay.hj212.model.verify.ProtocolMap;
import com.github.guocay.hj212.segment.config.Configurator;
import com.github.guocay.hj212.segment.config.Configured;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 转换器
 * 将 {@link ProtocolMap} 转为 {@link Data}
 * <p>支持 HJ/T 212-2005 / HJ 212-2017 / HJ 212-2025</p>
 * @author aCay
 */
public class DataConverter
        implements Converter<ProtocolMap<String,Object>,Data>,
        Configured<DataConverter> {

    private static String SPLIT = "-";
    private static String SB = "SB";
    /**
     * 设备运行状态：SByyy-RS / SByyy-RT（HJ 212-2025: yyy取值[0, 999]）
     */
    private static String REGEX_DEVICE = "SB.*-RS$|SB.*-RT$";
    /**
     * 现场端信息：vvvvvv-Info / vvvvvv-SN
     * <p>HJ 212-2025: vvvvvv为6位编码，第1位为i/d/e/g/p</p>
     */
    private static String REGEX_LIVE_SIDE = "^[idegp][0-9a-z]{5}-Info$|^[idegp][0-9a-z]{5}-SN$";
    /**
     * 污染因子：任何带'-'分隔符的键
     */
    private static String REGEX_POLLUTION = ".*-.*";
    /**
     * 现场端信息编码模式（HJ 212-2025新增）
     * <p>匹配 idegp 开头的6位编码，如 i12001, d10101, e10101, g10101, p10101</p>
     */
    private static Pattern FIELD_INFO_CODE = Pattern.compile("^[idegp][0-9a-z]{5}$");

    private ObjectMapper objectMapper;

    private Predicate<String> predicateDevice;
    private Predicate<String> predicateLiveSide;
    private Predicate<String> predicatePollution;


    public DataConverter(){
        predicateDevice = Pattern.compile(REGEX_DEVICE).asPredicate();
        predicateLiveSide = Pattern.compile(REGEX_LIVE_SIDE).asPredicate();
        predicatePollution = Pattern.compile(REGEX_POLLUTION).asPredicate();
    }

    /**
     * 根据Key中的第一个分隔符分组，分隔符后的所有内容都作为字段键
     * <p>支持多级嵌套键，如 a21026-i22004-Info=1 → {a21026: {i22004-Info: 1}}</p>
     * @param map Map
     * @param split 分隔符
     * @return 分组Map
     */
    private Map<String,Map<String,String>> groupBySplitKey(Map<String,String> map, String split){
        Map<String,Map<String,String>> result = map.entrySet().stream()
                .collect(Collectors.groupingBy(
                        t -> {
                            String key = t.getKey();
                            int idx = key.indexOf(split);
                            return idx < 0 ? key : key.substring(0, idx);
                        },
                        Collectors.toMap(
                                kv -> {
                                    String key = kv.getKey();
                                    int idx = key.indexOf(split);
                                    return idx < 0 ? key : key.substring(idx + 1);
                                },
                                Map.Entry::getValue
                        )
                ));
        return result;
    }

    /**
     * 转换 现场端
     * @param map
     * @return
     */
    private Map<String, LiveSide> convertLiveSide(Map<String,String> map){
        return groupBySplitKey(map,SPLIT)
                .entrySet()
                .stream()
                .map(kv -> {
                    LiveSide liveSide = objectMapper.convertValue(kv.getValue(),LiveSide.class);
                    return new AbstractMap.SimpleEntry<>(kv.getKey(),liveSide);
                })
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
    }

    /**
     * 转换 设备
     * @param map
     * @return
     */
    private Map<String, Device> convertDevice(Map<String,String> map){
        return groupBySplitKey(map,SPLIT)
                .entrySet()
                .stream()
                .map(kv -> {
                    Device device = objectMapper.convertValue(kv.getValue(),Device.class);
                    return new AbstractMap.SimpleEntry<>(kv.getKey().replace(SB,""),device);
                })
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
    }

    /**
     * 转换 污染因子
     * <p>支持 HJ 212-2025 新增的现场端信息编码 (xxxxxx-vvvvvv)</p>
     * @param map
     * @return
     */
    private Map<String, Pollution> convertPollution(Map<String,String> map){
        Map<String, Map<String, String>> grouped = groupBySplitKey(map, SPLIT);
        Map<String, Pollution> result = new HashMap<>();

        for (Map.Entry<String, Map<String, String>> entry : grouped.entrySet()) {
            String code = entry.getKey();
            Map<String, String> fields = new HashMap<>(entry.getValue());
            Map<String, String> fieldInfo = new HashMap<>();

            // 提取 HJ 212-2025 污染因子关联的现场端信息 (xxxxxx-vvvvvv)
            for (String subKey : new ArrayList<>(fields.keySet())) {
                if (FIELD_INFO_CODE.matcher(subKey).matches()) {
                    fieldInfo.put(subKey, fields.remove(subKey));
                }
            }

            Pollution pollution = objectMapper.convertValue(fields, Pollution.class);
            if (!fieldInfo.isEmpty()) {
                // 合并 @JsonAnySetter 自动捕获的多级 fieldInfo（如 i22004-Info）
                if (pollution.getFieldInfo() != null && !pollution.getFieldInfo().isEmpty()) {
                    fieldInfo.putAll(pollution.getFieldInfo());
                }
                pollution.setFieldInfo(fieldInfo);
            }
            result.put(code, pollution);
        }
        return result;
    }


    /**
     * 过滤Map中的Entry
     * @param map
     * @param predicate 过滤逻辑
     * @return
     */
    private Map<String,String> filter(Map<String,String> map, Predicate<String> predicate){
        return map.entrySet()
                .stream()
                .filter(kv -> predicate.test(kv.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }


    /**
     * 转换 数据区
     * @param map
     * @return
     */
    private Map<String,Object> convertDataLevel(Map<String,String> map){
        Map<String,Object> cp = new HashMap<>();
        //设备运行状态 SBxxx-RS/RT
        Map<String,String> d = filter(map,predicateDevice);
        if(!d.isEmpty()){
            Map<String,Device> deviceMap = convertDevice(d);
            cp.put(CpData.DEVICE,deviceMap);
            d.keySet().forEach(map::remove);
        }

        //现场端信息 vvvvvv-Info/SN（vvvvvv以i开头）
        Map<String,String> ls = filter(map,predicateLiveSide);
        if(!ls.isEmpty()){
            Map<String,LiveSide> liveSideMap = convertLiveSide(ls);
            cp.put(CpData.LIVE_SIDE,liveSideMap);
            ls.keySet().forEach(map::remove);
        }

        //数据区Flag标记
        String flag = map.get(DataElement.Flag.name());
        if(flag != null){
            List<DataFlag> dataFlags = convertDataFlag(flag);
            map.remove(DataElement.Flag.name());
            cp.put(Data.FLAG,dataFlags);
        }

        //污染因子及现场端信息 (xxxxxx-Rtd/x-vvvvvv 等)
        Map<String,String> p = filter(map,predicatePollution);
        if(!p.isEmpty()){
            Map<String,Pollution> pollutionMap = convertPollution(p);
            cp.put(CpData.POLLUTION,pollutionMap);
            p.keySet().forEach(map::remove);
        }

        //保留通用字段
        cp.putAll(map);
        return cp;
    }

    /**
     * 转换 数据段标记
     * @param flag
     * @return
     */
    private List<DataFlag> convertDataFlag(String flag){
        if(flag != null){
            int i = Integer.parseInt(flag.trim());
            if (i < 0 || i > 255) throw new IllegalArgumentException("Flag 应在 0 到 255 之间");
            DataFlag.versionGroup(i);

            return Stream.of(DataFlag.values())
                    .filter(sf -> sf.isMarked(i))
                    .collect(Collectors.toList());
        }
        return null;
    }

    /**
     * 转换 数据段
     * @param map
     * @return
     */
    @SuppressWarnings("unchecked")
    private Data convertDataLevel(ProtocolMap<String,Object> map){
        String flag = (String) map.get(DataElement.Flag.name());
        if(flag != null){
            List<DataFlag> flagList = convertDataFlag(flag);
            //replace
            map.put(Data.FLAG,flagList);
        }

        Map<String,String> cp = (Map<String, String>) map.get(DataElement.CP.name());
        if(cp != null){
            Map<String,Object> cpMap = convertDataLevel(cp);
            //replace
            map.put(Data.CP,cpMap);
        }

        return objectMapper.convertValue(map,Data.class);
    }

    @Override
    public Data convert(ProtocolMap<String,Object> map) {
        return convertDataLevel(map);
    }

    @Override
    public void configured(Configurator<DataConverter> by) {
        by.config(this);
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
}
