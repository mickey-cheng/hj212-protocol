package com.github.guocay.hj212.core.decode;


import com.github.guocay.hj212.core.ProtocolParser;
import com.github.guocay.hj212.core.VerifyUtil;
import com.github.guocay.hj212.core.converter.DataConverter;
import com.github.guocay.hj212.exception.ProtocolFormatException;
import com.github.guocay.hj212.exception.SegmentFormatException;
import com.github.guocay.hj212.model.Data;
import com.github.guocay.hj212.model.DataFlag;
import com.github.guocay.hj212.model.verify.PacketElement;
import com.github.guocay.hj212.model.verify.ProtocolMap;
import com.github.guocay.hj212.model.verify.groups.ModeGroup;
import com.github.guocay.hj212.model.verify.groups.VersionGroup;
import com.github.guocay.hj212.segment.config.Configurator;
import com.github.guocay.hj212.segment.config.Configured;
import com.github.guocay.hj212.segment.core.SegmentParser;
import com.github.guocay.hj212.segment.core.decode.SegmentDeserializer;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.guocay.hj212.core.ProtocolParser.crc16Checkout;
import static com.github.guocay.hj212.core.feature.VerifyFeature.DATA_CRC;
import static com.github.guocay.hj212.core.feature.VerifyFeature.DATA_LEN_RANGE;
import static com.github.guocay.hj212.core.feature.VerifyFeature.THROW_ERROR_VERIFICATION_FAILED;
import static com.github.guocay.hj212.core.feature.VerifyFeature.USE_VERIFICATION;
import static com.github.guocay.hj212.core.validator.clazz.FieldValidator.create_format_exception;

/**
 * 对象 级别 反序列化器
 * @author aCay
 */
@SuppressWarnings({ "rawtypes", "unused" })
public class DataDeserializer
        implements ProtocolDeserializer<Data>, Configured<DataDeserializer> {

    private int verifyFeature;
    private Configurator<SegmentParser> segmentParserConfigurator;
    private SegmentDeserializer<Map<String,Object>> segmentDeserializer;
    private Configurator<DataConverter> dataConverterConfigurator;
    private Validator validator;

    @Override
    public void configured(Configurator<DataDeserializer> configurator){
        configurator.config(this);
    }

    @Override
    public Data deserialize(ProtocolParser parser) throws IOException, ProtocolFormatException {
        parser.readHeader();
        int len = parser.readInt32(10);
        if(len == -1){
            ProtocolFormatException.length_not_range(PacketElement.DATA_LEN,len,4,4);
        }
        if(DATA_LEN_RANGE.enabledIn(verifyFeature)){
            VerifyUtil.verifyRange(len,0,1024, PacketElement.DATA_LEN);
        }
        char[] data = parser.readData(len);
        int crc = parser.readInt32(16);

        if(DATA_CRC.enabledIn(verifyFeature)){
            if(crc == -1 ||
                    crc16Checkout(data,data.length) != crc){
                ProtocolFormatException.crc_verification_failed(PacketElement.DATA,data,crc);
            }
        }
        parser.readFooter();
        return deserialize(data);
    }

    public Data deserialize(char[] data) throws IOException, ProtocolFormatException {
        if (com.github.guocay.hj212.core.feature.VerifyFeature.STRICT_2025.enabledIn(verifyFeature))
            com.github.guocay.hj212.core.validator.Protocol2025Validator.validate(data);
        PushbackReader reader = new PushbackReader(new CharArrayReader(data));
        SegmentParser parser = new SegmentParser(reader);
        parser.configured(segmentParserConfigurator);

        Map<String,Object> result = null;
        try {
            result = segmentDeserializer.deserialize(parser);
        } catch (SegmentFormatException e) {
            ProtocolFormatException.segment_exception(e);
        }
        return deserialize(result);
    }

    public Data deserialize(Map<String,Object> map) throws ProtocolFormatException {
        DataConverter dataConverter = new DataConverter();
        dataConverter.configured(dataConverterConfigurator);
        Data result = dataConverter.convert(ProtocolMap.createCpDataLevel(map));

        if(USE_VERIFICATION.enabledIn(verifyFeature)){
            verify(result);
        }
        return result;
    }

	private void verify(Data result) throws ProtocolFormatException {
        List<Class> groups = new ArrayList<>();
        groups.add(Default.class);
        groups.add(DataFlag.versionGroup(result.getDataFlag()));
        if(DataFlag.D.isMarked(result.getDataFlag())){
            groups.add(ModeGroup.UseSubPacket.class);
        }

        Set<ConstraintViolation<Data>> constraintViolationSet =
                validator.validate(result,groups.toArray(new Class[]{}));
        if(!constraintViolationSet.isEmpty()) {
            if(THROW_ERROR_VERIFICATION_FAILED.enabledIn(verifyFeature)){
                create_format_exception(constraintViolationSet,result);
            }else{
                //TODO set context
            }
        }
    }

    public void setVerifyFeature(int verifyFeature) {
        this.verifyFeature = verifyFeature;
    }

    public void setSegmentParserConfigurator(Configurator<SegmentParser> segmentParserConfigurator) {
        this.segmentParserConfigurator = segmentParserConfigurator;
    }

    public void setSegmentDeserializer(SegmentDeserializer<Map<String, Object>> segmentDeserializer) {
        this.segmentDeserializer = segmentDeserializer;
    }

    public void setDataConverterConfigurator(Configurator<DataConverter> mapperConfigurator) {
        this.dataConverterConfigurator = mapperConfigurator;
    }

    public void setValidator(Validator validator) {
        this.validator = validator;
    }

}
