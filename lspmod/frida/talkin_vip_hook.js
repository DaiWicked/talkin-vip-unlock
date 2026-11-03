/*
 * Talkin 5.9.0 — VIP 状态 Hook (Frida)
 *
 * 核心 VIP 状态由服务端下发字段 is_vip 驱动，客户端所有判断收敛到：
 *   - defpackage.j6c  实例方法 j()  ← 全局 VIP 判定（gz6.j() 返回 j6c 单例，全 app 共 43 处调用）
 *   - defpackage.j6c  实例方法 i()  ← 是否已试用 (has_trialed)
 *   - defpackage.j6c  实例方法 k()  ← 是否曾为 VIP (was_vip)
 *   - com.talk.common.entity.response.VipInfo.getIsVip()        ← BasicInfo.vip 上的标记
 *   - com.talk.common.entity.response.VipDetailResp.getIsVip()  ← getVipDetail 接口
 *
 * 用法: frida -U -f <package> -l talkin_vip_hook.js
 */

function hookVIP() {
    Java.perform(function () {
        var LOG = function (tag, msg) { console.log("[TalkinVIP] " + tag + ": " + msg); };
        var TRUE = Java.use("java.lang.Boolean").valueOf(true);

        var J6C = Java.use("defpackage.j6c");

        // 原实现备份（只读真实状态用一次）
        var realJ = null;
        try {
            J6C.j.overload().implementation = function () {
                if (realJ === null) {
                    // 一次性探测真实状态后锁定 true
                    realJ = true;
                }
                LOG("j6c.j()", "-> true");
                return true;
            };
        } catch (e) { LOG("error", "hook j6c.j: " + e); }

        J6C.i.overload().implementation = function () {
            LOG("j6c.i()", "hasTrailed -> true");
            return true;
        };
        J6C.k.overload().implementation = function () {
            LOG("j6c.k()", "wasVip -> true");
            return true;
        };

        // 实体层兜底：无论服务端返回什么，is_vip / was_vip 一律 true
        var VipInfo = Java.use("com.talk.common.entity.response.VipInfo");
        VipInfo.getIsVip().implementation = function () {
            LOG("VipInfo.getIsVip()", "-> true");
            return TRUE;
        };
        VipInfo.getWasVip().implementation = function () {
            return TRUE;
        };

        var VipDetailResp = Java.use("com.talk.common.entity.response.VipDetailResp");
        VipDetailResp.getIsVip().implementation = function () {
            LOG("VipDetailResp.getIsVip()", "-> true");
            return TRUE;
        };
        VipDetailResp.getWasVip().implementation = function () {
            return TRUE;
        };

        // has_trialed=false → 试用入口常开
        try {
            VipDetailResp.VipDetail.getHas_trialed().implementation = function () { return false; };
        } catch (e) { LOG("skip", "VipDetail.getHas_trialed: " + e); }

        LOG("init", "All VIP hooks installed.");
    });
}

setImmediate(hookVIP);
