# HJ212 Protocol

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Maven](https://img.shields.io/badge/Maven-tested_3.10.0-blue)](https://maven.apache.org/)

> 一个轻量级的**污染物在线监控（监测）系统数据传输标准**（HJ/T 212）Java 编解码库。
>
> 支持 HJ/T 212-2005、HJ 212-2017 以及 HJ 212-2025 三个版本。

---

## 目录

- [协议背景](#协议背景)
- [当前状态](#当前状态)
- [项目边界](#项目边界)
- [特性](#特性)
- [架构概览](#架构概览)
- [快速开始](#快速开始)
  - [引入依赖](#引入依赖)
  - [解析数据包](#解析数据包)
  - [生成数据包](#生成数据包)
- [数据模型](#数据模型)
  - [通信包结构](#通信包结构)
  - [Data — 数据段](#data--数据段)
  - [CpData — 数据区](#cpdata--数据区)
  - [版本兼容](#版本兼容)
- [配置系统](#配置系统)
  - [解析特性（ParserFeature）](#解析特性parserfeature)
  - [校验特性（VerifyFeature）](#校验特性verifyfeature)
  - [段解析特性（SegmentParserFeature）](#段解析特性segmentparserfeature)
  - [数据校验（Jakarta Validation）](#数据校验jakarta-validation)
- [示例模块](#示例模块)
- [目录结构](#目录结构)
- [HJ 212—2025 编解码接口](#hj-2122025-编解码接口)
- [构建与测试](#构建与测试)
- [许可证](#许可证)

---

## 协议背景

HJ/T 212 系列标准由国家生态环境部发布，规定了污染物在线监控（监测）系统与监控中心之间的数据传输格式。本库提供以下版本的报文编解码；2025 版的校验和加密接口范围详见 [2025 编解码使用说明](docs/HJ212-2025编解码使用说明.md)：

| 标准版本 | 状态 | 版本标记 |
|----------|------|----------|
| HJ/T 212-2005 | ✅ 支持 | V5~V0 = `000000` |
| HJ 212-2017 | ✅ 支持 | V5~V0 = `000001` |
| HJ 212-2025 | ✅ 支持 | V5~V0 = `000010` |

## 当前状态

已完成本项目约定的 HJ 212—2025 编解码适配、字段校验细节、附录 C 全部命令样例回归和接口一致性工作。项目定位为支持 HJ 212—2025 的编解码库；这项完成状态不等同于全面合规认证或所有厂商设备互操作认证。

当前 3368 项测试全部通过：核心模块 3365 项、独立示例模块 3 项。已在 OpenJDK 17 验证；Java 8 运行时和真实设备互操作尚未实测。

- [编解码使用说明](docs/HJ212-2025编解码使用说明.md)：接口、严格模式、SM4、分帧及模型用法。
- [回归覆盖说明](docs/HJ212-2025回归覆盖说明.md)：63 张表、188 条报文、69 组字段规则及标准样例处理原则。
- [修复说明](docs/HJ212-2025修复说明.md)：最终修复内容及验证范围。

## 项目边界

核心模块 `protocol` 只负责报文、数据模型、字段校验及显式加解密。设备密钥存储与轮换、网络会话、重发补传、数据库及设备控制由调用方负责。`example` 是独立演示模块。

## 特性

- **三层分层架构**：通信包 → 键值段 → 数据模型，职责清晰
- **多版本兼容**：同一套 API 同时处理 2005 / 2017 / 2025 报文
- **可插拔特性配置**：通过位掩码灵活控制解析 / 校验行为
- **完整的 CRC16 校验**：使用标准附录 A.1 的 CRC16 算法
- **强类型数据模型**：自动将 `KEY=VALUE` 报文转换为强类型 POJO
- **Jakarta Validation 校验**：字段级格式、取值范围、条件必填校验
- **零侵入**：目标为 Java 8；使用 Jackson、Jakarta Validation 及实现、SLF4J，SM4 采用 Bouncy Castle

## 架构概览

```
┌─────────────────────────────────┐
│   Data (强类型模型)              │  ← 应用层使用
├─────────────────────────────────┤
│   DataConverter / Reverse       │  ← 模型 ↔ Map 互转
│   DataSerializer / Deserializer │
├─────────────────────────────────┤
│   SegmentParser / Generator     │  ← KEY=VALUE;&&...&& 解析
├─────────────────────────────────┤
│   ProtocolParser / Generator    │  ← ##LEN+DATA+CRC\r\n 解析
└─────────────────────────────────┘
```

**核心类职责**：

| 类 | 职责 |
|----|------|
| `ProtocolMapper` | **对外统一 API 入口**，提供 `readData()` / `writeDataAsString()` 等方法 |
| `ProtocolParser` | 解析通信包：包头、数据段长度、CRC16 校验、包尾 |
| `ProtocolGenerator` | 生成通信包：写入头、长度、校验码 |
| `SegmentParser` | Token 状态机解析 `KEY=VALUE;KEY=VALUE;&&...&&` 格式 |
| `SegmentGenerator` | 生成分段键值对 |
| `DataConverter` | 将解析后的 Map 分组转换为强类型 `Data` / `CpData` / `Pollution` / `Device` / `LiveSide` |
| `DataReverseConverter` | 将强类型对象展平回 Map |
| `VerifyUtil` | CRC16、长度、范围等校验工具 |

## 快速开始

### 引入依赖

```xml
<dependency>
    <groupId>com.github.guocay</groupId>
    <artifactId>hj212-protocol</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

以上版本与当前源码 POM 一致，不表示已经发布到公共仓库。使用当前工作区版本时，先在项目根目录执行 `mvn -pl protocol -am install`；发布后使用实际发布版本号。

### 解析数据包

```java
// 创建 Mapper 实例（配置完成后可复用；运行中不要并发修改配置）
ProtocolMapper mapper = ProtocolMapper.getInstance()
        .enableDefaultParserFeatures()
        .enableDefaultVerifyFeatures();

// 2017 版明文报文（Flag=5）；2025 严格模式及加密用法见后文。
String packet = "##0160QN=20200101120000001;ST=32;CN=2011;PW=123456;MN=010000A8900016F022169DC0;Flag=5;CP=&&DataTime=20200101120000;B01-Rtd=12.34,B01-Flag=N;301-Rtd=56.78,301-Flag=N&&09C0\r\n";

// 解析为强类型对象
Data data = mapper.readData(packet);

// 获取基本信息
System.out.println(data.getQn());          // 20200101120000001
System.out.println(data.getCn());          // 2011
System.out.println(data.getSt());          // 32

// 获取数据区
CpData cp = data.getCp();
System.out.println(cp.getDataTime());      // 20200101120000

// 获取污染因子
Map<String, Pollution> pollution = cp.getPollution();
Pollution b01 = pollution.get("B01");
System.out.println(b01.getRtd());          // 12.34
System.out.println(b01.getFlag());         // N
```

### 生成数据包

```java
Data data = new Data()
    .setQn("20200101120000001")
    .setSt("32").setCn("2011").setPw("123456")
    .setMn("010000A8900016F022169DC0")
    .setDataFlag(java.util.Arrays.asList(DataFlag.A, DataFlag.V0));

CpData cp = new CpData();
cp.setDataTime("20200101120000");

Map<String, Pollution> pollutions = new LinkedHashMap<>();
Pollution p = new Pollution();
p.setRtd(new BigDecimal("12.34"));
p.setFlag("N");
pollutions.put("B01", p);
cp.setPollution(pollutions);

data.setCp(cp);

// 生成 HJ212 报文
String packet = mapper.writeDataAsString(data);
System.out.println(packet);
// 输出包含包头、UTF-8 数据段字节长度、数据段、四位 CRC 和 CRLF。
```

## 数据模型

### 通信包结构

```
##  LEN  QN=...;ST=...;CN=...;PW=...;MN=...;Flag=...;RF=...;CP=&&...&&  CRC  \r\n
↑2    ↑4      ↑ 数据段（0≤len≤1024）                                      ↑4   ↑2
```

### Data — 数据段

| 字段 | 类型 | 长度 | 必填 | 说明 |
|------|------|------|------|------|
| `QN` | String | 17 | ✅ | 时间戳 yyyyMMddHHmmssSSS |
| `ST` | String | 2 | ✅ | 系统编码（如 21/31/32/51/91） |
| `CN` | String | 4 | ✅ | 命令编码（如 2011/2061/3011） |
| `PW` | String | ≤6 | ✅ | 访问密码 |
| `MN` | String | 24/14 | ✅ | 2025 为 24 位大写十六进制入网编码；首次联网例外见使用说明；2005 为最多 14 位 |
| `Flag` | List<DataFlag> | 1～3 | ✅ | 线上编码为 0～255 的整数，模型转换为标志位列表 |
| `PNUM` | Integer | — | 条件 | 总包数（分包位 2 被设置时必填） |
| `PNO` | Integer | — | 条件 | 包号（分包位 2 被设置时必填） |
| `RF` | Integer | 1 | 可选 | 🆕 补传标志（2025新增，RF=1为补传） |
| `CP` | CpData | 见说明 | ✅ | 严格模式下 CP=&&数据区&& 整段最多 950 个传输字节 |

### CpData — 数据区

数据区按以下四类组织：

**1. 普通键值对** — 直接映射到 `CpData` 属性：

| 字段 | 说明 | 版本 |
|------|------|------|
| `SystemTime` | 系统时间 | 2017 |
| `QnRtn` | 请求回应代码 | |
| `ExeRtn` | 执行结果回应代码 | |
| `RtdInterval` | 实时采样间隔 | 2017 |
| `DataTime` | 数据时间 | |
| `BeginTime` / `EndTime` | 时间范围 | |
| `PolId` | 污染因子编码 | |
| `NewPW` | 新密码 | 2017 |
| `NewSK` | 新密钥（C16，字母数字字符，不限十六进制） | 🆕 2025 |
| `SKCreateTime` | 密钥创建时间 | 🆕 2025 |
| `CalibrationType` | 校准类型（0零点/1量程） | 🆕 2025 |
| `SampleType` | 采样模式 | 🆕 2025 |

**2. 污染因子数据** — `xxxxxx- {Rtd/Min/Avg/Max/ZsRtd/...}` 模式，自动分组为 `Map<String, Pollution>`：

```java
// HJ212报文中的:
// w01001-Rtd=7.1,w01001-Flag=N,w01001-Cou=150.0,w01003-Rtd=0.05
// 自动解析为:
cp.getPollution().get("w01001").getRtd();  // 7.1
cp.getPollution().get("w01001").getFlag(); // N
```

🆕 **2025 新增污染因子字段**：
`CompleteTime`, `WcRtd/RawRtd`, `WcMin/WcAvg/WcMax`, `RawMin/RawAvg/RawMax`, `StandardValue`, `SampleRd`, `ResultType`, `SampleType`, `SN`, `fieldInfo`（`xxxxxx-vvvvvv` 格式的现场端关联信息）

**3. 设备运行状态** — `SByyy-RS/RT` 模式（`yyy` 取值 2005: [1,99], 2017: [1,99], 2025: [0,999]）：

```java
cp.getDevice().get("01").getRs();  // 运行状态
cp.getDevice().get("01").getRt();  // 运行时间
```

**4. 现场端信息** — `vvvvvv-Info/SN` 模式（vvvvvv为6位 i 开头编码）：

```java
cp.getLiveSide().get("i12001").getInfo();
```

### 版本兼容

通过 `DataFlag` 自动区分版本，校验器按分组执行：

```java
// 解析后，Data 的 flag 列表中自动包含版本信息
data.getDataFlag().contains(DataFlag.V0);   // 2017: true, 2025: false
data.getDataFlag().contains(DataFlag.V1);   // 2017: false, 2025: true
```

校验系统通过 VersionGroup（`V2005` / `V2017` / `V2025`）按版本分组执行对应的校验规则，确保不同版本的字段约束独立生效。

## 配置系统

### 解析特性（ParserFeature）

```java
ProtocolMapper mapper = ProtocolMapper.getInstance()
    .enable(ParserFeature.HEADER_CONSTANT)   // 校验包头 ##
    .enable(ParserFeature.FOOTER_CONSTANT);  // 校验包尾 \r\n
    // .disable(ParserFeature.HEADER_CONSTANT) // 关闭校验
```

| 特性 | 默认值 | 说明 |
|------|--------|------|
| `HEADER_CONSTANT` | ON | 校验包头必须为 `##` |
| `FOOTER_CONSTANT` | OFF | 校验包尾必须为 `<CR><LF>` |

### 校验特性（VerifyFeature）

```java
mapper.enable(VerifyFeature.DATA_CRC)   // 开启 CRC16 校验
             .enable(VerifyFeature.DATA_LEN_RANGE)  // 校验数据段长度
             .enable(VerifyFeature.USE_VERIFICATION); // 启用字段校验
```

| 特性 | 默认值 | 说明 |
|------|--------|------|
| `DATA_LEN_RANGE` | ON | 校验数据段长度 0~1024 |
| `DATA_CRC` | **OFF** | 校验 CRC16（⚠️ 生产环境建议开启） |
| `USE_VERIFICATION` | ON | 启用 Jakarta Validation 字段校验 |
| `THROW_ERROR_VERIFICATION_FAILED` | ON | 校验失败时抛出异常 |
| `STRICT_2025` | OFF | 2025 严格语法、字面宽度、已知字段与明确命令参数校验 |

表中默认值指显式调用 `enableDefaultParserFeatures()` / `enableDefaultVerifyFeatures()` 后启用的默认组合；新建 Mapper 不会自行启用全部默认组合。2025 接入推荐直接调用 `new ProtocolMapper().enableStrict2025()`，同时开启默认组合、CRC、包头与包尾检查。

### 段解析特性（SegmentParserFeature）

```java
mapper.enable(SegmentParserFeature.ALLOW_ISOLATED_KEY)
             .enable(SegmentParserFeature.IGNORE_INVAILD_SYMBOL);
```

### 数据校验（Jakarta Validation）

库内置声明式字段校验，并提供显式开启的 2025 严格校验，支持按字段、按版本、按模式分组执行：

```java
// 解析时自动校验（USE_VERIFICATION=true）
Data data = mapper.readData(packet);

// 手动校验
Data invalidData = new Data().setSt("123");  // ST 最多 2 位，将触发校验失败
Set<ConstraintViolation<Data>> violations = new ProtocolFactory().validator().validate(invalidData, jakarta.validation.groups.Default.class, VersionGroup.V2017.class);
```

支持的校验注解：

| 注解 | 说明 | 示例 |
|------|------|------|
| `@FieldC` | 字符数组（长度） | `@FieldC(field="ST", value=@CharArray(len=2))` |
| `@FieldN` | 数字数组（整数+小数位） | `@FieldN(field="Flag", value=@NumberArray(integer=3))` |
| `@FieldValidDate` | 日期格式 | `@FieldValidDate(field="QN", value=@Date(format="yyy..."))` |
| `@FieldMissing` | 字段缺失校验 | |
| `@FieldPattern` | 正则模式校验 | |
| `@ValueRange` | 取值范围校验 | |

---

## 示例模块

`example` 模块是一个基于 **Spring Boot + Netty** 的监控中心接收服务器，仅供演示接收流程；不属于核心库的运行依赖。

该示例按行拆分明文报文，并仅演示实时值入库。SM4 密文必须使用核心库的长度分帧接口，不能直接交给这个明文管道。数据库、会话及定时转表代码仅为示例业务，不代表核心库的运行要求。

**启动类**：`com.github.guocay.hj212.MonitorCenterApplication.java`

**核心处理类**：`com.github.guocay.hj212.business.service.MonitorService.java`

**处理流程**：

```
Netty Server (TCP)
  │
  ├─ LineBasedFrameDecoder    ← 按 \r\n 拆帧（防粘包）
  ├─ StringDecoder            ← 字节 → 字符串
  │
  └─ NettyServerHandler
       └─ MonitorService
            ├─ ProtocolMapper.readData()    ← 解析 HJ212 报文
            ├─ 提取污染因子数据
            ├─ 写入数据库（竖表）
            ├─ 根据 Flag 判断是否需要应答
            └─ 构建并返回应答报文
```

**数据存储**：

| 类 | 说明 |
|----|------|
| `MonitorFactorPo` | 竖表存储（根据污染物编码映射列） |
| `MonitorErrorPo` | 异常数据记录 |
| `Hj212TransTask` | 定时任务：竖表 → 横表转换 |

---

## 目录结构

```
hj212-protocol
├── example/                       示例代码
│   └── src/main/java
│       └── com/github/guocay/hj212
│           ├── business/service   MonitorService（核心处理逻辑）
│           ├── netty              Netty TCP 服务器
│           └── MonitorCenterApplication.java
├── protocol/                      协议核心库
│   └── src/main/java
│       └── com/github/guocay/hj212
│           ├── core               ProtocolMapper / Parser / Generator
│           │   ├── converter      DataConverter / 模型 ↔ Map 互转
│           │   ├── decode         反序列化器
│           │   ├── encode         序列化器
│           │   ├── feature        特性位定义
│           │   └── validator      校验注解 / 校验器
│           ├── model              数据模型 / 校验模型
│           │   └── verify         ProtocolMap / DataElement
│           ├── segment            底层段解析引擎
│           │   ├── core           SegmentParser / SegmentGenerator
│           │   ├── config         Configurator 接口
│           │   ├── decode         SegmentDeserializer
│           │   └── encode         SegmentSerializer
│           └── exception          协议异常 / 段异常
├── pom.xml
└── README.md
```

## HJ 212—2025 编解码接口

```java
ProtocolMapper mapper = new ProtocolMapper().enableStrict2025();
Data data = mapper.readData(packetBytes);
byte[] plain = mapper.writeDataAsBytes(data);
byte[] encrypted = mapper.writeDataAsEncryptedBytes(data, deviceKey);
Data restored = mapper.readEncryptedData(encrypted, deviceKey);
```

`deviceKey` 是调用方提供的 16 字节密钥。密文是原始二进制，不能转成 UTF-8 字符串或按换行拆帧。流式读取使用 `ProtocolFrame.read(input)`，或 `readEncryptedData(input, deviceKey)`；后者只消费一帧，不关闭输入流。

字符型监测值使用 `Pollution.getRtdText()` / `setRtdText(String)`；已有数值调用 `getRtd()` / `setRtd(BigDecimal)` 保留。更多示例和校验范围见 [2025 编解码使用说明](docs/HJ212-2025编解码使用说明.md)。

## 构建与测试

在项目根目录执行：

```shell
mvn test
```

当前 3368 项测试通过：核心模块 3365 项，独立示例模块 3 项。覆盖标准固定 CRC/SM4 样例、三个版本往返、严格字段校验、字符型值、扩展字段及二进制分帧。测试运行环境为 OpenJDK 17、Maven 3.10.0；尚未在 Java 8 运行时验证，也不代表真实设备互操作认证。

## 许可证

[MIT](LICENSE)

## 作者

| 作者 | 贡献 |
|------|------|
| [aCay (GuoBay)](https://github.com/guocay) | 项目创建，HJ/T 212-2005 / HJ 212-2017 支持 |
| Charles Cheng | HJ 212-2025 版本适配 |

---

> 参考：[污染物在线监控（监测）系统数据传输标准 (HJ 212-2017)](https://www.mee.gov.cn/ywgz/fgbz/bz/bzwb/other/qt/201706/t20170608_415697.shtml) ｜ [HJ 212-2025](https://www.mee.gov.cn/ywgz/fgbz/bz/bzwb/jcffbz/202507/t20250711_1123257.shtml)

附录 C 的 63 张表、188 条命令/应答样例，以及 69 组字段规则和接口一致性结果，见 [字段与附录 C 回归覆盖](docs/HJ212-2025回归覆盖说明.md)。
