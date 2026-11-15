package com.github.guocay.hj212;

import com.github.guocay.hj212.core.*;
import com.github.guocay.hj212.core.feature.*;
import com.github.guocay.hj212.exception.ProtocolFormatException;
import com.github.guocay.hj212.model.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** 标准附录及边界报文回归测试，不依赖真实数采仪。 */
public class ProtocolRegressionTest {
    private ProtocolMapper mapper() {
        return new ProtocolMapper().enableDefaultParserFeatures().enableDefaultVerifyFeatures()
            .enable(VerifyFeature.DATA_CRC).enable(ParserFeature.FOOTER_CONSTANT);
    }
    private String segment(int flag, String cp) {
        return "QN=20240601085857223;ST=32;CN=2011;PW=123456;MN=010000A8900016F000169DC0;Flag="+flag+";CP=&&"+cp+"&&";
    }
    // 与被测实现分离，直接按附录 A 对字节计算校验码。
    private String packet(String segment) {
        byte[] bytes=segment.getBytes(StandardCharsets.UTF_8);
        int crc=0xffff;
        for(byte b:bytes) {
            crc=(crc>>>8)^(b&255);
            for(int j=0;j<8;j++) crc=(crc&1)!=0 ? (crc>>>1)^0xa001 : crc>>>1;
        }
        return String.format(Locale.ROOT,"##%04d%s%04X\r\n",bytes.length,segment,crc);
    }
    @Test public void appendixACrcVector() throws Exception {
        String s="QN=20240601085857223;ST=32;CN=1011;PW=123456;MN=010000A8900016F000169DC0;Flag=9;CP=&&&&";
        assertEquals("##0087"+s+"2200\r\n",packet(s));
        assertEquals("1011",mapper().readData(packet(s)).getCn());
    }
    @Test public void allVersionsAnd2025FieldsRoundTrip() throws Exception {
        for(int flag:new int[]{0,4,8}) {
            String s=segment(flag,"DataTime=20240601085857;a21026-Rtd=12.34,a21026-Flag=N");
            if(flag==0) s=s.replace("010000A8900016F000169DC0","12345678901234");
            Data data=mapper().readData(packet(s));
            Data result=mapper().readData(mapper().writeDataAsString(data));
            assertEquals(data.getMn(),result.getMn());
            assertEquals("12.34",result.getCp().getPollution().get("a21026").getRtd().toPlainString());
        }
        String s=segment(9,"DataTime=20240601085857;RtdInterval=1;a21026-WcRtd=1.25,a21026-RawAvg=2.50,a21026-Flag=NA1;a21026-i22004=123").replace(";CP=",";RF=1;CP=");
        Data data=mapper().readData(packet(s));
        Data result=mapper().readData(mapper().writeDataAsString(data));
        assertEquals(1,result.getRf());
        assertEquals(1,result.getCp().getRtdInterval());
        assertEquals("NA1",result.getCp().getPollution().get("a21026").getFlag());
        assertEquals("123",result.getCp().getPollution().get("a21026").getFieldInfo().get("i22004"));
    }
    @Test public void versionSpecificValidationAndNestedValidation() {
        assertThrows(ProtocolFormatException.class,()->mapper().readData(packet(segment(4,"RtdInterval=1"))));
        assertThrows(ProtocolFormatException.class,()->mapper().readData(packet(segment(8,"CalibrationType=-1"))));
        assertThrows(ProtocolFormatException.class,()->mapper().readData(packet(segment(8,"a21026-ResultType=2"))));
        assertThrows(ProtocolFormatException.class,()->mapper().readDeepMap(packet(segment(8,"SB001-RT=1;SB002-RT=-1"))));
        assertThrows(ProtocolFormatException.class,()->mapper().readData(packet(segment(8,"DataTime=20240230000000"))));
    }
    @Test public void longKeysAndUtf8Values() throws Exception {
        String value=String.join("",Collections.nCopies(30,"测试"));
        Data data=mapper().readData(packet(segment(8,"a21026-CompleteTime=20240601085857;i12001-Info="+value)));
        assertEquals("20240601085857",data.getCp().getPollution().get("a21026").getCompleteTime());
        String output=mapper().writeDataAsString(data);
        assertEquals(value,mapper().readData(output.getBytes(StandardCharsets.UTF_8)).getCp().getLiveSide().get("i12001").getInfo());
    }
    @Test public void crcIsAlwaysFourHexDigits() throws Exception {
        for(int i=0;i<256;i++) {
            char[] chars={(char)i};
            StringWriter out=new StringWriter();
            new ProtocolGenerator(out).writeDataAndLenAndCrc(chars);
            assertEquals(4+new String(chars).length()+4,out.toString().length());
        }
    }
    @Test public void shortReaderReadsAreAccepted() throws Exception {
        String s=packet(segment(8,"DataTime=20240601085857"));
        Reader reader=new FilterReader(new StringReader(s)) {
            @Override public int read(char[] b,int off,int len) throws IOException {
                return super.read(b,off,Math.min(1,len));
            }
        };
        assertEquals("32",mapper().readData(reader).getSt());
    }
    @Test public void badCrcHeaderFooterAndLengthAreRejected() {
        String p=packet(segment(8,"DataTime=20240601085857"));
        assertThrows(ProtocolFormatException.class,()->mapper().readData(p.replace("ST=32","ST=31")));
        assertThrows(ProtocolFormatException.class,()->mapper().readData("!!"+p.substring(2)));
        assertThrows(ProtocolFormatException.class,()->mapper().readData(p.substring(0,p.length()-2)));
        assertThrows(ProtocolFormatException.class,()->mapper().readData("##9999"+p.substring(6)));
    }
    @Test public void splitPacketNumbersSurviveRoundTrip() throws Exception {
        String s=segment(11,"DataTime=20240601085857").replace(";CP=",";PNUM=2;PNO=1;CP=");
        Data data=mapper().readData(packet(s));
        Data result=mapper().readData(mapper().writeDataAsString(data));
        assertEquals(2,result.getpNum()); assertEquals(1,result.getpNo());
        assertThrows(ProtocolFormatException.class,()->mapper().readDeepMap(packet(segment(11,""))));
    }
    @Test public void mapperInstancesKeepSeparateVerificationSettings() throws Exception {
        ProtocolMapper strict=mapper();
        ProtocolMapper loose=mapper().disable(VerifyFeature.DATA_CRC);
        String broken=packet(segment(8,"")).replace("ST=32","ST=31");
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<?> a=pool.submit(()->{for(int i=0;i<100;i++) assertThrows(ProtocolFormatException.class,()->strict.readData(broken));});
            Future<?> b=pool.submit(()->{for(int i=0;i<100;i++) {try {assertEquals("31",loose.readData(broken).getSt());}catch(Exception e){throw new RuntimeException(e);}}});
            a.get(15,TimeUnit.SECONDS); b.get(15,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
    }
}
