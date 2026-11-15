package com.github.guocay.hj212.core.validator;

import com.github.guocay.hj212.exception.ProtocolFormatException;
import com.github.guocay.hj212.core.validator.field.DateValidator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 2025 版严格语法和字段校验。只检查报文，不执行设备或业务操作。 */
public final class Protocol2025Validator {
    private Protocol2025Validator() { }

    public static void validate(char[] chars) throws ProtocolFormatException {
        String segment = new String(chars);
        int cpStart = segment.indexOf(";CP=&&");
        String head = cpStart >= 0 ? segment.substring(0, cpStart) : segment;
        Map<String,String> fields = entries(head, ";");
        String flagText = fields.get("Flag");
        // 不改变没有 Flag 的 2005 厂商报文；没有可识别版本时不推断版本。
        if (flagText == null) return;
        int flag = integer("Flag", flagText, 0, 255);
        if ((flag >>> 2) > 2) fail("Flag", "未知的标准版本号");
        if ((flag >>> 2) != 2) return;
        if (cpStart < 0 || !segment.endsWith("&&")) fail("CP", "必须以 CP=&&...&& 表示数据区");
        for (String key : new String[]{"QN","ST","CN","PW","MN"})
            if (!fields.containsKey(key)) fail(key, "缺少必要字段");
        date("QN", fields.get("QN"), "yyyyMMddHHmmssSSS");
        match("ST", fields.get("ST"), "(0[1-9]|[1-9][0-9]|[A-Z][0-9])");
        match("CN", fields.get("CN"), "[0-9]{4}");
        int cn = Integer.parseInt(fields.get("CN"));
        if (!((cn >= 1000 && cn <= 3999) || cn >= 9000)) fail("CN", "不属于命令编码范围");
        match("PW", fields.get("PW"), "[A-Za-z0-9]{1,6}");
        String mn = fields.get("MN");
        // 附录 C.2 的首次联网及其通知应答允许 MN 为空。
        if (!(mn.isEmpty() && (cn == 1001 || cn == 9013))) match("MN", mn, "[0-9A-F]{24}");
        boolean split = (flag & 2) != 0;
        if (split) {
            int total = integer("PNUM", required(fields,"PNUM"), 1, 9999);
            int number = integer("PNO", required(fields,"PNO"), 1, 9999);
            if (number > total) fail("PNO", "不能大于 PNUM");
        } else if (fields.containsKey("PNUM") || fields.containsKey("PNO")) fail("PNUM/PNO", "未设置分包位时不得携带包序号");
        if (fields.containsKey("RF")) match("RF", fields.get("RF"), "1");
        String body = segment.substring(cpStart + 6, segment.length() - 2);
        if (body.getBytes(StandardCharsets.UTF_8).length + 7 > 950) fail("CP", "含 CP=&& 和闭合符的长度不得超过 950 字节");
        Map<String,String> cp = entries(body, "[;,]");
        for (Map.Entry<String,String> entry : cp.entrySet()) validateField(entry.getKey(), entry.getValue());
        // 仅对语义明确、不依赖方向和会话状态的命令检查条件必填。
        switch(cn) {
            case 1000: required(cp,"OverTime"); required(cp,"ReCount"); break;
            case 1012: required(cp,"SystemTime"); break;
            case 1014: required(cp,"SKCreateTime"); break;
            case 1062: required(cp,"RtdInterval"); break;
            case 1064: required(cp,"MinInterval"); break;
            case 1072: required(cp,"NewPW"); break;
            case 2081: required(cp,"RestartTime"); break;
            case 9011: required(cp,"QnRtn"); break;
            case 9012: required(cp,"ExeRtn"); break;
            default: break;
        }
        if (cp.containsKey("BeginTime") && cp.containsKey("EndTime")
                && cp.get("BeginTime").compareTo(cp.get("EndTime")) > 0) fail("BeginTime", "不能晚于 EndTime");
    }

    private static Map<String,String> entries(String text, String delimiter) throws ProtocolFormatException {
        Map<String,String> result = new LinkedHashMap<>();
        if (text.isEmpty()) return result;
        List<String> parts = new ArrayList<>();
        int start = 0;
        boolean quotedLog = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '/' && i + 1 < text.length() && text.charAt(i+1) == '/'
                    && (quotedLog || (i > 0 && text.charAt(i-1) == '='
                        && text.substring(start,i-1).matches("([A-Za-z0-9]+-)?i[0-9a-z]1[0-9a-z]{3}(-Info)?")))) {
                quotedLog = !quotedLog; i++; continue;
            }
            if (!quotedLog && (c == ';' || (delimiter.equals("[;,]") && c == ','))) {
                parts.add(text.substring(start, i)); start = i + 1;
            }
        }
        if (quotedLog) fail("日志", "缺少闭合 //");
        parts.add(text.substring(start));
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq <= 0) fail("结构", "键值对必须以等号连接且不得有空项目");
            String key = part.substring(0, eq);
            if (!key.matches("[A-Za-z0-9]+(-[A-Za-z0-9]+)*")) fail("字段名", "存在非法字符");
            if (result.containsKey(key)) fail(key, "不允许重复字段");
            result.put(key, part.substring(eq + 1));
        }
        return result;
    }

    private static void validateField(String key, String value) throws ProtocolFormatException {
        if (key.matches("([A-Za-z0-9]+-)?i[0-9a-z]1[0-9a-z]{3}(-Info)?")) {
            if (value.length() < 4 || !value.startsWith("//") || !value.endsWith("//")) fail(key,"日志必须置于一对 // 内");
            if (value.getBytes(StandardCharsets.UTF_8).length >= 890) fail(key,"日志须小于 890 字节");
            return;
        }
        if (Arrays.asList("SystemTime","RestartTime","BeginTime","EndTime","DataTime").contains(key)
                || key.endsWith("-SampleTime") || key.endsWith("-CompleteTime")) { date(key,value,"yyyyMMddHHmmss"); return; }
        if (key.equals("SKCreateTime")) { date(key,value,"yyyyMMddHHmmssSSS"); return; }
        if (key.equals("RtdInterval")) { integer(key,value,1,3600); return; }
        if (key.endsWith("-SampleType")) { integer(key,value,0,4); return; }
        if (key.equals("MinInterval")) {
            int n = integer(key,value,1,30);
            if (!Arrays.asList(1,2,3,4,5,6,10,12,15,20,30).contains(n)) fail(key,"不属于分钟间隔取值集合");
            return;
        }
        if (Arrays.asList("OverTime","ReCount","VaseNo").contains(key)) { integer(key,value,0,99); return; }
        if (key.equals("Stime")) { integer(key,value,0,120); return; }
        if (Arrays.asList("CalibrationType","SampleType").contains(key)) { integer(key,value,0,key.equals("SampleType")?4:1); return; }
        if (key.equals("QnRtn") || key.equals("ExeRtn")) {
            int n=integer(key,value,1,100);
            if (n!=100 && n>(key.equals("QnRtn")?9:6)) fail(key,"不属于回应代码取值集合");
            return;
        }
        if (key.equals("NewPW") || key.equals("NewSK")) { match(key,value,"[A-Za-z0-9]{1,"+(key.equals("NewSK")?16:6)+"}"); return; }
        if (key.equals("PolId") || key.equals("InfoId")) { match(key,value,"[a-z0-9]{1,6}"); return; }
        if (key.endsWith("-Flag")) { match(key,value,"[A-Za-z0-9]{1,3}"); return; }
        if (key.endsWith("-EFlag")) { match(key,value,"[A-Za-z0-9]{1,4}"); return; }
        if (key.endsWith("-SN")) { match(key,value,"[A-Za-z0-9]{1,24}"); return; }
        if (key.startsWith("SB") && (key.endsWith("-RS") || key.endsWith("-RT"))) {
            match(key.substring(0,key.length()-3),key.substring(2,key.length()-3),"[0-9]{1,3}");
            if (key.endsWith("-RS")) integer(key,value,0,1);
            else decimal(key,value,2,2,BigDecimal.ZERO,new BigDecimal("24"));
            return;
        }
        if (key.endsWith("-ResultType")) { integer(key,value,0,1); return; }
        if (key.endsWith("-StandardValue") || key.endsWith("-SampleRd")) { decimal(key,value,6,2,null,null); return; }
        if (key.matches(".*-(DayData|NightData|Data)")) { decimal(key,value,3,1,null,null); return; }
        if (key.matches(".*-(Rtd|Min|Avg|Max|Cou|ZsRtd|ZsMin|ZsAvg|ZsMax|WcRtd|WcMin|WcAvg|WcMax|RawRtd|RawMin|RawAvg|RawMax)")) {
            String code=key.substring(0,key.indexOf('-'));
            // 附录 B.12、B.14 中字符型用电事件和关键生产工况。
            if (key.endsWith("-Rtd") && code.matches("(d[123]09[0-9]{2}|p(101|201|301|302|401|991|111)[0-9]{2})")) {
                int max=code.startsWith("p101")||code.startsWith("p201")||code.startsWith("p111")?6:code.startsWith("p30")?3:4;
                match(key,value,"[A-Za-z0-9]{1,"+max+"}");
            } else decimal(key,value,Integer.MAX_VALUE,Integer.MAX_VALUE,null,null);
        }
        // 未知字段和扩充编码按原值保留，避免将可扩充表误作封闭白名单。
    }

    private static int integer(String key,String value,int min,int max) throws ProtocolFormatException {
        int width = String.valueOf(max).length();
        if (key.equals("Stime") || key.equals("RtdInterval")) width = 4;
        if (value == null || !value.matches("[0-9]{1,"+width+"}")) fail(key,"整数格式或原始位数无效");
        try {
            int n=Integer.parseInt(value);
            if(n<min || n>max) fail(key,"超出允许范围");
            return n;
        } catch(NumberFormatException e) { fail(key,"整数超出范围"); return -1; }
    }
    private static void decimal(String key,String value,int integer,int fraction,BigDecimal min,BigDecimal max) throws ProtocolFormatException {
        match(key,value,"[+-]?[0-9]+(\\.[0-9]+)?");
        BigDecimal n=new BigDecimal(value);
        String unsigned = value.replaceFirst("^[+-]", "");
        int dot = unsigned.indexOf('.');
        int integerWidth = dot < 0 ? unsigned.length() : dot;
        if (integerWidth>integer || n.scale()>fraction
                || (min!=null && n.compareTo(min)<0) || (max!=null && n.compareTo(max)>0)) fail(key,"数值精度或范围无效");
    }
    private static void date(String key,String value,String format) throws ProtocolFormatException {
        if (value==null || !value.matches("[0-9]{"+format.length()+"}") || !DateValidator.isValidFormat(format,value)) fail(key,"日期格式无效");
    }
    private static String required(Map<String,String> values,String key) throws ProtocolFormatException {
        String value=values.get(key);
        if (value==null || value.isEmpty()) fail(key,"缺少必要字段");
        return value;
    }
    private static void match(String key,String value,String regex) throws ProtocolFormatException {
        if (value==null || !value.matches(regex)) fail(key,"字符集、格式或长度无效");
    }
    private static void fail(String key,String reason) throws ProtocolFormatException {
        // 不在异常中输出字段值，尤其是 PW 与 NewSK。
        throw new ProtocolFormatException("HJ212-2025 字段 "+key+": "+reason);
    }
}
