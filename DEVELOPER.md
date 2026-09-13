# 趣智校园第三方客户端 — 开发者指南

## 前置条件

| 条件 | 说明 |
|---|---|
| 学校 | 项目使用趣智校园/KLCXKJ 热水器的学校（projectId 每所学校不同，需自行抓包获取） |
| 抓包工具 | Reqable（Magisk 模块可选）+ LSPosed + TrustMeAlready（绕过 SSL Pinning） |
| 开发环境 | Android Studio + JDK 21 + Kotlin + Jetpack Compose |

---

## 一、核心发现：App 不走手机 BLE

**趣智校园 App 全程通过 HTTP + 云端 4G 控制热水器，不通过手机蓝牙。**

热水器自带 4G 模块，手机发 HTTP 请求到云端，云端通过 4G 转发给热水器。

```
手机 → HTTP API (v3-api.china-qzxy.cn) → 云端服务器 → 4G → 热水器
手机 ← MQTT (47.107.37.60:1883) ← 订单状态推送
```

BLE 只用于扫描发现热水器（获取 MAC 地址），不用于控制。

---

## 二、认证体系

### 登录

```
POST /user/login
Body: telephone=手机号&password=加密密码&phoneSystem=android&type=0&version=6.5.24
```

密码算法：**MD5(明文) → 取末尾10字符 → 转大写**

返回：
```json
{
  "success": true,
  "data": {
    "userId": 12345678,
    "loginCode": "xxx",
    "userAccount": {"accountId": 12345, "name": "用户名", "projectId": 999}
  }
}
```

### 全局认证参数

所有请求需携带（GET 放 URL 参数，POST 放 Body）：

```
loginCode={token}&userId={uid}&accountId={aid}&projectId={pid}&telephone={phone}&phoneSystem=android&version=6.5.24
```

**loginCode 有效期长**，可持久化到本地存储（本项目用 EncryptedSharedPreferences 加密），下次启动自动恢复。

---

## 三、核心 API

| 功能 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 登录 | POST | `/user/login` | MD5 密码 |
| 钱包 | GET | `/account/wallet` | 趣智校园余额（通常为 0） |
| 设备信息 | GET | `/device/info/mac?macAddress=xx:xx:xx` | MAC → snCode/设备名/预扣金额 |
| **开始洗澡** | POST | `/order/tcpDevice/downRate/rateOrder` | Body: xfModel=0&snCode=xxx |
| 开阀结果确认 | POST | `/order/tcpDevice/query/downRateResult` | 确认开阀是否成功 |
| **停止洗澡** | POST | `/order/tcpDevice/closeOrder` | Body: snCode=xxx&orderNo=xxx |
| 关阀结果确认 | POST | `/order/tcpDevice/closeOrder/result/query` | 确认关阀是否成功 |
| 消费结果查询 | POST | `/order/consumeOrder/result/query` | 查询消费结算结果 |
| 查询进行中的订单 | POST | `/order/tcpDevice/query/rateOrder/using` | 有订单时返回 errorCode=307 |
| 账单列表 | GET | `/order/query/account/bill/list?month=2026-05&billRequestType=2` | |
| 账单详情 | GET | `/order/query/account/bill/detail?orderId=xxx&consumeDate=xxx` | |
| 使用码 | GET | `/account/useCode/new` | 获取当前使用码 |
| 生成使用码 | POST | `/account/useCode/new/generate` | |
| 使用码开关 | POST | `/account/useCode/new/status/update` | useCodeStatus: 1开/0关 |
| 发送短信验证码 | GET | `/user/verification/code/get` | 需 secret（绑定账号） |
| 验证码登录 | POST | `/user/registerAndLogin` | 用短信验证码登录 |

### 开始洗澡完整流程

1. BLE 扫描 → 发现 `KLCXKJ-Water` → 获取 MAC 地址
2. `GET /device/info/mac` → 获取 snCode、deviceName、withholdMoney
3. 连接 MQTT `tcp://47.107.37.60:1883`（无认证）
4. `POST /order/tcpDevice/downRate/rateOrder` (xfModel=0&snCode=xxx)
5. 等待 orderNo（MQTT `app_downRate_{手机号}` 推送，或 HTTP 轮询 `/query/rateOrder/using`）
6. 返回 errorCode=307 时表示订单进行中，data.orderNo 即订单号

### 停止洗澡

`POST /order/tcpDevice/closeOrder` (snCode=xxx&orderNo=xxx)

### 恢复进行中的订单

`POST /order/tcpDevice/query/rateOrder/using` + snCode。如果 errorCode=307，data.orderNo 即当前订单号，调用 closeOrder 停止。
**注意**：data.isOwner=true 说明订单属于当前账号，false 说明别人在使用。

---

## 四、MQTT 协议

| 项目 | 值 |
|---|---|
| Broker | `tcp://47.107.37.60:1883` |
| 加密 | 无（明文） |
| 认证 | 无 |
| 客户端库 | Eclipse Paho Android |

### 手机订阅 Topic（仅接收）

| Topic | 用途 |
|---|---|
| `app_downRate_{手机号}` | 订单创建通知，包含 orderNo |
| `app_shutdownOrder_{手机号}` | 订单停止通知 |
| `app_uploadData_{手机号}` | 消费数据上报（仅在订单结束时发送） |

**注意**：MQTT 推送在订单结束才发送消费数据，中间扣费过程不推送。官方 App 也不显示实时扣费。

---

## 五、BLE 扫描

- 设备名：`KLCXKJ-Water`
- MAC 前缀：`C4:7F:0E`
- 厂商：凯路创新科技

扫描到设备后，只用 MAC 地址去查 HTTP API 获取设备信息。**不需要连接 GATT、不需要写 FF02。**

---

## 六、一卡通余额（无法直接调用）

一卡通余额在**易校园 App**（包名 `cn.com.yunma.school.app`），API 域名为 `compus.xiaofubao.com`。

接口格式：
```
POST /routeauth/auth/route/auth/user/getMultiCardMoney
Header: sign: {HMAC-SHA256(body + secret)}
Body: {"ymId":"xxx","nt":timestamp,"platform":"YUNMA_APP"}
```

**sign 算法在 native so 层，由百度安全 SDK 保护，网易易盾 + 反调试双重防护。** 尝试过 Frida、LSPosed、静态逆向等 7 种方案均失败。

### 变通方案

在钱包页手动输入初始余额，之后根据账单消费记录自动扣减（仅扣除填写时间后的新账单）。

---

## 七、开发建议

### 技术栈

| 组件 | 库 |
|---|---|
| HTTP | Retrofit + OkHttp |
| MQTT | Eclipse Paho Android |
| BLE | Android BluetoothLeScanner |
| UI | Jetpack Compose + Material3 |

### 关键坑点

1. **`/query/rateOrder/using` 返回 errorCode=307 时才是使用中**，不要只判断 success=true
2. **orderNo 在 MQTT 推送和 307 响应里**，不在 downRate 的返回中
3. **挤号检测**：服务器返回含"登录/token/失效/过期"的消息时，清除 token 跳回登录页
4. **开阀需确认**：downRate 后轮询 `/query/downRateResult` 确认开阀成功，避免设备离线时误以为已开
5. **热水器自动关停**：解析 downRate 响应的 `autoDisConTime`（秒）做倒计时；也可每隔 30 秒查 `/query/rateOrder/using` 兜底检测是否已被外部关闭
6. **计时器持久化**：用 Unix 时间戳存存储（加密），App 重启后计算差值恢复，不需要后台运行

### 不同学校的适配

不同学校的 projectId 不同，需要自行抓包获取。步骤如下：
1. 安装 Reqable + LSPosed + TrustMeAlready
2. 打开官方趣智校园 App 登录
3. 抓取 `/user/login` 响应，从 `userAccount.projectId` 获取自己学校的值
4. 将 projectId 替换到代码中

### 项目结构参考

```
api/
  QzxyService.kt     — Retrofit 接口定义
  NetworkModule.kt   — OkHttp 单例 + 全局拦截器
model/
  LoginModels.kt     — 登录/钱包/账单/开阀关阀结果数据类
  DeviceModels.kt    — 设备信息（含 displayName 格式化）
  ActiveOrder.kt     — 活跃订单
utils/
  PrefsHelper.kt     — 加密存储封装（EncryptedSharedPreferences）
  MqttManager.kt     — Paho MQTT 封装
  BluetoothScanner.kt — BLE 扫描
ui/
  LoginScreen.kt     — 登录页
  MainScreen.kt      — 主页（设备列表+扫码+寝室筛选）
  MainViewModel.kt   — 核心业务逻辑
  ShowerScreen.kt    — 洗澡中界面（含自动关停倒计时）
  WalletScreen.kt    — 钱包+账单
  UserScreen.kt      — 用户信息+使用码+绑定寝室
  QrScanActivity.kt  — 扫码界面（CameraX + ML Kit + 手电筒）
```

---

## 八、已知限制

| 限制 | 说明 |
|---|---|
| 实时扣费 | 服务器不提供中间消费数据 |
| 一卡通余额 | API 有 sign 签名保护，无法在第三方 App 中调用 |
| 多学校 | 需要修改 projectId 和 BLE 设备名过滤 |

---

## 九、致谢

- LSPosed + TrustMeAlready — HTTPS 抓包绕过
- Reqable — 流量分析
- nRF Connect — BLE 扫描验证
- SDKQZNetworkOffline (GitHub) — BLE 协议参考
