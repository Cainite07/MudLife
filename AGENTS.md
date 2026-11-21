# 江大生活项目规范

- 唯一对外品牌为「江大生活 / Jiangda Life」。中文与英文标签、多语言资源、界面、文档和宣传图统一使用正式名称。
- 仓库路径固定为 [GitHub](https://github.com/Cainite07/JiangdaLife) 与 [Gitee](https://gitee.com/cainite/JiangdaLife)。Gradle 根项目名为 `JiangdaLife`，Actions 工作流名为 `Build Jiangda Life APK`。
- 新发行两端均使用 `v版本号` 标签、`Jiangda Life v版本号` 标题、`JiangdaLife-版本号.apk` 与 `SHA256SUMS.txt`。用 `:app:packageReleaseApk` 生成分发 APK。发布前核对两端的提交、标签、文件及更新清单，核验公开下载后才将新版清单送到两端 `main`。
- 正式版保持 `cn.mudlife.app` 与现有固定发行证书。保留现有调试变体、组件和广播、Provider、持久化文件及键、加密凭据和存储身份。源码包路径、内部类名/主题、既有日志和更新缓存、签名输入、历史附件原名与兼容入口可以保留旧技术标识。
- 发布前搜索新旧品牌的大小写变体，检查文件名、界面文字、构建与发行配置、文档引用和图片内文字与二维码。历史素材请放在归档中，发布说明使用当前品牌。
- 发布路径、APK 命名或版本清单变化时，验证原有客户端可访问的清单、历史附件、两个新下载地址、校验值、包名、签名及版本比较。已经公开的标签和附件保持原有记录。
- [维护说明](CODEX.md) 记录业务约束，[双平台发布](docs/RELEASING.md) 记录操作流程。旧版本文档用于回查历史，当前发布遵循本页。
