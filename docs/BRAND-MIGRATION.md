# 江大生活品牌迁移清单

迁移基线为 2026-11-21 检查到的 GitHub、Gitee `main`，提交 `b5dc031`，最新正式版 v1.5.4（45）。两端最新 Release 都叫 `MudLife v1.5.4`，APK 为 `MudLife-1.5.4.apk`，公开下载均为 11,142,541 字节，SHA-256 相同。GitHub 有 13 个历史 Release，Gitee 有 11 个，两端各有 13 个标签。两端公开 API 无待合并 PR，Git refs 只显示 `main`。另一个本地工作区有未提交账单修改。GitHub 已认证的仓库权限为 admin，Gitee 已认证为仓库所有者。两端目标仓库的认证 API 均返回 404。两端 API 未列出 Webhook，GitHub 未列出仓库级 Actions Secrets。

| 文件或范围 | 基线中的名称与作用 | 迁移决定 | 风险与兼容性 | 修改前核查 |
| --- | --- | --- | --- | --- |
| `settings.gradle.kts`、`app/build.gradle.kts` | 根项目 `MudLife`；AGP 默认 `app-release.apk`；旧签名变量与配置文件 | 根项目改为 `JiangdaLife`，单独生成正式命名的 APK；沿用签名输入 | 发布脚本和 CI 必须消费同一个正式输出；签名输入属于兼容接口 | 已读 |
| `app/src/{main,debug}/AndroidManifest.xml`、`res/values*/strings.xml` | 正式中英文已正确；Debug 为“泥浆生活·修复预览”；开场预览使用写死的中文标签 | 使用本地化标签覆盖对应构建类型，保留独立安装身份 | 正式、调试、引导预览的包名与组件身份保持一致 | 已读 |
| `ui/{UnifiedApp,UserScreen,HomeCampusServices,AccountGuide}.kt`、桌面小组件及相关资源 | 设置链接指向旧仓库；其他处有中文品牌字面量；小组件标签为功能名 | 统一仓库地址和品牌资源，维持小组件名称及组件身份 | 纯展示改动；已固定的小组件和快捷入口兼容 | 已搜 |
| `utils/{UpdateManager,UpdateDownloader}.kt`、对应测试及 `version.json` | 旧仓库 raw、四个 jsDelivr 入口、代理、推断 Gitee 附件路径、旧 User-Agent 和缓存名 | 新客户端使用目标入口，兼容旧清单与历史 APK 原名；保留更新缓存规则 | 改名前后实测现有 App 内的旧入口，保留可用升级路径 | 已读并探测旧入口 |
| `.github/workflows/build.yml`、`deploy.bat` | 旧工作流/Artifact 名、签名环境变量、本机部署输出 | 改展示和打包路径，Secrets 与签名变量保持现状 | GitHub Secrets 尚待维护者配置，正式本地验证可使用原签名 | 已读 |
| `scripts/{publish_release,configure_gitee}.py` | 旧 API 路径、Release 名、APK 名；脚本负责同步两端 Git 和发行附件 | 更新发布命名，核验附件与远端后再提交清单；仅下一个正式发行采用新 APK 名 | 部分发布可恢复，必须保留旧 tag 与现有附件 | 已读 |
| `README.md`、`CODEX.md`、`PROJECT.md`、`DEVELOPER.md`、`docs/RELEASING.md`、`API-qzxy.md`、`CHANGELOG.md`、`signing/README.md`、`LICENSE` | 现有品牌、旧仓库和 APK 规则、旧历史称呼、签名文件路径与版权署名；仓库根目录无 `AGENTS.md` | 建立统一规则，调整当前展示及操作路径；保留签名路径、准确历史记录及版权署名 | 规则冲突可能令未来发布重复使用旧品牌；历史文件名据实记录 | 已搜 |
| `assets/`、`scripts/render_promos.py`、`app/src/main/res/{mipmap-*,drawable}/` | 旧图标文件名；旧品牌写在横图、部分历史截图、旧长图及生成模板中；长图带下载二维码 | 迁移活跃素材和二维码，历史截图与原长图保留归档 | 逐图查看并复核引用及二维码目标 | 已查看 38 张仓库图片 |
| `app/src/*/java/cn/mudlife/`、主题与清单、存储/日志/更新缓存、测试数据 | 包名、namespace、类名、主题、组件广播、JS 结果键、`mudlife_prefs`、`mudlife_debug.log`、更新缓存 | 保留安装身份、组件契约、数据及内部标识；更新对外 User-Agent 和日志标签 | 升级数据、签名、小组件和既有缓存兼容优先 | 已搜 |
| 两端远端、Release、Actions、仓库设置 | 旧路径，两个远端共用历史，旧清单有多个入口 | 原地改名；核对 ID、refs、历史下载和旧客户端升级入口，失败时恢复可用路径 | 平台重定向规则各自实测，外部转贴的地址也依赖平台行为 | 基线已查，改名后待复查 |
