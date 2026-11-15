package com.github.guocay.hj212;

import com.github.guocay.hj212.core.*;
import com.github.guocay.hj212.core.crypto.ProtocolCrypto;
import com.github.guocay.hj212.core.feature.*;
import com.github.guocay.hj212.exception.ProtocolFormatException;
import com.github.guocay.hj212.model.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 仅验证编解码能力，不依赖接收服务、数据库或数采仪。 */
public class Protocol2025Test {
    private ProtocolMapper mapper() {return new ProtocolMapper().enableDefaultParserFeatures().enableDefaultVerifyFeatures().enable(VerifyFeature.DATA_CRC);}
    private ProtocolMapper strict() {return new ProtocolMapper().enableStrict2025();}
    private byte[] key() {byte[] k=new byte[16];Arrays.fill(k,(byte)0x30);return k;}
    private Map<String,Object> map(String cn, Map<String,Object> body) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("QN","20240601085857223");result.put("ST","32");result.put("CN",cn);result.put("PW","123456");
        result.put("MN","010000A8900016F000169DC0");result.put("Flag","9");result.put("CP",body);
        return result;
    }
    private Map<String,Object> body(String key,String value) {Map<String,Object> m=new LinkedHashMap<>();m.put(key,value);return m;}
    private byte[] bytes(String s) {return s.getBytes(StandardCharsets.UTF_8);}
    private byte[] hex(String s) {byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
    private String packet(String segment) {
        byte[] data=bytes(segment);int crc=0xffff;
        for(byte b:data){crc=(crc>>>8)^(b&255);for(int j=0;j<8;j++)crc=(crc&1)!=0?(crc>>>1)^0xa001:crc>>>1;}
        return String.format(Locale.ROOT,"##%04d%s%04X\r\n",data.length,segment,crc);
    }
    @Test public void appendixASm4CiphertextAndClearTail() throws Exception {
        // 附录 A.2 示例 3/4；使用固定密钥和固定密文，不以自我往返代替外部向量。
        String data="QN=20240520210700000;ST=31;CN=2051;PW=123456;MN=010000A8900016F000169DC0;Flag=9;CP=&&DataTime=20240520210600;a01011-Min=1.35,a01011-Avg=1.35,a01011-Max=1.35,a01011-Flag=N;a01014-Min=3.20,a01014-Avg=3.20,a01014-Max=3.20,a01014-Flag=N&&";
        String plain=packet(data); assertTrue(plain.endsWith("B541\r\n"));
        byte[] input=bytes(plain),encrypted=ProtocolCrypto.encrypt(input,key());
        int start=plain.indexOf("CP=&&")+5;
        byte[] expected=hex("E43E159ACFAB242CB53155FB8B9281A1A242577ABFAED881BE88B72D69FC55ED1A80119FAAC8B3CD33D285CC76F57F2CA7F88B9573C6647E0F07E24609BD6B63EB20C5B9784F28E71D2F4994FF61D9E13B391A1D2A454B65388D5D9435DBF021528C7BF9755EF16518710C0CAC0ADEF9C5156CD0A5A17674AC0ED2ABC40023E9EF70128554B82438E91ADCE98A0D03A4");
        assertArrayEquals(expected,Arrays.copyOfRange(encrypted,start,start+expected.length));
        assertEquals("g=N&&B541\r\n",new String(encrypted,start+expected.length,encrypted.length-start-expected.length,StandardCharsets.US_ASCII));
        assertArrayEquals(input,ProtocolCrypto.decrypt(encrypted,key()));
        assertArrayEquals(bytes(plain),input);
    }
    @Test public void shortEmptyAlignedAndUtf8EncryptedBodiesRoundTrip() throws Exception {
        for(String value:new String[]{"","123456789","测试中文内容测试中文内容","a21026-Rtd=1.25"}) {
            String packet=mapper().writeMapAsString(map("2011",value.isEmpty()?new LinkedHashMap<>():body("i11001-Info",value)));
            byte[] plain=bytes(packet),encrypted=ProtocolCrypto.encrypt(plain,key());
            assertEquals(plain.length,encrypted.length);
            assertArrayEquals(plain,ProtocolCrypto.decrypt(encrypted,key()));
            assertNotNull(mapper().readEncryptedData(encrypted,key()).getCp());
        }
    }
    @Test public void encryptedMapperMethodsPreserveFields() throws Exception {
        Data data=mapper().readData(mapper().writeMapAsString(map("2011",body("p99101-Rtd","St"))));
        byte[] encrypted=mapper().writeDataAsEncryptedBytes(data,key());
        assertEquals("St",mapper().readEncryptedData(encrypted,key()).getCp().getPollution().get("p99101").getRtdText());
        byte[] mapBytes=mapper().writeMapAsEncryptedBytes(map("1014",body("SKCreateTime","20240601085857223")),key());
        assertNotNull(mapper().readEncryptedDeepMap(mapBytes,key()).get("CP"));
    }
    @Test public void badKeysCorruptionAndInvalidFramesFail() throws Exception {
        byte[] plain=bytes(mapper().writeMapAsString(map("2011",body("DataTime","20240601085857"))));
        byte[] encrypted=ProtocolCrypto.encrypt(plain,key());
        assertThrows(IllegalArgumentException.class,()->ProtocolCrypto.encrypt(plain,new byte[15]));
        assertThrows(IllegalArgumentException.class,()->ProtocolCrypto.decrypt(encrypted,null));
        assertThrows(ProtocolFormatException.class,()->ProtocolCrypto.decrypt(encrypted,new byte[16]));
        byte[] corrupt=encrypted.clone();corrupt[plain.length-12]^=1;
        assertThrows(ProtocolFormatException.class,()->ProtocolCrypto.decrypt(corrupt,key()));
        assertThrows(ProtocolFormatException.class,()->ProtocolCrypto.encrypt(Arrays.copyOf(plain,plain.length-1),key()));
        assertThrows(ProtocolFormatException.class,()->ProtocolCrypto.encrypt(bytes(new String(plain,StandardCharsets.UTF_8).replace("ST=32","ST=31")),key()));
        assertThrows(ProtocolFormatException.class,()->ProtocolCrypto.decrypt(bytes("##0000"),key()));
    }
    @Test public void numericApiAndCharacterValuesCoexist() throws Exception {
        Map<String,Object> b=body("DataTime","20240601085857");b.put("p99101-Rtd","St");b.put("d10901-Rtd","N");b.put("a21026-Rtd","1.250");
        Data data=strict().readData(strict().writeMapAsString(map("2011",b)));
        assertNull(data.getCp().getPollution().get("p99101").getRtd());
        assertEquals("St",data.getCp().getPollution().get("p99101").getRtdText());
        assertEquals("1.250",data.getCp().getPollution().get("a21026").getRtd().toPlainString());
        Data copy=strict().readData(strict().writeDataAsString(data));
        assertEquals("N",copy.getCp().getPollution().get("d10901").getRtdText());
        assertEquals("St",copy.getCp().getPollution().get("p99101").getRtdText());
    }
    @Test public void unknownCpFieldsSurviveTypedRoundTrip() throws Exception {
        Map<String,Object> b=body("VendorValue","扩展内容");b.put("a21026-VendorMetric","custom");
        Data data=mapper().readData(mapper().writeMapAsString(map("2201",b)));
        Data copy=mapper().readData(mapper().writeDataAsString(data));
        assertEquals("扩展内容",copy.getCp().getExtensions().get("VendorValue"));
        assertEquals("custom",copy.getCp().getPollution().get("a21026").getFieldInfo().get("VendorMetric"));
    }
    @Test public void strictFieldBoundariesAreSymmetric() throws Exception {
        for(String[] invalid:new String[][]{{"RtdInterval","0"},{"MinInterval","7"},{"SampleType","5"},{"OverTime","100"},{"Stime","121"},{"QnRtn","10"},{"a21026-ResultType","2"},{"a21026-Flag","ABCD"},{"SB1000-RS","1"},{"SB001-RT","24.01"},{"a21026-StandardValue","1234567.00"},{"a21026-Rtd","St"},{"DataTime","20240230000000"}}) {
            Map<String,Object> m=map("2201",body(invalid[0],invalid[1]));
            assertThrows(ProtocolFormatException.class,()->strict().writeMapAsString(m),Arrays.toString(invalid));
            String p=mapper().writeMapAsString(m);
            assertThrows(ProtocolFormatException.class,()->strict().readDeepMap(p),Arrays.toString(invalid));
            assertThrows(ProtocolFormatException.class,()->strict().readMap(p),Arrays.toString(invalid));
            assertThrows(ProtocolFormatException.class,()->strict().readData(p),Arrays.toString(invalid));
        }
    }
    @Test public void strictHeaderAndSubpacketRules() throws Exception {
        for(String[] invalid:new String[][]{{"MN","ABC"},{"RF","0"},{"CN","5000"},{"ST","abc"},{"QN","20240601085857"},{"Flag","11"}}) {
            Map<String,Object> m=map("2011",new LinkedHashMap<>());m.put(invalid[0],invalid[1]);
            assertThrows(ProtocolFormatException.class,()->strict().writeMapAsString(m),Arrays.toString(invalid));
        }
        Map<String,Object> m=map("2011",new LinkedHashMap<>());m.put("Flag","11");m.put("PNUM","2");m.put("PNO","3");
        assertThrows(ProtocolFormatException.class,()->strict().writeMapAsString(m));m.put("PNO","2");
        assertEquals(2,strict().readData(strict().writeMapAsString(m)).getpNo());
        Map<String,Object> first=map("1001",body("i23005-Info","6970000000001ABCDEFG1234"));first.put("MN","");
        assertEquals("",strict().readData(strict().writeMapAsString(first)).getMn());
    }
    @Test public void clearCommandRequirementsAndKeyCharset() throws Exception {
        for(String cn:new String[]{"1000","1012","1014","1062","1064","1072","2081","9011","9012"})
            assertThrows(ProtocolFormatException.class,()->strict().writeMapAsString(map(cn,new LinkedHashMap<>())));
        Map<String,Object> b=body("SKCreateTime","20240601085857223");b.put("NewSK","Zz1234567890ABCD");
        Data data=strict().readData(strict().writeMapAsString(map("1014",b)));
        assertEquals("Zz1234567890ABCD",data.getCp().getNewSK());
        assertNotNull(strict().readData(strict().writeDataAsString(data)));
    }
    @Test public void binaryFramingKeepsCiphertextAndAdjacentPackets() throws Exception {
        byte[] first=mapper().writeMapAsEncryptedBytes(map("2011",body("i11001-Info","binary framing with CRLF inside ciphertext")),key());
        byte[] second=mapper().writeMapAsEncryptedBytes(map("1014",body("SKCreateTime","20240601085857223")),key());
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();out.write(first);out.write(second);
        java.io.ByteArrayInputStream input=new java.io.ByteArrayInputStream(out.toByteArray());
        assertArrayEquals(first,ProtocolFrame.read(input));assertArrayEquals(second,ProtocolFrame.read(input));assertNull(ProtocolFrame.read(input));
        java.io.InputStream shortReads=new java.io.FilterInputStream(new java.io.ByteArrayInputStream(out.toByteArray())) {
            @Override public int read(byte[] b,int off,int len) throws java.io.IOException {return super.read(b,off,Math.min(1,len));}
        };
        assertEquals("2011",mapper().readEncryptedData(shortReads,key()).getCn());
        assertEquals("1014",mapper().readEncryptedDeepMap(shortReads,key()).get("CN"));
        assertNull(mapper().readEncryptedData(shortReads,key()));
        byte[] binary=first.clone();int cpOffset=new String(first,StandardCharsets.ISO_8859_1).indexOf("CP=&&")+5;
        binary[cpOffset]=13;binary[cpOffset+1]=10;
        assertArrayEquals(binary,ProtocolFrame.read(new java.io.ByteArrayInputStream(binary)));
        assertThrows(java.io.EOFException.class,()->ProtocolFrame.read(new java.io.ByteArrayInputStream(Arrays.copyOf(first,first.length-1))));
        assertThrows(ProtocolFormatException.class,()->ProtocolFrame.read(new java.io.ByteArrayInputStream(bytes("##9999"))));
    }
    @Test public void sm4CompleteBlockAndRemainderBoundaries() throws Exception {
        for(int n:new int[]{0,1,13,14,15,29,30,31,62,63}) {
            String value=String.join("",Collections.nCopies(n,"x"));
            String plain=mapper().writeMapAsString(map("2201",body("X",value)));
            int start=plain.indexOf("CP=&&")+5,end=plain.length()-8;
            byte[] k=key(),before=k.clone(),original=bytes(plain);
            byte[] encrypted=ProtocolCrypto.encrypt(original,k);
            int length=((end-start)/16)*16;
            assertArrayEquals(Arrays.copyOfRange(original,end-(end-start-length),original.length),Arrays.copyOfRange(encrypted,start+length,encrypted.length));
            assertArrayEquals(original,ProtocolCrypto.decrypt(encrypted,k));
            assertArrayEquals(before,k);assertArrayEquals(bytes(plain),original);
        }
    }
    @Test public void strictPackAndByteInterfacesShareValidation() throws Exception {
        Map<String,Object> m=map("2011",body("DataTime","20240601085857"));
        byte[] p=strict().writeMapAsBytes(m);
        Pack pack=strict().readValue(p,Pack.class);
        java.io.StringWriter writer=new java.io.StringWriter();strict().writeValueAsWriter(pack,Pack.class,writer);
        assertArrayEquals(p,bytes(writer.toString()));
        Data data=strict().readData(p);data.getCp().setSampleType(5);
        assertThrows(ProtocolFormatException.class,()->strict().writeDataAsBytes(data));
        pack.setData("QN=20240601085857223;ST=00;CN=2011;PW=123456;MN=010000A8900016F000169DC0;Flag=8;CP=&&&&".toCharArray());
        assertThrows(ProtocolFormatException.class,()->strict().writeValueAsWriter(pack,Pack.class,new java.io.StringWriter()));
    }
    @Test public void invalidUtf8IsRejectedWithoutReplacementCharacters() throws Exception {
        byte[] p=mapper().writeMapAsBytes(map("2201",body("i11001-Info","test")));
        int position=new String(p,StandardCharsets.UTF_8).indexOf("test");p[position]=(byte)0xFF;
        assertThrows(java.io.IOException.class,()->mapper().readData(p));
    }
    @Test public void strictDetectsDuplicatesAndCpLength() throws Exception {
        String p=mapper().writeMapAsString(map("2011",body("DataTime","20240601085857")));
        String s=p.substring(6,p.length()-6).replace("DataTime=20240601085857","DataTime=20240601085857;DataTime=20240601085857");
        assertThrows(ProtocolFormatException.class,()->strict().readDeepMap(packet(s)));
        Map<String,Object> m=map("2201",body("VendorValue",String.join("",Collections.nCopies(940,"x"))));
        assertThrows(ProtocolFormatException.class,()->strict().writeMapAsString(m));
    }
}
