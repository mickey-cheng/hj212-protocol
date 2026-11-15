package com.github.guocay.hj212.business.service;

import com.github.guocay.hj212.business.po.MonitorFactorPo;
import com.github.guocay.hj212.core.ProtocolMapper;
import com.github.guocay.hj212.model.CpData;
import com.github.guocay.hj212.model.Data;
import com.github.guocay.hj212.model.DataFlag;
import com.github.guocay.hj212.business.core.annotaion.MonitorServiceListen;
import com.github.guocay.hj212.business.core.util.Constant;
import com.github.guocay.hj212.business.mapper.MonitorFactorMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

/**
 * 用于接收hj212协议内容
 * @author GuoKai
 * @since 2019/11/22
 */
@Service
@Scope(SCOPE_PROTOTYPE)
public class MonitorService {

	private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

    private final MonitorFactorMapping factorMapper;

    private final ProtocolMapper t212Mapper = ProtocolMapper.getInstance()
            .enableDefaultParserFeatures().enableDefaultVerifyFeatures()
            .enable(com.github.guocay.hj212.core.feature.VerifyFeature.DATA_CRC);

    public MonitorService(MonitorFactorMapping factorMapper) {
        this.factorMapper = factorMapper;
    }

    @MonitorServiceListen("hj212协议接收服务")
    public String monitor(String monitorData,String ipAddress) throws Exception{
        String response = null;
		Data data = t212Mapper.readData(monitorData);
		if (data.getCp() != null && data.getCp().getPollution() != null) data.getCp().getPollution().forEach((key, value) -> {
            if (value.getRtd() == null) return;
			MonitorFactorPo monitorFactorPo = new MonitorFactorPo();
			monitorFactorPo.setDataStatus(Constant.EFFECTIVE).setMncode(data.getMn());
			monitorFactorPo.setDataTime(data.getCp().getDataTime()).setFactor(key);
			monitorFactorPo.setValue(value.getRtd().toString());
			monitorFactorPo.setTime(LocalDateTime.now(Clock.systemDefaultZone()));
			factorMapper.insert(monitorFactorPo);
		});
		if (DataFlag.A.isMarked(data.getDataFlag())) {
			Data builder = new Data();
			builder.setQn(data.getQn()).setSt("91").setCn(Constant.DATA_RESPONSE);
			builder.setPw(data.getPw()).setMn(data.getMn()).setCp(new CpData());
			builder.setDataFlag(data.getDataFlag() == null ? Collections.emptyList() :
                data.getDataFlag().stream().filter(f -> f != DataFlag.A && f != DataFlag.D)
                    .collect(java.util.stream.Collectors.toList()));
			response = t212Mapper.writeDataAsString(builder);
		}
        return response;
    }
}
