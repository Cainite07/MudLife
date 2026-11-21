<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="88" alt="江大生活图标">
</p>

<h1 align="center">江大生活 · Jiangda Life</h1>

<p align="center">面向江苏大学学生的 Android 应用，集成校园卡、课表与考试、生活账单和宿舍服务。</p>

<p align="center">
  <a href="https://gitee.com/cainite/JiangdaLife/releases">Gitee 下载</a> ·
  <a href="https://github.com/Cainite07/JiangdaLife/releases">GitHub 下载</a> ·
  <a href="CHANGELOG.md">更新记录</a> ·
  <a href="https://github.com/Cainite07/JiangdaLife/issues">问题反馈</a>
</p>

## 主要功能

| 页面 | 功能 |
| --- | --- |
| 首页 | 查看校园卡余额和付款码，扫码使用饮水机，查询宿舍电费，查看洗浴和吹风机使用码。 |
| 课表 | 从江苏大学本科教务同步课表和考试安排，支持手动编辑课程、离线查看和「今日课表」桌面小组件。 |
| 账单 | 查看校园卡流水和饮水、洗浴、吹风机消费，按月份与类别浏览，在月报中查看每日支出和分类占比。 |
| 我的 | 分别管理学校、趣智校园和趣智后勤热水账号，设置外观与触感，检查应用更新。 |

## 安装与更新

当前正式版为 **v1.5.4**，支持 **Android 8.0 及以上的 ARM64 设备**。GitHub 和 Gitee 提供相同的 APK 与 SHA-256 校验文件。可覆盖安装签名相同的更高版本，保留登录信息和本地数据。应用内可在「我的 → 检查更新」查看新版本。

## 开发

项目使用 Kotlin 和 Jetpack Compose。使用 JDK 21 和 Android SDK 36 构建调试版并运行单元测试。

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

正式包构建、签名与双平台发行见 [发布指南](docs/RELEASING.md)。提交问题时请附应用版本、Android 版本和复现步骤，截图请遮盖账号、付款码和使用码。

项目采用 [MIT 许可证](LICENSE)。
