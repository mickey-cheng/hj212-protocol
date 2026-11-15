package com.github.guocay.hj212;

import com.fasterxml.jackson.databind.*;
import com.github.guocay.hj212.core.*;
import com.github.guocay.hj212.core.crypto.ProtocolCrypto;
import com.github.guocay.hj212.model.*;
import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

/** 覆盖附录 C.1～C.63，每一命令/应答行均跨接口验证，不依赖生产代码生成输入 CRC。 */
public class AppendixCRegressionTest {
    private ProtocolMapper mapper(){return new ProtocolMapper().enableStrict2025();}
    private String packet(String s){
        byte[] bytes=s.getBytes(StandardCharsets.UTF_8);int crc=65535;
        for(byte b:bytes){crc=(crc>>>8)^(b&255);for(int i=0;i<8;i++)crc=(crc&1)==0?crc>>>1:(crc>>>1)^0xA001;}
        return String.format(Locale.ROOT,"##%04d%s%04X\r\n",bytes.length,s,crc);
    }
    private List<JsonNode> samples() throws Exception {
        try(InputStream in=getClass().getResourceAsStream("/hj212-2025/appendix-c.json")) {
            List<JsonNode> rows=new ArrayList<>();new ObjectMapper().readTree(in).get("samples").forEach(rows::add);return rows;
        }
    }
    @Test public void allTablesAndRowsArePresent() throws Exception {
        List<JsonNode> rows=samples();assertEquals(188,rows.size());
        Set<Integer> tables=rows.stream().map(x->x.get("table").asInt()).collect(Collectors.toSet());
        assertEquals(IntStream.rangeClosed(1,63).boxed().collect(Collectors.toSet()),tables);
        assertEquals(23,rows.stream().filter(x->!x.get("source").equals(x.get("normalized"))).count());
    }
    @TestFactory public Stream<DynamicTest> allCommandRowsAndInterfaces() throws Exception {
        return samples().stream().flatMap(row->Stream.of("Data","Map","DeepMap","Pack","Encrypted").map(type->DynamicTest.dynamicTest(
            row.get("id").asText()+" / "+type,()->check(row.get("normalized").asText(),type))));
    }
    @TestFactory public Stream<DynamicTest> printedEllipsesAreNotAcceptedAsWireData() throws Exception {
        return samples().stream().filter(row->!row.get("source").equals(row.get("normalized")))
            .flatMap(row->Stream.of("Data","Map","DeepMap","Pack").map(type->DynamicTest.dynamicTest(
                row.get("id").asText()+" / 原文省略号 / "+type,()->{
                    String p=packet(row.get("source").asText());ProtocolMapper m=mapper();
                    assertThrows(com.github.guocay.hj212.exception.ProtocolFormatException.class,()->{
                        switch(type){case "Data":m.readData(p);break;case "Map":m.readMap(p);break;case "DeepMap":m.readDeepMap(p);break;default:m.readValue(p,Pack.class);}
                    });
                })));
    }
    // 附录样例本身的 CP 日志不包含 ASCII 分隔符；此处独立解析固定样例以核对每一个原始字段。
    private Map<String,Object> expectedFields(String segment) {
        int start=segment.indexOf(";CP=&&");Map<String,Object> result=new LinkedHashMap<>();
        for(String part:segment.substring(0,start).split(";")){int eq=part.indexOf('=');result.put(part.substring(0,eq),part.substring(eq+1));}
        String body=segment.substring(start+6,segment.length()-2);Map<String,Object> cp=new LinkedHashMap<>();
        if(!body.isEmpty())for(String part:body.split("[;,]")){int eq=part.indexOf('=');cp.put(part.substring(0,eq),part.substring(eq+1));}
        result.put("CP",cp);return result;
    }
    private Map<String,Object> deep(String s)throws Exception{return mapper().readDeepMap(s);}
    private void equivalent(String expected,String actual) throws Exception {
        Map<String,Object> e=deep(expected),a=deep(actual);
        assertEquals(e,a,"字段和值必须在往返后完整保留");
    }
    private void check(String segment,String type) throws Exception {
        String p=packet(segment);byte[] b=p.getBytes(StandardCharsets.UTF_8);ProtocolMapper m=mapper();
        assertEquals(expectedFields(segment),m.readDeepMap(p),"每个源字段都必须被解析保留");
        switch(type) {
            case "Data":
                Data data=m.readData(p);assertEquals(data.getCn(),m.readData(b).getCn());
                assertEquals(data.getMn(),m.readData(new StringReader(p)).getMn());assertEquals(data.getQn(),m.readData(new ByteArrayInputStream(b)).getQn());
                equivalent(p,m.writeDataAsString(data));equivalent(p,new String(m.writeDataAsBytes(data),StandardCharsets.UTF_8));
                ByteArrayOutputStream out=new ByteArrayOutputStream();m.writeValueAsStream(data,Data.class,out);equivalent(p,new String(out.toByteArray(),StandardCharsets.UTF_8));
                break;
            case "Map":
                Map<String,String> map=m.readMap(p);assertEquals(map,m.readMap(b));assertEquals(map,m.readMap(new StringReader(p)));assertEquals(map,m.readMap(new ByteArrayInputStream(b)));
                equivalent(p,m.writeMapAsString(map));break;
            case "DeepMap":
                Map<String,Object> deep=m.readDeepMap(p);assertEquals(deep,m.readDeepMap(b));assertEquals(deep,m.readDeepMap(new StringReader(p)));assertEquals(deep,m.readDeepMap(new ByteArrayInputStream(b)));
                equivalent(p,m.writeMapAsString(deep));break;
            case "Pack":
                Pack pack=m.readValue(p,Pack.class);assertArrayEquals(segment.toCharArray(),pack.getSegment());
                assertArrayEquals(pack.getSegment(),m.readValue(b,Pack.class).getSegment());assertArrayEquals(pack.getSegment(),m.readValue(new StringReader(p),Pack.class).getSegment());assertArrayEquals(pack.getSegment(),m.readValue(new ByteArrayInputStream(b),Pack.class).getSegment());
                StringWriter writer=new StringWriter();m.writeValueAsWriter(pack,Pack.class,writer);assertEquals(p,writer.toString());break;
            case "Encrypted":
                byte[] key=new byte[16];Arrays.fill(key,(byte)'0');
                byte[] encrypted=ProtocolCrypto.encrypt(b,key);assertArrayEquals(b,ProtocolCrypto.decrypt(encrypted,key));
                equivalent(p,m.writeDataAsString(m.readEncryptedData(encrypted,key)));
                equivalent(p,m.writeMapAsString(m.readEncryptedDeepMap(new ByteArrayInputStream(encrypted),key)));break;
            default:fail(type);
        }
    }
}
