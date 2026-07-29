package com.github.guocay.hj212.model.verify;

import com.github.guocay.hj212.core.validator.clazz.FieldC;
import com.github.guocay.hj212.core.validator.clazz.FieldN;
import com.github.guocay.hj212.core.validator.clazz.FieldValidDate;
import com.github.guocay.hj212.core.validator.field.CharArray;
import com.github.guocay.hj212.core.validator.field.NumberArray;
import com.github.guocay.hj212.core.validator.field.Date;
import com.github.guocay.hj212.model.verify.groups.ProtocolMapLevelGroup;

import java.util.Map;

/**
 * T212 Map - HJ 212-2025
 * <p>解决无法对MAP进行验证定义问题</p>
 * <p>2025版与2017版的主要差异：
 *   1. MN编码格式（EPC-96）保持一致
 *   2. Flag标记含义更新：V5~V0=000010表示2025版
 *   3. 新增RF补传标志字段（optional）</p>
 * @author aCay
 */
@SuppressWarnings("serial")
@FieldValidDate(field = "QN",
        value = @Date(format = "yyyyMMddHHmmssSSS"))
@FieldC(field = "ST",
        value = @CharArray(len = 2))
@FieldC(field = "CN",
        value = @CharArray(len = 4))
@FieldC(field = "PW",
        value = @CharArray(len = 6))
@FieldC(field = "MN",
        value = @CharArray(len = 24))
@FieldN(field = "Flag",
        value = @NumberArray(integer = 3))
@FieldN(field = "PNUM",
        value = @NumberArray(integer = 4, optional = true))
@FieldN(field = "PNO",
        value = @NumberArray(integer = 4, optional = true))
@FieldN(field = "RF",
        value = @NumberArray(integer = 1, optional = true))
@FieldC(field = "CP", groups = { ProtocolMapLevelGroup.DataLevel.class },
        value = @CharArray(len = 950))
public class ProtocolMapV2025<K,V>
        extends ProtocolMap<K,V> {

    public ProtocolMapV2025(Map<K, V> m) {
        super(m);
    }
}
