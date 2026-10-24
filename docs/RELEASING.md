# MudLife 双平台发布

维护者每次正式发布都同步 GitHub `Cainite07/MudLife` 和 Gitee `cainite/MudLife`。两个仓库使用相同源码提交、版本标签、正式 APK 和 SHA-256 校验文件；仓库同步不会自动同步发行附件。

## 一次性配置

- GitHub 使用本机 Git Credential Manager 中已有的仓库凭据。
- Gitee 使用有 `projects`、`user_info` 权限的私人令牌；运行 `python scripts/configure_gitee.py` 交互配置，令牌只保存到凭据管理器。GitHub 授权网页登录并不等于具有 Gitee API 发布凭据。
- 两个 Git remote 分别为 `origin=https://github.com/Cainite07/MudLife.git` 和 `gitee=https://gitee.com/cainite/MudLife.git`。
- 沿用现有固定签名，见 [签名说明](../signing/README.md)。不要重新生成私钥，不要将凭据或签名私钥加入仓库。

## 每次发行

1. 检查未提交改动和版本基线，完成必要修改及适度验证。
2. 同步 `app/build.gradle.kts`、`version.json`、README、CHANGELOG 的版本；更新清单中首先填写 Gitee 的对应 APK 下载地址。
3. 使用原签名运行单元测试及 `assembleRelease`，核对证书与包名。Windows 中文路径测试启动失败时，可按 CODEX.md 的已知环境方法构建。
4. 检查本次源码与宣传素材不含真实账号、二维码、使用码、会话、导出账单、私钥等信息。宣传图使用虚构演示数据并注明。
5. 将本次完整成果提交到 Git，使工作区干净。不要把 APK 或构建目录加入 Git。
6. 在仓库根目录运行 `python scripts/publish_release.py`。脚本先核对版本、包名、固定证书和凭据，再将相同标签及源码推送到两端，分别上传 APK 和 `SHA256SUMS.txt`。先核对发行附件，最后更新两端 main 和正式发行状态。
7. 检查输出的两端发行地址、源码提交、文件大小和 SHA-256；Gitee 下载核对显式禁用本机代理。脚本可重复运行以继续中断的同一版本；同名附件内容冲突时停止，不自动删除或覆盖。

脚本使用本机 Git Credential Manager，不将令牌写入文件或输出。生成的 APK 副本及校验文件放在忽略的 `app/build/outputs/dual-release/` 下。

## 应用内更新

更新检查并行读取 Gitee raw 与现有 GitHub/CDN 清单，选取最高有效版本；下载优先采用清单里的 Gitee 镜像，再尝试备用源。安装前核对包名、版本号与当前安装包签名。Gitee 的可用性与速度仍取决于用户网络及平台服务。

当前固定证书 SHA-256：`00da3d63a98a2f73565b306c18c67ca2f4fe27fc2ae71599544f96593637c2df`。

## GitHub Actions

push 和 pull request 自动运行单元测试。只有手动启用 `build_release` 才从 Secrets 恢复既有固定签名并生成发行包；缺少 Secrets 时发行构建失败，不创建替代密钥。两端发布由上述本机脚本完成，无需把 Gitee 令牌复制到源码或 GitHub Secrets。

个人构建代理应写在本机 `~/.gradle/gradle.properties`，不得把 `127.0.0.1` 等仅本机可用的代理地址写入项目的公共配置，否则 GitHub runner 无法下载 Gradle。发行源码以对应版本标签为准；发行后仅修复构建配置时，无需替换已经校验的 APK 或移动已发布标签。
