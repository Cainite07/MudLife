# 江大生活发布指南

## 构建

使用 JDK 21、Android SDK 36 和 SDK Build Tools 35.0.0。通过 `ANDROID_HOME` 指定 Android SDK 路径，并使用项目的 Gradle Wrapper 运行任务。

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:packageReleaseApk
```

Windows PowerShell 使用以下命令构建和测试。

```powershell
.\gradlew.bat --no-daemon '-Pkotlin.compiler.execution.strategy=in-process' :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat --no-daemon '-Pkotlin.compiler.execution.strategy=in-process' :app:packageReleaseApk
```

调试包位于 `app/build/outputs/apk/debug/`。正式分发包位于 `app/build/outputs/distribution/JiangdaLife-版本号.apk`，适用于 Android 8.0 及以上的 ARM64 设备。

## 正式签名

正式版的应用 ID 为 `cn.mudlife.app`。构建与发布脚本以 [证书指纹](../signing/release-certificate.sha256) 校验原发行签名。兼容同签名版本的覆盖升级，应用 ID、组件身份、本地数据文件与加密存储应保持兼容。较早使用不同证书的安装需要单独处理。

本机签名配置位于用户目录的 `.android/mudlife-signing.properties`，也可通过环境变量提供。

| 本机属性 | 环境变量 |
| --- | --- |
| `KEYSTORE_FILE` | `MUDLIFE_KEYSTORE_FILE` |
| `KEYSTORE_PASSWORD` | `MUDLIFE_KEYSTORE_PASSWORD` |
| `KEY_ALIAS` | `MUDLIFE_KEY_ALIAS` |
| `KEY_PASSWORD` | `MUDLIFE_KEY_PASSWORD` |

GitHub Actions 在提交和 PR 上运行单元测试。维护者可通过 `build_release` 构建签名产物，使用原密钥对应的 `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。私钥、凭据和本机配置存放在仓库之外。

## 双平台发行

- Git 远端使用 `origin` 对应 [GitHub](https://github.com/Cainite07/JiangdaLife)，`gitee` 对应 [Gitee](https://gitee.com/cainite/JiangdaLife)，两端使用 HTTPS 克隆地址。
- 发布脚本通过 Git Credential Manager 读取授权。Gitee 使用具有 `projects` 和 `user_info` 权限的令牌，可运行 `python scripts/configure_gitee.py` 进行配置。

1. 同步 `app/build.gradle.kts`、`version.json`、README 和更新记录。新版本使用 `v版本号` 标签、`Jiangda Life v版本号` 标题和 `JiangdaLife-版本号.apk` 文件名。清单的主下载地址使用 GitHub，第一镜像使用 Gitee。
2. 用原签名构建正式包，验证安装、更新和升级后的数据读取。提交发行内容，确认工作区干净。
3. 运行 `python scripts/publish_release.py`。脚本核验 APK 包名、版本、签名、ARM64 库及离线扫码模型，向两端提交同一标签与 APK，并生成和上传 `SHA256SUMS.txt`。
4. 两端发行状态和公开附件校验通过后，脚本将更新清单推送至两端 `main`。若发布中断，用相同提交和相同 APK 重试。已有同名附件内容不一致时停止核对。

应用并行检查两端更新清单及备用源，安装前核对包名、版本号和签名。调整发行路径或附件名称时，验证旧客户端仍能访问清单和历史附件，并以已发布文件的实际名称为准。

## 维护约定

- 对外使用「江大生活 / Jiangda Life」，仓库和 Gradle 项目标识使用 `JiangdaLife`。
- 学校、趣智校园和趣智后勤热水分别登录。修改账号与缓存逻辑时，保持账号隔离，避免旧请求结果写入新会话。
- 校园卡扣款以学校流水为准，独立支付的设备消费作为补充。来源待核实的记录不计入支出，同订单避免重复统计。
- 设备关停以服务端确认的订单状态为准。公开图片及问题报告使用演示数据或遮盖个人资料、付款码和使用码。
