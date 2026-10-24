<p align="center">
  <img src="assets/mudlife-icon-preview.png" width="104" alt="泥浆生活：校园之门图标" />
</p>

<h1 align="center">泥浆生活 · MudLife</h1>

<p align="center">校园日常，放在一起。</p>
<p align="center">江苏大学一卡通 · 趣智校园 · 趣智热水后勤</p>
<p align="center">
  <a href="https://gitee.com/cainite/MudLife/releases">Gitee 国内下载</a> ·
  <a href="https://github.com/Cainite07/MudLife/releases">GitHub Releases</a> ·
  <a href="CHANGELOG.md">更新记录</a> ·
  <a href="https://github.com/Cainite07/MudLife/issues">问题反馈</a>
</p>

![MudLife 功能概览](assets/promo/overview.png)

MudLife 是面向江苏大学日常生活的 Android 客户端，采用 Kotlin、Jetpack Compose 与 Material 3。它把校园卡付款、生活账单、常用饮水机和两组使用码整理到同一个应用中，保留温暖纸黄、墨绿配色与轻微触感反馈。

当前版本：**1.4.5** · Android **8.0 及以上** · 包名 `cn.mudlife.app`。

## 日常需要的，就放在顺手的位置

| 页面 | 可以做什么 |
| --- | --- |
| **首页** | 扫码使用饮水机，保存常用设备，进入洗浴与吹风机使用码 |
| **校园卡** | 出示在线付款码，选择可用校园卡与付款方式，按需开启提亮和保持亮屏 |
| **账单** | 查看校园卡余额、进入充值，按月份和饮食、洗澡、充值、其他分类浏览流水 |
| **我的** | 管理三个账号，查看电子学生卡，查询与缴纳学校本部宿舍电费，设置外观和触感，检查更新 |

账单统一采用**学校校园卡流水**，只统计支出；充值保留明细，退款与补助不计入消费。趣智饮水和独立钱包充值不混入校园卡账单，金额缺失或记录未完整同步时不显示不完整总额。

![常用入口、使用码与首次登录引导](assets/promo/daily.png)

## 三个账号，按需登录

| 账号 | 对应服务 |
| --- | --- |
| **学校账号** | 校园卡付款码、余额、账单、充值、电费和电子学生卡 |
| **趣智校园** | 饮水机与吹风机使用码 |
| **趣智热水后勤** | 洗浴使用码 |

首次安装会提供可跳过的账号引导。也可以在“我的 → 账号管理”随时登录；三个账号分别保存，不要求一次全部登录。使用码页同时展示洗浴码和吹风机码，未登录的一项提供对应入口。

学校登录、充值及付款依赖学校服务；需要微信的支付流程会进入相应环境，返回后按原订单核对结果。在线付款码依赖网络，应用不替代 NFC 校园卡。

## 校园卡账单，集中查看

<p align="center">
  <img src="assets/promo/bills.png" width="440" alt="余额、校园卡分类账单与支出统计示意" />
</p>

页面上方是余额与充值入口，下方是月份、支出和日期分组明细。已同步的学校账单保留本机缓存，月份选择按账号保存。

**图片与隐私：**以上宣传图依据当前页面布局制作，属于界面示意，全部金额、设备名和记录均为虚构演示数据；付款码和使用码使用占位展示，没有可用支付信息。图片不包含真实姓名、手机号、学号、头像、宿舍、设备标识或账号会话。

## 下载与覆盖升级

- 国内网络优先使用 [Gitee 发行版](https://gitee.com/cainite/MudLife/releases)，GitHub 用户可使用 [GitHub Releases](https://github.com/Cainite07/MudLife/releases)。
- 两个平台发布相同的正式 APK、版本号与 SHA-256 校验文件。应用内检查更新同时支持 Gitee 和 GitHub 来源，下载优先使用发布清单中的镜像。
- 正式版保持同一包名和固定签名，正常覆盖升级沿用本机登录与用户保存的数据。
- **历史签名说明：**1.3.18 起采用当前固定发行签名。旧签名的历史版本不能直接覆盖到新签名系列；切换前请自行保存需要保留的信息，卸载旧包会清除旧包的数据。

发行证书 SHA-256：

```text
00da3d63a98a2f73565b306c18c67ca2f4fe27fc2ae71599544f96593637c2df
```

## 开发与构建

- Kotlin 2.0.21 / Jetpack Compose / Material 3
- Retrofit + OkHttp / MQTT
- CameraX + ML Kit 扫码
- Android SDK 36，JDK 17 及以上

```bash
# 本地开发与单元测试
./gradlew testDebugUnitTest assembleDebug

# 正式发行；需要维护者已有的固定签名配置
./gradlew assembleRelease
```

正式私钥与凭据不在仓库内。发行构建会检查固定证书身份，不会自动创建或替换密钥；本地配置见 [签名说明](signing/README.md)。

宣传图仅使用演示数据，由基础页面示意经 AI 润色完成；设计提示见 [宣传图说明](assets/promo/prompts.md)。`python scripts/render_promos.py` 可重新生成基础稿至构建目录，需要 Pillow 与 Microsoft YaHei 字体，不覆盖已选定的成图。双平台发布流程见 [发布说明](docs/RELEASING.md)。

## 反馈与许可

反馈问题时，请提供版本号、设备系统、操作步骤和错误提示；截图中请遮盖账号、付款码、使用码及其他个人信息。

本项目采用 [MIT License](LICENSE)。
