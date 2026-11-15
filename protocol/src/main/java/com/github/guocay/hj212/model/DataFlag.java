package com.github.guocay.hj212.model;

import java.util.Collection;

/**
 * 数据段 标志
 * @author aCay
 */
public enum DataFlag {

    /**
     * 命令是否应答
     */
    A(1),
    /**
     * 是否有数据包序号
     */
    D(2),
    /**
     * 标准版本号V0（HJ 212-2017）
     * <p>HJ 212-2025: V5~V0=000001表示HJ 212-2017</p>
     */
    V0(4),
    /**
     * 标准版本号V1（HJ 212-2025）
     * <p>HJ 212-2025: V5~V0=000010表示本次修订版本号</p>
     */
    V1(8),
    /**
     * 标准版本号V2
     */
    V2(16),
    /**
     * 标准版本号V3
     */
    V3(32),
    /**
     * 标准版本号V4
     */
    V4(64),
    /**
     * 标准版本号V5
     */
    V5(128);

    /** 按完整版本位识别，避免将 2025 版误判为 2005 版。 */
    public static Class<?> versionGroup(int flag) {
        switch ((flag & 0xFC) >>> 2) {
            case 0: return com.github.guocay.hj212.model.verify.groups.VersionGroup.V2005.class;
            case 1: return com.github.guocay.hj212.model.verify.groups.VersionGroup.V2017.class;
            case 2: return com.github.guocay.hj212.model.verify.groups.VersionGroup.V2025.class;
            default: throw new IllegalArgumentException("不支持的协议版本: " + flag);
        }
    }

    public static Class<?> versionGroup(Collection<DataFlag> flags) {
        int flag = 0;
        if (flags != null) for (DataFlag value : flags) flag |= value.bit;
        return versionGroup(flag);
    }

    private int bit;

    DataFlag(){
        this.bit = (1 << ordinal());
    }

    DataFlag(int bit){
        this.bit = bit;
    }

    public int getBit() {
        return bit;
    }

    public boolean isMarked(int flags) {
        return (flags & bit) != 0;
    }

    public boolean isMarked(Collection<DataFlag> flags) {
        return flags != null && flags.contains(this);
    }

}
