package com.github.guocay.hj212.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** 按通信包数据段顺序输出，CP 固定置于最后。 */
public final class ProtocolMaps {
    private ProtocolMaps() { }
    /** 内部序列化适配：readMap 的 CP 文本仍应以 && 包围输出。 */
    public static final class RawCp {
        private final String value;
        public RawCp(String value) { this.value = value; }
        public String value() { return value; }
    }

    public static Map<String,Object> ordered(Map<String,Object> source) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (String name : new String[]{"QN","ST","CN","PW","MN","Flag","PNUM","PNO","RF"})
            if (source.containsKey(name)) result.put(name,source.get(name));
        for (Map.Entry<String,Object> e : source.entrySet())
            if (!e.getKey().equals("CP") && !result.containsKey(e.getKey())) result.put(e.getKey(),e.getValue());
        if (source.containsKey("CP")) {
            Object cp = source.get("CP");
            result.put("CP", cp instanceof String ? new RawCp((String)cp) : cp);
        }
        return result;
    }
}
