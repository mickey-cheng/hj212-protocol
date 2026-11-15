package com.github.guocay.hj212.netty;

import com.github.guocay.hj212.business.mapper.MonitorFactorMapping;
import com.github.guocay.hj212.business.po.MonitorFactorPo;
import com.github.guocay.hj212.business.service.MonitorService;
import com.github.guocay.hj212.core.*;
import com.github.guocay.hj212.core.feature.VerifyFeature;
import com.github.guocay.hj212.model.*;
import io.netty.buffer.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Proxy;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 使用实际服务管道模拟数采仪，不连接数据库。 */
public class NettyReceptionTest {
    private final List<MonitorFactorPo> inserted=new ArrayList<>();
    private MonitorService service() {
        MonitorFactorMapping mapper=(MonitorFactorMapping)Proxy.newProxyInstance(
            MonitorFactorMapping.class.getClassLoader(),new Class<?>[]{MonitorFactorMapping.class},
            (p,m,args)->{if(m.getName().equals("insert")){inserted.add((MonitorFactorPo)args[0]);return 1;}return null;});
        return new MonitorService(mapper);
    }
    private ProtocolMapper mapper(){return new ProtocolMapper().enableDefaultParserFeatures().enableDefaultVerifyFeatures().enable(VerifyFeature.DATA_CRC);}
    private String packet(int flag,String cp) throws Exception {
        Map<String,Object> map=new LinkedHashMap<>();
        map.put("QN","20240601085857223");map.put("ST","32");map.put("CN","2011");map.put("PW","123456");
        map.put("MN","010000A8900016F000169DC0");map.put("Flag",Integer.toString(flag));map.put("CP",cp);
        // 原始 CP 采用 Map 表达，保证生成 && 分隔符。
        Map<String,Object> data=new LinkedHashMap<>();
        if(!cp.isEmpty()) for(String item:cp.split(";")){String[] kv=item.split("=",2);data.put(kv[0],kv[1]);}
        map.put("CP",data);
        return mapper().writeMapAsString(map);
    }
    @Test public void fragmentedAndCoalescedPacketsKeepFooterAndReply() throws Exception {
        EmbeddedChannel channel=new EmbeddedChannel();
        NettyServer.configurePipeline(channel.pipeline(),new NettyServerHandler(service()));
        String first=packet(9,"DataTime=20240601085857;a21026-Rtd=1.25");
        String second=packet(8,"DataTime=20240601085858;a21026-Rtd=2.50");
        try {
            channel.writeInbound(Unpooled.copiedBuffer(first.substring(0,17),StandardCharsets.UTF_8));
            assertTrue(inserted.isEmpty());
            channel.writeInbound(Unpooled.copiedBuffer(first.substring(17)+second,StandardCharsets.UTF_8));
            assertEquals(2,inserted.size());
            ByteBuf response=channel.readOutbound();assertNotNull(response);
            try {
                Data reply=mapper().readData(response.toString(StandardCharsets.UTF_8));
                assertEquals("9014",reply.getCn());assertEquals("91",reply.getSt());
                assertEquals(Collections.singletonList(DataFlag.V1),reply.getDataFlag());
            } finally {response.release();}
            assertNull(channel.readOutbound());
        } finally {channel.finishAndReleaseAll();}
    }
    @Test public void statisticsAndEmptyCpDoNotCrashOrBecomeRealTimeValues() throws Exception {
        MonitorService service=service();
        assertNotNull(service.monitor(packet(9,"DataTime=20240601085857;a21026-Avg=5.5"),"127.0.0.1"));
        assertNotNull(service.monitor(packet(9,""),"127.0.0.1"));
        assertTrue(inserted.isEmpty());
    }
    @Test public void crcFailureDoesNotInsert() throws Exception {
        String broken=packet(9,"DataTime=20240601085857;a21026-Rtd=1.25").replace("Rtd=1.25","Rtd=9.25");
        assertThrows(Exception.class,()->service().monitor(broken,"127.0.0.1"));
        assertTrue(inserted.isEmpty());
    }
}
