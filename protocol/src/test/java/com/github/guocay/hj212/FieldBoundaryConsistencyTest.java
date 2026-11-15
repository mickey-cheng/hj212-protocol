package com.github.guocay.hj212;

import com.fasterxml.jackson.databind.*;
import com.github.guocay.hj212.core.*;
import com.github.guocay.hj212.model.*;
import com.github.guocay.hj212.exception.ProtocolFormatException;
import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

/** 原始字面宽度在转换为 Integer/BigDecimal 之前检查。 */
public class FieldBoundaryConsistencyTest {
    private ProtocolMapper mapper(){return new ProtocolMapper().enableStrict2025();}
    private List<JsonNode> rules() throws Exception {
        try(InputStream in=getClass().getResourceAsStream("/hj212-2025/field-boundaries.json")) {
            List<JsonNode> rows=new ArrayList<>();new ObjectMapper().readTree(in).forEach(rows::add);return rows;
        }
    }
    private String segment(String field,String value,boolean header) {
        Map<String,String> h=new LinkedHashMap<>();
        h.put("QN","20240601085857223");h.put("ST","32");h.put("CN","2201");h.put("PW","123456");h.put("MN","010000A8900016F000169DC0");h.put("Flag","8");
        if (header) h.put(field,value);
        if (field.equals("PNUM")||field.equals("PNO")) {h.put("Flag","11");if(!field.equals("PNUM"))h.put("PNUM","2");if(!field.equals("PNO"))h.put("PNO","1");}
        if (field.equals("Flag") && (value.equals("10")||value.equals("11"))) {h.put("PNUM","2");h.put("PNO","1");}
        return h.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(Collectors.joining(";"))+";CP=&&"+(header?"":field+"="+value)+"&&";
    }
    private String packet(String s) {
        byte[] b=s.getBytes(StandardCharsets.UTF_8);int crc=65535;
        for(byte v:b){crc=(crc>>>8)^(v&255);for(int i=0;i<8;i++)crc=(crc&1)==0?crc>>>1:(crc>>>1)^0xA001;}
        return String.format(Locale.ROOT,"##%04d%s%04X\r\n",b.length,s,crc);
    }
    @TestFactory public Stream<DynamicTest> widthRangeAndCharsetAcrossInterfaces() throws Exception {
        return rules().stream().flatMap(rule->Stream.of("valid","invalid").flatMap(kind->{
            List<JsonNode> values=new ArrayList<>();rule.get(kind).forEach(values::add);
            return values.stream().flatMap(value->Stream.of("Data","Map","DeepMap","Pack","WriteMap","WriteDeepMap","WritePack").map(type->DynamicTest.dynamicTest(
                rule.get("field").asText()+"="+value.asText()+" / "+kind+" / "+type,
                ()->{String s=segment(rule.get("field").asText(),value.asText(),rule.get("scope").asText().equals("header"));
                    if(kind.equals("valid")) assertDoesNotThrow(()->apply(s,type));
                    else assertThrows(ProtocolFormatException.class,()->apply(s,type));
                })));
        }));
    }
    private void apply(String s,String type) throws Exception {
        ProtocolMapper m=mapper();String p=packet(s);
        switch(type) {
            case "Data": Data d=m.readData(p);m.writeDataAsString(d);break;
            case "Map":m.readMap(p);break;
            case "DeepMap":m.readDeepMap(p);break;
            case "Pack":m.readValue(p,Pack.class);break;
            case "WritePack":Pack pack=new Pack();pack.setData(s.toCharArray());m.writeValueAsWriter(pack,Pack.class,new StringWriter());break;
            case "WriteMap":case "WriteDeepMap":
                int start=s.indexOf(";CP=&&");Map<String,Object> values=new LinkedHashMap<>();
                for(String pair:s.substring(0,start).split(";")){int eq=pair.indexOf('=');values.put(pair.substring(0,eq),pair.substring(eq+1));}
                String cp=s.substring(start+6,s.length()-2);
                if(type.equals("WriteMap"))values.put("CP",cp);
                else {Map<String,Object> body=new LinkedHashMap<>();if(!cp.isEmpty()){int eq=cp.indexOf('=');body.put(cp.substring(0,eq),cp.substring(eq+1));}values.put("CP",body);}
                m.writeMapAsString(values);break;
            default:fail(type);
        }
    }
    @Test public void logsKeepDelimitersAndUtf8AcrossAllInterfaces() throws Exception {
        String log="//维护记录;范围=1,2&&中文 与 空格//";
        String s=segment("i11001-Info",log,false),p=packet(s);ProtocolMapper m=mapper();
        assertEquals(log,((Map<?,?>)m.readDeepMap(p).get("CP")).get("i11001-Info"));
        assertEquals(log,m.readData(p).getCp().getLiveSide().get("i11001").getInfo());
        assertEquals(m.readDeepMap(p),m.readDeepMap(m.writeDataAsString(m.readData(p))));
        assertEquals(m.readDeepMap(p),m.readDeepMap(m.writeMapAsString(m.readMap(p))));
        assertEquals(m.readDeepMap(p),m.readDeepMap(m.writeMapAsString(m.readDeepMap(p))));
        assertEquals(log,((Map<?,?>)m.readDeepMap(p.getBytes(StandardCharsets.UTF_8)).get("CP")).get("i11001-Info"));
        String extension=packet(segment("VendorValue","//server/share",false));
        assertEquals("//server/share",m.readData(extension).getCp().getExtensions().get("VendorValue"));
        assertEquals(m.readMap(extension),m.readMap(m.writeMapAsString(m.readMap(extension))));
    }
    @Test public void logQuotationAndByteLimitAreEnforced() throws Exception {
        for(String value:new String[]{"日志无引号","//未闭合","//"+String.join("",Collections.nCopies(886,"x"))+"//"})
            for(String type:new String[]{"Data","Map","DeepMap","Pack","WriteMap","WriteDeepMap","WritePack"})
                assertThrows(Exception.class,()->apply(segment("i11001-Info",value,false),type));
        for(String type:new String[]{"Data","Map","DeepMap","Pack","WriteMap","WriteDeepMap","WritePack"})
            apply(segment("i11001-Info","//"+String.join("",Collections.nCopies(885,"x"))+"//",false),type);
    }
    @Test public void missingRsStaysAbsentAndLegacyNumericAccessorRemains() throws Exception {
        ProtocolMapper m=mapper();Data d=m.readData(packet(segment("SB1-RT","1.1",false)));
        assertFalse(d.getCp().getDevice().get("1").hasRs());assertEquals(0,d.getCp().getDevice().get("1").getRs());
        assertFalse(((Map<?,?>)m.readDeepMap(m.writeDataAsString(d)).get("CP")).containsKey("SB1-RS"));
        d.getCp().getDevice().get("1").setRs(1);assertTrue(d.getCp().getDevice().get("1").hasRs());
        assertEquals("1",((Map<?,?>)m.readDeepMap(m.writeDataAsString(d)).get("CP")).get("SB1-RS"));
    }
    @Test public void headerLengthAndCrcUseOnlyTheirDigitAlphabets() throws Exception {
        String p=packet(segment("SampleType","1",false));
        String plusLength="##+"+p.substring(3);
        String plusCrc=p.substring(0,p.length()-6)+"+"+p.substring(p.length()-5);
        for(String bad:new String[]{plusLength,plusCrc}) {
            ProtocolMapper m=mapper();assertThrows(IOException.class,()->m.readData(bad));assertThrows(Exception.class,()->m.readMap(bad));assertThrows(Exception.class,()->m.readDeepMap(bad));assertThrows(Exception.class,()->m.readValue(bad,Pack.class));
        }
    }
}
