# 泥浆生活 (MudLife)

基于 Jetpack Compose 的校园水控与生活服务 Android 客户端，支持热水器、直饮水机及吹风机等公共设备控制与数据查询。

---

## 主要特性

### 1. 全区全品类账单聚合
- 支持高校多项目、多子账户动态寻址与并发查询；
- 兼容 `laundryBillDTO` 与 `thirdTradeMoney`，召回支付宝免密直扣的吹风机与公共洗衣机流水；
- 自动过滤充值记录，按消费时间高精度去重排序。

### 2. 设备控制与快捷操作
- 支持扫码与蓝牙低功耗（BLE）双模式寻址与开阀；
- 常用直饮水机与浴室设备支持左滑置顶或移除，提升日常使用效率；
- 提供高校后勤 8 位洗浴使用码展示与远程状态控制。

### 3. 应用内运行诊断与日志分享
- 内置 300 条环形内存日志与本地滚动存储（上限 2MB）；
- 敏感数据自动脱敏（手机号、Token、密码等）；
- 集成 Android `FileProvider`，支持一键调起微信、系统分享面板导出日志或复制报错堆栈。

### 4. 界面与交互设计
- 基于 Material 3 与半透明磨砂设计规范；
- 原生自适应系统深浅色外观切换；
- 洗浴使用码放大单行居中排布，适配洗漱远距离查看。

### 5. 底层稳定性加固
- 关阀订单状态与回调重试机制，防止并发或异常网络下关阀丢失；
- 数据模型非空安全兜底，规避反序列化异常；
- 登录凭据基于 AndroidX Security Crypto 加密存储与异常降级。

---

## 技术栈

* **语言 / 框架**：Kotlin 2.0.21 / Jetpack Compose
* **网络与通信**：Retrofit 2.9.0 + OkHttp 4.12.0 / Eclipse Paho MQTT
* **扫码与蓝牙**：CameraX + Google ML Kit / Android BLE API
* **安全与存储**：EncryptedSharedPreferences (AES-256) / Android FileProvider

---

## 构建与下载

### 直接安装
从本仓库右侧的 [Releases](https://github.com/Cainite07/MudLife/releases) 下载最新签名的 `泥浆生活.apk`。

### 本地编译
需要 JDK 17+ 及 Android SDK 35/36：

```bash
./gradlew assembleRelease -x lintVitalRelease
```

输出路径：`app/build/outputs/apk/release/`。

---

## 许可证

本项目基于 [MIT License](LICENSE) 开源。仅供学习交流与个人研究使用。
