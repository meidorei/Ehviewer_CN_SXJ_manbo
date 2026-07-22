# 01 数据基础、安全迁移与共享状态契约

## 目标

把运行时数据库从 v7 安全升级到 v8，增加订阅缓存、Feed 检查点和标签更新状态，并为后续阶段提供线程安全仓库。本阶段不增加高亮、计数、刷新按钮或边界标志。

阶段 02 可以已经存在；实施本阶段不得覆盖或回退自动中文搜索代码。

## 开始门禁

- 记录 `git status --short`、当前 schema、Gradle 任务和基线构建结果。
- 标记工作区已有修改的归属，尤其是阶段 02 和临时 NDK 配置。
- 禁止运行会覆盖现有定制 DAO 的全量 greenDAO 生成。
- 准备一个脱敏的真实 v7 数据库 fixture；没有 fixture 时只能标记迁移测试“未完成”，不能宣称迁移安全。

## schema v8

所有业务唯一键列均设为 `NOT NULL`。文本可选值使用空串，禁止依赖 SQLite 对 `NULL` 唯一性的特殊行为。

### `SUBSCRIPTION_TAG_CACHE`

- `_id INTEGER PRIMARY KEY AUTOINCREMENT`
- `ACCOUNT_KEY TEXT NOT NULL`
- `TAG_NAME TEXT NOT NULL`
- `SERVER_TAG_ID TEXT NOT NULL DEFAULT ''`
- `WATCHED INTEGER NOT NULL`
- `HIDDEN INTEGER NOT NULL`
- `COLOR TEXT NOT NULL DEFAULT ''`
- `WEIGHT INTEGER NOT NULL DEFAULT 0`
- `FIRST_SEEN_AT INTEGER NOT NULL`
- `SYNCED_AT INTEGER NOT NULL`
- `UNIQUE(ACCOUNT_KEY, TAG_NAME)`
- `INDEX(ACCOUNT_KEY)`

### `FEED_CHECKPOINT`

- `ACCOUNT_KEY TEXT NOT NULL`
- `SOURCE_TYPE TEXT NOT NULL`：`HOME`、`SUBSCRIPTION_AGGREGATE`、`QUICK_SEARCH`
- `SOURCE_KEY TEXT NOT NULL`
- `QUERY_SIGNATURE TEXT NOT NULL`
- `PREVIOUS_TIME INTEGER NOT NULL DEFAULT 0`
- `CURRENT_TIME INTEGER NOT NULL DEFAULT 0`
- `PREVIOUS_GIDS TEXT NOT NULL DEFAULT ''`
- `CURRENT_GIDS TEXT NOT NULL DEFAULT ''`
- `UPDATED_AT INTEGER NOT NULL`
- `UNIQUE(ACCOUNT_KEY, SOURCE_TYPE, SOURCE_KEY, QUERY_SIGNATURE)`

`PREVIOUS_*` 表示本次成功推进前的边界，`CURRENT_*` 表示最新成功边界；一次推进必须在同一事务中完成旧值平移和新值写入。

### `SUBSCRIPTION_TAG_UPDATE_STATE`

- `ACCOUNT_KEY TEXT NOT NULL`
- `TAG_NAME TEXT NOT NULL`
- `QUERY_SIGNATURE TEXT NOT NULL`
- `COUNT INTEGER NOT NULL DEFAULT 0`
- `COUNT_STATE TEXT NOT NULL`：`EXACT`、`LOWER_BOUND`、`UNKNOWN`
- `CHECKED_AT INTEGER NOT NULL`
- `UNIQUE(ACCOUNT_KEY, TAG_NAME, QUERY_SIGNATURE)`

## 建表与迁移

- 建立唯一的 `SubscriptionSchema.createTables(db)`，全新建库和 v7→v8 都调用它。
- `EhDB.upgradeDB()` 增加 `case 7`，只调用上述幂等建表/索引逻辑。
- 将运行时 schema 版本改为 8，但保留自定义 `DBOpenHelper.onUpgrade()`；不得改用会 drop 表的 `DevOpenHelper`。
- 任意 SQL 失败时让事务失败并记录错误，不继续打开一个半迁移数据库。
- 重复调用建表方法必须安全。
- 为导入 v7 备份暴露同一幂等建表入口。

## `postedTimestamp`

- `GalleryInfo` 增加 Unix 秒 `long postedTimestamp`，未知为 `0`。
- gdata 的数字时间直接赋值；HTML 日期解析集中到兼容解析器，失败返回 `0`。
- 更新 JSON、Parcel、对象复制和下载对象转换。
- JSON 缺字段默认 `0`。
- Parcel 如存在跨进程/持久化旧数据读取，采用版本或 `dataAvail()` 兼容；不能假设所有旧 Parcel 都含新字段。
- CSV 新字段只能追加在末尾；读取器同时接受旧 20 列和新格式。不要借本阶段重写无关 CSV 行为。
- `postedTimestamp == 0` 永远不能推进或命中检查点。

## 仓库 API

建立单一 `SubscriptionRepository`，数据库 I/O 在后台单线程执行器执行：

- `getAccountKey()`：登录 ID 或固定 `guest`。
- `normalizeTagName(raw)`。
- `replaceTagSnapshot(accountKey, tags)`：事务替换单账号镜像。
- `loadSubscribedTagSet(accountKey)`：返回不可变副本。
- `readCheckpoint(key)`、`advanceCheckpoint(key, boundary)`。
- `readTagCounts(accountKey, signature)`。
- `replaceTagCounts(accountKey, signature, states)`。
- `commitAggregateUpdate(checkpoint, allCounts)`：一个事务提交。
- `deleteQuickSearchCheckpoints(accountKey, quickSearchId)`。

仓库方法不得隐式读取“最近登录账号”；账号键必须由调用者显式传入写操作。

## 序列化规则

- GID 集合排序、去重后序列化，确保相同集合得到稳定文本。
- 空集合用空串或 `[]` 的唯一规范形式。
- 解析损坏内容时返回受控错误，不静默推进边界。
- 查询签名使用 README 中定义的稳定契约；如果阶段 02 已有实现，应复用而不是再造一套。

## 自动化测试

- 全新 v8 建库的表、索引、默认值和唯一约束。
- 真实 v7 fixture 升级前后旧表行数、主键和关键字段一致。
- 重复执行建表/迁移不破坏数据。
- 任一迁移 SQL 故障不会清空原库。
- 多账号及游客隔离。
- 标签规范化和 GID 集合稳定序列化。
- JSON/Parcel/CSV 新旧兼容。
- 日期解析失败返回 `0`。
- “检查点+全部计数”故障原子回滚。

## 完成标准

- v7→v8 是纯增量迁移并有真实结构 fixture 证据。
- 三张新表可稳定读写，复合唯一键不会因 `NULL` 失效。
- 仓库支持后续原子更新和账号隔离。
- 阶段 02 行为未回退。
- 测试、构建和 `artifacts/step-01-data-foundation.apk` 已交付。
