# 01 数据模型、原始发布时间与安全迁移

## 阶段目标

建立后续功能的数据基础，但不加入刷新按钮、计数、高亮或分割线。完成后交付一份行为基本等同原版、数据库可安全从 v7 升到 v8 的 APK。

## 1. 开始前基线

- 确认工作区已由项目所有者回退到目标分支，记录 `git status`。
- 记录当前 schema、Gradle 任务和基线 Debug 构建结果。
- 不运行会覆盖现有 DAO 的全量代码生成；当前 `daogenerator` 版本定义若落后于运行时，应先修复或采用独立表管理类。

## 2. schema v8

新增三张表，并保证全新建库与 v7→v8 升级使用同一份定义：

### `SUBSCRIPTION_TAG_CACHE`

- `_id` 自增主键。
- `ACCOUNT_KEY`：`ipb_member_id`。
- `TAG_NAME`：规范化完整原始标签名。
- `SERVER_TAG_ID`、`WATCHED`、`HIDDEN`、`COLOR`、`WEIGHT`。
- `FIRST_SEEN_AT`、`SYNCED_AT`。
- 唯一键：`ACCOUNT_KEY + TAG_NAME`；账号列建索引。

### `FEED_CHECKPOINT`

- `ACCOUNT_KEY`。
- `SOURCE_TYPE`：`HOME`、`SUBSCRIPTION_AGGREGATE`、`QUICK_SEARCH`。
- `SOURCE_KEY`、`QUERY_SIGNATURE`。
- `PREVIOUS_TIME`、`CURRENT_TIME`。
- `PREVIOUS_GIDS`、`CURRENT_GIDS`。
- `UPDATED_AT`。
- 唯一键：账号、来源类型、来源 ID、查询签名。

### `SUBSCRIPTION_TAG_UPDATE_STATE`

- `ACCOUNT_KEY`、`TAG_NAME`、`QUERY_SIGNATURE`。
- `COUNT`、`COUNT_STATE`、`CHECKED_AT`。
- 状态为 `EXACT`、`LOWER_BOUND`、`UNKNOWN`。
- 唯一键：账号、标签、查询签名。

## 3. 安全迁移

- 将运行时 schema 从 v7 升为 v8。
- 在 `EhDB.upgradeDB()` 增加 v7 分支，只执行新表和索引的 `CREATE ... IF NOT EXISTS`。
- 禁止删除、重建、清空旧表；迁移失败要保留原库并记录错误。
- 全新安装创建三张表；重复执行创建逻辑不破坏数据。
- 为后续导入旧备份预留“表不存在则创建”的兼容入口。

## 4. `postedTimestamp`

- 在 `GalleryInfo` 增加 Unix 秒 `long postedTimestamp`。
- `gdata` 数字时间直接保存；现有显示日期由它格式化。
- HTML 文本日期用兼容解析回退；失败为 `0`。
- 更新 Parcelable、JSON、复制/转换逻辑，旧数据缺字段时默认为 `0`。
- `0` 不得参与检查点推进。
- 审查 CSV/位置敏感备份格式，避免添加字段后破坏旧数据读取。

## 5. 仓库基础 API

实现线程安全的数据仓库：

- 读取当前账号键和规范化标签。
- 事务替换当前账号标签镜像。
- 读取/推进检查点。
- 读取/替换标签计数。
- “检查点 + 全部计数”提供一次事务提交 API。
- 数据库 I/O 放后台单线程执行器；返回不可变集合或副本。
- 游客使用独立作用域，不能读取最近账号缓存。

标签规范化只统一大小写、首尾空白和连续空白；永远不使用翻译后的显示名。

## 6. 测试

- 全新 v8 建库包含表、索引和唯一约束。
- 真实 v7 fixture 升级后，下载、历史、收藏、书签等旧表行数和关键字段不变。
- 重复迁移不破坏数据。
- 多账号与游客隔离。
- 标签规范化。
- GID 集合稳定序列化/反序列化。
- 新旧 `GalleryInfo` JSON/Parcel 兼容。
- 日期解析失败安全返回 `0`。
- 事务故障不会留下半套检查点/计数。

## 7. 手机验收

- 安装启动，主页、详情、下载、历史、收藏正常。
- 登录、退出、重启不崩溃。
- 本阶段不应出现黄色标签、刷新计数或边界线。

## 8. APK 交付

```powershell
.\gradlew.bat app:testAppReleaseDebugUnitTest
.\gradlew.bat app:assembleAppReleaseDebug
Copy-Item app\build\outputs\apk\appRelease\debug\app-appRelease-debug.apk artifacts\step-01-data-migration.apk
Get-FileHash artifacts\step-01-data-migration.apk -Algorithm SHA256
```

## 完成标准

- v7→v8 为纯增量迁移，原数据不变。
- 三张表和原始发布时间可稳定读写。
- 数据仓库支持后续原子提交。
- 单测、构建和阶段 APK 均已交付。

