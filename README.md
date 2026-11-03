# Talkin VIP Unlock（LSPosed 模块）

针对 Talkin 5.9.0（`com.talk.inapp`）的 LSPosed/Xposed 模块，hook 客户端 VIP 状态判定。
基于 jadx 反编译分析定位 hook 点，仅供学习交流。

## 仓库结构

```
├── TalkinVIP-1.0.apk            # 模块成品（已签名，可直接安装）
├── lspmod/                      # 模块工程（aapt2 + javac + d8 手动构建链）
│   ├── app/src/main/
│   │   ├── java/com/coffeecold/talkinvip/MainHook.java   # hook 源码
│   │   ├── AndroidManifest.xml  # xposedmodule 元数据 + scope
│   │   ├── assets/xposed_init   # Xposed 入口声明
│   │   └── res/values/arrays.xml # 作用域声明
│   ├── apistub/                 # de.robv API 编译桩（运行时由 LSPosed 提供）
│   ├── libs/xposed-api-stub.jar # 桩编译产物
│   └── frida/talkin_vip_hook.js # 同逻辑的 Frida 脚本（调试用）
└── .gitignore                   # 原始 APK / 工具 / 反编译输出不入库
```

## Hook 点（来自反编译结论）

全 app 43 处 VIP 判定收敛到单例 `defpackage.j6c`（经 `gz6.j()` 获取）：

| Hook 目标 | 改为 | 作用 |
|---|---|---|
| `j6c.j()` | `true` | 全局 isVip 判定（核心） |
| `j6c.i()` | `true` | has_trialed（试用状态） |
| `j6c.k()` | `true` | was_vip |
| `VipInfo.getIsVip()/getWasVip()/getHasTrailed()` | `true` | 实体层兜底 + 回写内部字段 |
| `VipDetailResp.getIsVip()/getWasVip()` | `true` | 接口层兜底 |
| `VipDetail.getHas_trialed()` | `false` | 试用入口常开 |
| `VipDetail.getExpire_time()` | `4102444800L` | 到期时间拉远 |

加载方式：hook `ClassLoader.loadClass`，`defpackage.j6c` 加载即安装（多 dex 动态加载兼容）。

## 解锁功能清单（客户端门禁）

语音房字幕不限时、建房音频配额、私聊热门用户/日限次数、匹配高级筛选、
看过我/谁喜欢我、访客在线状态、发音学习次数、建群、VIP 价兑换、
VIP 角标勋章、通话 VIP 标识、付费墙弹窗短路。

服务端强校验部分（VIP 解锁接口返回、Google Play 支付票据验证）不受影响。

## 构建链

```bash
# 0. 编译 Xposed API 桩
javac -d apistub-classes $(ls apistub/de/robv/android/xposed/*.java apistub/de/robv/android/xposed/callbacks/*.java)
jar cf libs/xposed-api-stub.jar -C apistub-classes .

# 1. 编译模块
javac -encoding UTF-8 -classpath libs/xposed-api-stub.jar \
  -bootclasspath "$SDK/platforms/android-37.0/android.jar" \
  -d app/build-classes $(find app/src/main/java -name "*.java")

# 2. 资源
aapt2 compile --dir app/src/main/res -o app/build-res.zip
aapt2 link -o app/talkinvip-unsigned.apk -I "$SDK/platforms/android-37.0/android.jar" \
  --manifest app/src/main/AndroidManifest.xml -A app/src/main/assets app/build-res.zip

# 3. dex 并入 APK
d8 --release --min-api 27 --output app/build-dex (classes)
zip/jython 追加 classes.dex 到 APK

# 4. 签名
zipalign -f 4 in.apk out.apk
apksigner sign --ks debug.keystore --out TalkinVIP-1.0.apk out.apk
```

## 安装使用

1. 安装 `TalkinVIP-1.0.apk`
2. LSPosed 管理器 → 模块 → 启用，作用域勾选 Talkin
3. 强停 Talkin 后重开，LSPosed 日志应出现 `[TalkinVIP] hooks installed on defpackage.j6c`
