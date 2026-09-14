# 泥浆生活 (MudLife)

基于 Jetpack Compose 开发的校园水控与生活服务 Android 客户端，支持热水器、直饮水机控制以及吹风机等账单查询。

<p align="center">
  <img src="assets/showcase.jpg" alt="MudLife 界面预览" width="100%" />
</p>

<details>
<summary><b>🌙 点击展开查看：夜间深色模式与特色功能细节（共 6 张）</b></summary>
<br>

#### 夜间深色模式

| 1. 登录认证 | 2. 常用出水 | 3. 消费账单 | 4. 账号卡密 |
| :---: | :---: | :---: | :---: |
| <img src="assets/dark_login.jpg" width="220" /> | <img src="assets/dark_home.jpg" width="220" /> | <img src="assets/dark_bills.jpg" width="220" /> | <img src="assets/dark_account.jpg" width="220" /> |

#### 特色功能与弹窗

| 后勤洗浴码登录 | 吹风机使用码更换 |
| :---: | :---: |
| <img src="assets/dialog_logistics.jpg" width="260" /> | <img src="assets/dialog_dryer_code.jpg" width="260" /> |

</details>

---

## 主要功能

### 1. 全区全品类账单聚合
- 支持高校多项目账户自动寻址与并发查询；
- 兼容 `laundryBillDTO` 与 `thirdTradeMoney`，获取饮水机、吹风机与洗浴流水；
- 自动过滤充值记录，按消费时间去重排序。

### 2. 设备控制
- **直饮水机**：支持扫码开关，常用设备列表记录最近使用设备，支持左滑删除；
- **洗浴器**：支持控制开关，以及获取后勤洗澡使用码。

### 3. 运行诊断与日志导出
- 应用内置日志查看器（保留最新300条与本地2MB滚动文件）；
- 自动对手机号、密码等敏感数据脱敏；
- 集成 Android FileProvider，支持一键调起 QQ、微信等系统分享导出日志，或直接复制最新报错堆栈。

### 4. 界面与交互
- 基于 Material 3 半透明磨砂质感设计；
- 原生自适应系统深浅色外观切换；
- 常用直饮水机卡片支持左滑删除。

### 5. 稳定性保障
- 完善关阀订单校验与重试逻辑，防止并发或弱网下关水失效；
- 接口数据模型非空安全兜底，规避反序列化异常崩溃；
- 登录凭据基于 EncryptedSharedPreferences 加密存储，支持异常降级。

---

## 技术栈

* **语言 / 框架**：Kotlin 2.0.21 / Jetpack Compose
* **网络与推送**：Retrofit 2.9.0 + OkHttp 4.12.0 / Eclipse Paho MQTT
* **扫码识别**：CameraX + Google ML Kit 条码扫描
* **存储与系统**：EncryptedSharedPreferences (AES-256) / Android FileProvider

---

## 构建与下载

### 直接安装
从右侧 [Releases](https://github.com/Cainite07/MudLife/releases) 下载最新签名的 `MudLife-v1.3.12.apk`。

### 本地编译
需要 JDK 17+ 及 Android SDK 35/36：

```bash
./gradlew assembleRelease -x lintVitalRelease
```

---

*特别致敬 [yehu-imei/linyu](https://github.com/yehu-imei/linyu) 项目，感谢其在早期打下的开源基础。*

## 许可证

本项目基于 MIT License 开源。仅供学习交流与个人研究使用。
