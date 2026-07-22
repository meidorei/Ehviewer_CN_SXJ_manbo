# 订阅追踪功能分阶段实施索引

这组计划从项目所有者已回退完成的干净目标分支开始。实施过程不得覆盖无关改动，也不得因 `daogenerator` 版本滞后而直接覆盖现有定制 DAO。

## 顺序与依赖

```text
01 数据模型、时间戳与安全迁移
 ├─> 02 全局自动中文搜索（可单独验收）
 └─> 03 本地订阅镜像与详情高亮（可单独验收）
       └─> 04 聚合更新与新增计数（同时依赖 02）
             └─> 05 Feed 检查点与边界标志
                   └─> 06 集成、导入导出与异常加固
                         └─> 07 全量测试与最终 APK
```

阶段 02 和 03 在阶段 01 完成后可并行；其余阶段按编号合并。每阶段都必须能独立构建并交付一份可安装 Debug APK，手机先验收当前功能，再进入下一阶段。

## 每阶段统一交付

1. 执行该阶段的自动化测试。
2. 执行 `./gradlew.bat app:assembleAppReleaseDebug`；任务名不符时先查 `app:tasks --all`。
3. 将 APK 复制到 `artifacts/step-XX-<name>.apk`。
4. 报告绝对路径、文件大小、SHA-256 和测试结果。
5. 提供阶段手机冒烟清单，区分自动验证与真实账号验证。
6. 上一阶段功能不得在下一份 APK 中回退。

Debug 包名为 `com.xjs.ehviewer.debug`，能与正式版共存，但数据目录独立。正式版数据库的 v7→v8 安全性必须依靠迁移测试证明。

## 文档列表

- [01 数据模型、时间戳与安全迁移](01-data-and-migration.md)
- [02 全局自动中文搜索](02-auto-chinese-search.md)
- [03 本地订阅镜像与详情标签高亮](03-subscription-cache-and-highlight.md)
- [04 安全聚合更新、标签新增计数与订阅页 UI](04-subscription-update-and-counts.md)
- [05 Feed 检查点与“上次更新到这里”](05-feed-checkpoints-and-marker.md)
- [06 生命周期、账号隔离、导入导出与异常加固](06-integration-hardening.md)
- [07 全量测试、性能审计与最终 APK](07-final-validation-and-apk.md)

