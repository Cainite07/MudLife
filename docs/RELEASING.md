# Jiangda Life 双平台发布

维护者每次正式发布都同步 GitHub `Cainite07/JiangdaLife` 和 Gitee `cainite/JiangdaLife`。脚本同步两端的源码提交、版本标签、标题 `Jiangda Life v版本号`、正式 APK 和 SHA-256 校验文件。Git 只同步源码及标签，发行附件通过两端 API 分别上传。

## 一次性配置

- GitHub 使用本机 Git Credential Manager 中已有的仓库凭据。
- Gitee 使用有 `projects`、`user_info` 权限的私人令牌；运行 `python scripts/configure_gitee.py` 交互配置，令牌只保存到凭据管理器。GitHub 授权网页登录并不等于具有 Gitee API 发布凭据。
- 两个 Git remote 分别为 `origin=https://github.com/Cainite07/JiangdaLife.git` 和 `gitee=https://gitee.com/cainite/JiangdaLife.git`。
- 沿用现有固定签名，见 [签名说明](../signing/README.md)。不要重新生成私钥，不要将凭据或签名私钥加入仓库。

## 每次发行

1. 检查未提交改动和版本基线，完成必要修改及适度验证。
2. 同步 `app/build.gradle.kts`、`version.json`、README、CHANGELOG 的版本；`downloadUrl` 填写 GitHub 的对应 APK 地址，`mirrors` 第一项填写 Gitee 地址，应用优先使用国内源。
3. 使用原签名运行 `:app:packageReleaseApk`，核对证书、包名、ARM64 架构和内置离线扫码模型。AGP 中间输出为 `app/build/outputs/apk/release/app-release.apk`，正式分发输出为 `app/build/outputs/distribution/JiangdaLife-版本号.apk`。
4. 检查本次源码与宣传素材不含真实账号、二维码、使用码、会话、导出账单、私钥等信息。宣传图使用虚构演示数据并注明。
5. 将本次完整成果提交到 Git，使工作区干净。不要把 APK 或构建目录加入 Git。
6. 在仓库根目录运行 `python scripts/publish_release.py`。脚本先核对版本、包名、固定证书、凭据和两端 Git 历史，再同步标签，分别上传 APK 和 `SHA256SUMS.txt`。确认两端发行状态和附件字节一致后，将主分支及更新清单推到两端。
7. 用相同提交和同一个 APK 重跑即可恢复中断的发行。保留已上传的同名附件，发现内容冲突时停止。Gitee 附件验证显式禁用本机代理。

脚本从 Git Credential Manager 读取令牌。校验文件生成在 `app/build/outputs/distribution/`，与正式 APK 同目录。

## 公开说明

README、CHANGELOG、version.json 和发行页正文面向使用者，只描述功能变化、兼容性、下载与升级信息。协作对话、授权过程、个人设备信息及操作流水不写入公开说明。维护文档保留可复用的技术约束与发布流程，不追加聊天记录。

## 应用内更新

更新检查并行读取 Gitee raw 与 GitHub/CDN 的 `main/version.json`，选取最高有效版本。下载候选优先采用清单中的直接镜像，安装前核对包名、版本号与当前安装包签名。仓库路径变化后，分别检查两端历史直链和旧客户端已经写死的 raw/CDN 入口，确认旧客户端仍能读取持续更新的清单。

v1.5.4 已发布的附件名为 `MudLife-1.5.4.apk`。当前清单使用两端的新仓库路径，附件文件名、校验内容和标签沿用该版本的已发布记录。命名规则从下一次正式发行使用，新版本清单指向两端实际的 `JiangdaLife-版本号.apk`。

当前固定证书 SHA-256：`00da3d63a98a2f73565b306c18c67ca2f4fe27fc2ae71599544f96593637c2df`。

## GitHub Actions

push 和 pull request 自动运行单元测试。手动启用 `build_release` 时使用现有签名 Secrets 生成并上传发行 APK artifact。签名 Secrets 缺失时构建停止。两端发布由上述本机脚本完成，Gitee 令牌保留在本机凭据管理器。

个人构建代理应写在本机 `~/.gradle/gradle.properties`，不得把 `127.0.0.1` 等仅本机可用的代理地址写入项目的公共配置，否则 GitHub runner 无法下载 Gradle。发行源码以对应版本标签为准；发行后仅修复构建配置时，无需替换已经校验的 APK 或移动已发布标签。
