package com.github.guocay.hj212.model.verify.groups;

import com.github.guocay.hj212.model.DataFlag;

/**
 * 版本 验证组
 * @see DataFlag#V0
 * @author aCay
 */
public interface VersionGroup {

    /**
     * 2005版
     */
    interface V2005{}

    /**
     * 2017版
     */
    interface V2017{}

    /**
     * 2025版 (HJ 212-2025)
     * <p>V5~V0=000010 表示本次修订版本号</p>
     */
    interface V2025{}
}
