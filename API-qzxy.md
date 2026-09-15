# 趣智校园第三方客户端开发指南

本文档介绍如何通过趣智校园 API 开发自己的第三方校园热水器客户端。基于泥浆生活 (MudLife) 项目的逆向工程与工程实践成果。

---

## 目录

1. [API 概览](#1-api-概览)
2. [认证机制](#2-认证机制)
3. [密码加密](#3-密码加密)
4. [API 接口详解](#4-api-接口详解)
5. [MQTT 实时推送](#5-mqtt-实时推送)
6. [蓝牙设备发现](#6-蓝牙设备发现)
7. [完整业务流程](#7-完整业务流程)
8. [数据模型参考](#8-数据模型参考)
9. [注意事项与踩坑记录](#9-注意事项与踩坑记录)

---

## 1. API 概览

### 基础信息

| 项 | 值 |
|---|---|
| Base URL | `https://v3-api.china-qzxy.cn` |
| 协议 | HTTPS |
| 数据格式 | JSON（GET 请求参数在 URL 中，POST 请求为 form-urlencoded） |
| 认证方式 | 基于 loginCode 的会话认证 |

### 接口列表

| 接口 | 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|---|
| 登录 | POST | `/user/login` | ❌ | 获取 loginCode 等认证信息 |
| 钱包余额 | GET | `/account/wallet` | ✅ | 获取趣智校园钱包余额 |
| 设备信息 | GET | `/device/info/mac` | ✅ | 通过 MAC 地址获取设备详情 |
| 开始洗澡 | POST | `/order/tcpDevice/downRate/rateOrder` | ✅ | 开启热水器 |
| 开阀结果确认 | POST | `/order/tcpDevice/query/downRateResult` | ✅ | 确认开阀是否成功（v1.2.0 新增） |
| 停止洗澡 | POST | `/order/tcpDevice/closeOrder` | ✅ | 关闭热水器 |
| 关阀结果确认 | POST | `/order/tcpDevice/closeOrder/result/query` | ✅ | 确认关阀是否成功（v1.2.0 新增） |
| 消费结果查询 | POST | `/order/consumeOrder/result/query` | ✅ | 查询消费结算结果 |
| 查询进行中 | POST | `/order/tcpDevice/query/rateOrder/using` | ✅ | 查询设备是否有进行中的订单 |
| 账单列表 | GET | `/order/query/account/bill/list` | ✅ | 获取月度账单 |
| 账单详情 | GET | `/order/query/account/bill/detail` | ✅ | 获取单笔账单详情 |
| 获取使用码 | GET | `/account/useCode/new` | ✅ | 获取当前使用码 |
| 生成使用码 | POST | `/account/useCode/new/generate` | ✅ | 生成新的使用码 |
| 使用码开关 | POST | `/account/useCode/new/status/update` | ✅ | 开启/关闭使用码 |
| 发送短信验证码 | GET | `/user/verification/code/get` | ❌ | 发送验证码，需 secret |
| 短信验证码登录 | POST | `/user/registerAndLogin` | ❌ | 用验证码注册/登录 |

---

## 2. 认证机制

### 登录流程

```
POST /user/login
Content-Type: application/x-www-form-urlencoded

telephone=13800001111
&password=A1B2C3D4E5          ← MD5 后取后 10 位大写
&phoneSystem=android
&type=0
&version=6.5.24
```

### 登录响应

```json
{
  "success": true,
  "errorCode": 0,
  "errorMessage": "成功",
  "data": {
    "userId": 12345678,
    "telephone": "13800001111",
    "loginCode": "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6",
    "tags": "alLyrelease,999",
    "hasPassword": true,
    "userAccount": {
      "projectId": 999,
      "accountId": 12345,
      "userId": 12345678,
      "name": "张三",
      "accountRealMoney": 0.0
    }
  }
}
```

### 关键字段说明

| 字段 | 说明 | 用途 |
|---|---|---|
| `loginCode` | 会话令牌（32 位十六进制） | 后续所有认证请求都需要此值 |
| `userId` | 用户 ID | 认证参数 |
| `userAccount.accountId` | 账户 ID | 认证参数 |
| `userAccount.projectId` | 项目/学校 ID | 认证参数，不同学校值不同 |
| `userAccount.name` | 用户姓名 | 显示用 |

### 认证参数传递方式

**GET 请求**：认证参数作为 URL Query 参数传递

```
GET /account/wallet?loginCode=xxx&userId=xxx&accountId=xxx&projectId=xxx&telephone=xxx&phoneSystem=android&version=6.5.24
```

**POST 请求**：认证参数作为 Form 字段传递（与业务参数一起）

```
POST /order/tcpDevice/downRate/rateOrder
Content-Type: application/x-www-form-urlencoded

xfModel=0&snCode=xxx&loginCode=xxx&userId=xxx&accountId=xxx&projectId=xxx&telephone=xxx&telPhone=xxx&phoneSystem=android&version=6.5.24
```

### 完整认证参数 Map

```kotlin
fun authFields(): Map<String, String> = mapOf(
    "loginCode" to loginCode,
    "userId" to userId,
    "accountId" to accountId,
    "projectId" to projectId,
    "telephone" to telephone,
    "telPhone" to telephone,      // 注意：POST 请求中 telephone 和 telPhone 都需要
    "phoneSystem" to "android",
    "version" to "6.5.24"
)
```

> ⚠️ 注意：POST 请求中 `telephone` 和 `telPhone` 两个字段都需要传，值相同。这是趣智校园 API 的历史遗留设计。

### 挤号检测

当 loginCode 失效（在其他设备登录）时，API 会返回包含以下关键词的错误信息：
- "登录"、"token"、"失效"、"过期"、"认证"、"未登录"、"请重新"

HTTP 状态码 401/403 也表示会话失效。

---

## 3. 密码加密

趣智校园使用 **MD5 取后 10 位大写** 作为密码传输格式：

```kotlin
fun encryptPassword(password: String): String {
    val md5 = MessageDigest.getInstance("MD5")
        .digest(password.toByteArray())
        .joinToString("") { "%02x".format(it) }
    return if (md5.length >= 10) {
        md5.substring(md5.length - 10).uppercase()
    } else {
        md5.uppercase()
    }
}
```

**示例**：
- 输入密码：`mypassword`
- MD5 哈希：`34819d7beeabb9260a5c854bc85b3e44`
- 取后 10 位大写：`C854BC85B3`

> ⚠️ 这是趣智校园官方 App 的加密方式，安全性较低，但作为第三方客户端必须遵循。

---

## 4. API 接口详解

### 4.1 钱包余额

```
GET /account/wallet
```

**响应**：
```json
{
  "success": true,
  "data": {
    "accountRealMoney": 0.0,
    "accountGivenMoney": 0.0,
    "money": "0.000"
  }
}
```

| 字段 | 说明 |
|---|---|
| `money` | 总余额（字符串格式） |
| `accountRealMoney` | 真实充值金额 |
| `accountGivenMoney` | 赠送金额 |

### 4.2 设备信息

```
GET /device/info/mac?macAddress=AA:BB:CC:DD:EE:FF
```

**响应**：
```json
{
  "success": true,
  "data": {
    "deviceId": 18224,
    "deviceName": "热水器-学生公寓-1号楼-3层-301",
    "snCode": "QZXY20230001",
    "macAddress": "AA:BB:CC:DD:EE:FF",
    "withholdMoney": 2.0,
    "onlineStatusId": 1
  }
}
```

| 字段 | 说明 |
|---|---|
| `deviceId` | 设备 ID |
| `deviceName` | 设备名称（包含位置信息） |
| `snCode` | 设备序列号（控制设备的关键标识） |
| `macAddress` | MAC 地址 |
| `withholdMoney` | 预扣金额（元） |
| `onlineStatusId` | 在线状态 |

**设备名称格式**：`热水器-{校区}-{楼栋}-{楼层}-{房间}` 或 `洗手台{编号}-{校区}-{楼栋}-{楼层}-{房间}`

### 4.3 开始洗澡

```
POST /order/tcpDevice/downRate/rateOrder
Content-Type: application/x-www-form-urlencoded

xfModel=0&snCode=QZXY20230001&loginCode=xxx&userId=xxx&...
```

| 参数 | 说明 |
|---|---|
| `xfModel` | 消费模式，固定传 `0` |
| `snCode` | 设备序列号 |
| 认证参数 | 见认证机制 |

**响应**：
```json
{
  "success": true,
  "errorCode": 0,
  "errorMessage": "成功",
  "data": null
}
```

> ⚠️ 调用此接口后，设备不会立即返回 orderNo。需要通过 MQTT 推送或轮询 `queryUsing` 接口获取。

### 4.4 停止洗澡

```
POST /order/tcpDevice/closeOrder
Content-Type: application/x-www-form-urlencoded

snCode=QZXY20230001&orderNo=1234567&loginCode=xxx&userId=xxx&...
```

| 参数 | 说明 |
|---|---|
| `snCode` | 设备序列号 |
| `orderNo` | 订单号（从 queryUsing 或 MQTT 获取） |
| 认证参数 | 见认证机制 |

### 4.5 查询进行中的订单

```
POST /order/tcpDevice/query/rateOrder/using
Content-Type: application/x-www-form-urlencoded

xfModel=0&snCode=QZXY20230001&loginCode=xxx&userId=xxx&...
```

**响应（有进行中订单）**：
```json
{
  "success": true,
  "errorCode": 0,
  "data": {
    "orderNo": "1234567",
    "state": 1,
    "snCode": "QZXY20230001",
    "isOwner": true
  }
}
```

**响应（无进行中订单）**：
```json
{
  "success": true,
  "errorCode": 0,
  "data": {
    "orderNo": null,
    "isOwner": true
  }
}
```

**特殊 errorCode**：
- `307`：表示设备正在使用中（即使 `success` 为 false）

| 字段 | 说明 |
|---|---|
| `orderNo` | 订单号（null 表示无进行中订单） |
| `isOwner` | 是否为当前用户发起的订单 |
| `errorCode: 307` | 设备正在使用中 |

### 4.6 账单列表

```
GET /order/query/account/bill/list?month=2026-05&billRequestType=2
```

| 参数 | 说明 |
|---|---|
| `month` | 月份，格式 `yyyy-MM` |
| `billRequestType` | 账单类型，固定传 `2` |

**响应**：
```json
{
  "success": true,
  "data": [
    {
      "consumeBillDTO": {
        "orderId": "1234567",
        "consumeDate": "2026-05-30 10:08:42",
        "consumeMoney": "0.08",
        "description": "热水器:学生公寓-1号楼-3层-301洗手台"
      }
    }
  ]
}
```

### 4.7 使用码

**获取使用码**：
```
GET /account/useCode/new
```

**响应**：
```json
{
  "success": true,
  "data": {
    "useCode": "11930960",
    "useCodeStatus": 1,
    "useCodeRandom": "960",
    "resetAvailability": 0
  }
}
```

| 字段 | 说明 |
|---|---|
| `useCode` | 使用码（8 位数字） |
| `useCodeStatus` | 状态：`1` = 已开启，`0` = 已关闭 |
| `useCodeRandom` | 随机数部分（后三位，与手机号后三位相同） |

**开关使用码**：
```
POST /account/useCode/new/status/update
Content-Type: application/x-www-form-urlencoded

useCodeStatus=1&loginCode=xxx&userId=xxx&...
```

| 参数 | 说明 |
|---|---|
| `useCodeStatus` | `1` = 开启，`0` = 关闭 |

---

## 5. MQTT 实时推送

### 连接信息

| 项 | 值 |
|---|---|
| 服务器 | `tcp://47.107.37.60:1883` |
| 协议 | MQTT 3.1.1（明文 TCP，无 TLS） |
| 认证 | 无（匿名连接） |
| ClientId | 随机生成 |

### 订阅主题

开始洗澡后，需要订阅以下三个主题（`{phone}` 替换为用户手机号）：

| 主题 | 说明 |
|---|---|
| `app_downRate_{phone}` | 开始洗澡事件推送 |
| `app_shutdownOrder_{phone}` | 停止洗澡事件推送 |
| `app_uploadData_{phone}` | 实时消费金额推送 |

### 消息格式

```json
{
  "orderNo": "1234567",
  "consumeMoney": 0.08,
  "state": 1,
  "result": 0
}
```

| 字段 | 说明 |
|---|---|
| `orderNo` | 订单号（开始洗澡后通过此字段获取） |
| `consumeMoney` | 当前已消费金额（元） |
| `state` | 订单状态 |
| `result` | 操作结果 |

### 使用场景

1. **获取 orderNo**：调用 `downRate` 后，MQTT 会推送包含 `orderNo` 的消息
2. **实时更新余额**：`app_uploadData` 主题会推送 `consumeMoney` 更新
3. **HTTP 轮询兜底**：如果 MQTT 连接失败，应使用 HTTP 轮询 `queryUsing` 作为备选方案

---

## 6. 蓝牙设备发现

### BLE 扫描

趣智校园热水器通过 BLE（低功耗蓝牙）广播设备信息。

**设备名称过滤**：包含 `KLCXKJ-Water`（不区分大小写）

```kotlin
// Android BLE 扫描示例
val scanner = bluetoothAdapter.bluetoothLeScanner
val settings = ScanSettings.Builder()
    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
    .build()

scanner.startScan(null, settings, object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
        val name = result.device.name ?: return
        if (name.contains("KLCXKJ-Water", ignoreCase = true)) {
            // 发现热水器设备
            val mac = result.device.address   // MAC 地址
            val rssi = result.rssi            // 信号强度
        }
    }
})
```

### 信号强度参考

| RSSI 范围 | 信号等级 |
|---|---|
| ≥ -70 dBm | 强 |
| -70 ~ -85 dBm | 中 |
| < -85 dBm | 弱 |

### 设备发现流程

```
1. BLE 扫描 → 获取 MAC 地址
2. 调用 GET /device/info/mac?macAddress=xxx → 获取设备详情（snCode、名称等）
3. 调用 POST /order/tcpDevice/query/rateOrder/using → 检查是否有进行中订单
```

---

## 7. 完整业务流程

### 洗澡流程

```
用户打开 App
    │
    ├── BLE 扫描附近设备
    │       │
    │       └── 获取 MAC → 查询设备信息 → 获取 snCode
    │
    ├── 选择设备 → 显示设备详情弹窗
    │       │
    │       ├── 查询 queryUsing → 检查是否已有进行中订单
    │       │       │
    │       │       ├── 有订单 → 直接进入洗澡中界面（恢复订单）
    │       │       │
    │       │       └── 无订单 → 调用 downRate 开始洗澡
    │       │               │
    │       │               ├── 连接 MQTT 订阅推送
    │       │               ├── 进入洗澡中界面
    │       │               └── 轮询 queryUsing 获取 orderNo（最多 10 次，间隔 800ms）
    │       │
    │       └── 洗澡中界面
    │               │
    │               ├── 显示计时器（每秒更新）
    │               ├── 显示预扣金额
    │               ├── MQTT 推送更新消费金额
    │               ├── 每 30 秒轮询 queryUsing 检查订单状态
    │               │
    │               └── 用户点击"结束使用"
    │                       │
    │                       ├── 调用 closeOrder 停止洗澡
    │                       ├── 断开 MQTT
    │                       └── 返回主页
    │
    └── 使用码启动的设备（物理键盘操作）
            │
            └── 刷新时 queryUsing 发现进行中订单 → 自动添加到活跃列表
```

### 登录流程

```
用户输入手机号 + 密码
    │
    ├── 密码 MD5 加密（取后 10 位大写）
    │
    ├── POST /user/login
    │       │
    │       ├── 成功 → 保存 loginCode、userId、accountId、projectId
    │       │           → 进入主页
    │       │
    │       └── 失败 → 显示错误信息
    │
    └── 自动登录（已有 loginCode）
            │
            └── 从 SharedPreferences 恢复认证信息 → 进入主页
```

---

## 8. 数据模型参考

### 统一响应格式

所有 API 返回统一的 JSON 结构：

```json
{
  "success": true,
  "errorCode": 0,
  "errorMessage": "成功",
  "data": { ... }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `success` | Boolean | 请求是否成功 |
| `errorCode` | Int | 错误码，0 表示成功 |
| `errorMessage` | String? | 错误信息 |
| `msg` | String? | 备用错误信息字段 |
| `data` | T? | 业务数据（类型因接口而异） |

### 特殊 errorCode

| errorCode | 说明 |
|---|---|
| `0` | 成功 |
| `12` | 手机号或密码错误 |
| `307` | 设备正在使用中 |

---

## 9. 注意事项与踩坑记录

### 9.1 R8 混淆与 Gson 泛型

如果使用 Kotlin + R8 full mode + Retrofit suspend 函数，R8 会擦除 suspend 函数的泛型签名，导致 Gson 无法解析 `BaseResponse<T>` 的类型参数。

**解决方案**：
- Retrofit 接口返回 `Call<ResponseBody>`（非 suspend）
- 用 `suspendCancellableCoroutine` 桥接回调到协程
- 手动用 `JsonParser` 解析 JSON，用 `Class<T>` 反序列化 data 字段

### 9.2 POST 请求的双重 telephone 字段

POST 请求的认证参数中，`telephone` 和 `telPhone` 两个字段都需要传，值相同。遗漏任一字段可能导致认证失败。

### 9.3 orderNo 的异步获取

调用 `downRate` 后不会立即返回 `orderNo`。需要：
1. 连接 MQTT 等待推送（优先）
2. 或轮询 `queryUsing` 接口（兜底，建议间隔 800ms，最多 10 次）

### 9.4 MQTT 连接失败的处理

MQTT 连接可能因网络原因失败。应实现 HTTP 轮询兜底方案，确保即使 MQTT 不可用也能正常控制设备。

### 9.5 使用码启动设备的检测

用户可能在热水器物理键盘上输入使用码启动设备，此时 App 并不知道。需要在刷新设备列表时，对每个发现的设备调用 `queryUsing` 检查是否有进行中的订单。

### 9.6 挤号检测

趣智校园不支持多设备同时在线。当用户在另一台设备登录时，当前设备的 loginCode 会失效。API 会返回包含"登录"、"token"、"失效"等关键词的错误信息，或返回 HTTP 401/403。

### 9.7 一卡通余额

一卡通余额由易校园/小付宝系统管理（`compus.xiaofubao.com`），该 API 有签名保护（`sign` Header），签名算法在网易易盾加固的 native 层中，无法通过常规方式获取。如需显示一卡通余额，建议让用户手动输入。

---

## 附录：Retrofit 接口定义参考

```kotlin
interface QzxyService {

    @FormUrlEncoded
    @POST("/user/login")
    fun login(
        @Field("telephone") telephone: String,
        @Field("password") password: String,
        @Field("phoneSystem") phoneSystem: String = "android",
        @Field("type") type: Int = 0,
        @Field("version") version: String = "6.5.24"
    ): Call<ResponseBody>

    @GET("/account/wallet")
    fun getWallet(): Call<ResponseBody>

    @GET("/device/info/mac")
    fun getDeviceInfo(@Query("macAddress") mac: String): Call<ResponseBody>

    @FormUrlEncoded
    @POST("/order/tcpDevice/downRate/rateOrder")
    fun downRate(
        @Field("xfModel") xfModel: Int = 0,
        @Field("snCode") snCode: String,
        @FieldMap auth: Map<String, String>
    ): Call<ResponseBody>

    @FormUrlEncoded
    @POST("/order/tcpDevice/query/downRateResult")
    fun downRateResult(
        @Field("snCode") snCode: String,
        @FieldMap auth: Map<String, String>
    ): Call<ResponseBody>

    @FormUrlEncoded
    @POST("/order/tcpDevice/closeOrder")
    fun closeOrder(
        @Field("snCode") snCode: String,
        @Field("orderNo") orderNo: String,
        @FieldMap auth: Map<String, String>
    ): Call<ResponseBody>

    @FormUrlEncoded
    @POST("/order/tcpDevice/closeOrder/result/query")
    fun closeOrderResult(
        @Field("snCode") snCode: String,
        @Field("orderNo") orderNo: String,
        @FieldMap auth: Map<String, String>
    ): Call<ResponseBody>

    @FormUrlEncoded
    @POST("/order/consumeOrder/result/query")
    fun consumeOrderResult(
        @Field("snCode") snCode: String,
        @Field("orderNo") orderNo: String,
        @FieldMap auth: Map<String, String>
    ): Call<ResponseBody>

    @FormUrlEncoded
    @POST("/order/tcpDevice/query/rateOrder/using")
    fun queryUsing(
        @Field("xfModel") xfModel: Int = 0,
        @Field("snCode") snCode: String,
        @FieldMap auth: Map<String, String>
    ): Call<ResponseBody>

    @GET("/order/query/account/bill/list")
    fun getBillList(
        @Query("month") month: String,
        @Query("billRequestType") billRequestType: Int = 2
    ): Call<ResponseBody>

    @GET("/order/query/account/bill/detail")
    fun getBillDetail(
        @Query("orderId") orderId: String,
        @Query("consumeDate") consumeDate: String
    ): Call<ResponseBody>

    @FormUrlEncoded
    @POST("/account/useCode/new/status/update")
    fun updateUseCodeStatus(
        @Field("useCodeStatus") status: Int,
        @FieldMap auth: Map<String, String>
    ): Call<ResponseBody>

    @GET("/account/useCode/new")
    fun getUseCode(): Call<ResponseBody>

    @FormUrlEncoded
    @POST("/account/useCode/new/generate")
    fun generateUseCode(@FieldMap auth: Map<String, String>): Call<ResponseBody>

    // ── 短信验证码（secret 绑定账号，未完善） ──
    @GET("/user/verification/code/get")
    fun getVerificationCode(
        @Query("telephone") telephone: String,
        @Query("typeId") typeId: Int = 3,
        @Query("platform") platform: Int = 1,
        @Query("secret") secret: String
    ): Call<ResponseBody>

    @FormUrlEncoded
    @POST("/user/registerAndLogin")
    fun registerAndLogin(
        @Field("telephone") telephone: String,
        @Field("smsCode") smsCode: String,
        @Field("type") type: Int = 5,
        @Field("phoneSystem") phoneSystem: String = "android",
        @Field("version") version: String = "6.5.24"
    ): Call<ResponseBody>

    companion object {
        const val BASE_URL = "https://v3-api.china-qzxy.cn"
    }
}
```
