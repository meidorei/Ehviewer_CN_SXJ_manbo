![Icon](fastlane/metadata/android/en-US/images/icon.png)


# 下载
[GitHub](https://github.com/meidorei/Ehviewer_CN_SXJ_manbo/releases)

# 新增功能简介

- **本地追更**：右侧原“订阅”页已改为本地“追更”，不依赖服务器订阅，也不要求登录。支持更新计数、排序、自动基线以及 JSON 导入和导出。
- **书签更新**：书签栏可以检查全部书签或长按检查单个书签，并显示每个书签的新内容数量。
- **更新算法**：追更和书签均可选择“全局中文扫描”或逐项检查；全局扫描使用独立的时间游标，遇到上次全局扫描位置即可停止，无法精确处理的书签会自动改为逐书签检查。
- **上次更新到这**：列表分割线记录上一次成功打开列表时的顶部边界。检查更新不会移动它，本次打开记录的新位置会在下一次打开时显示。
- **可控制的后台检查**：更新任务显示当前方式、来源、进度、扫描页数和完整更新时间，并支持暂停、继续和停止。
- **可调请求间隔**：高级设置可以把更新检查的搜索请求间隔设为 `1.0–10.0` 秒，默认并推荐 `3.2` 秒。
- **自动拼接中文**：开启后，普通搜索、订阅搜索、标签搜索和上传者搜索会自动添加中文语言条件；已指定语言时不会重复添加。
- **JM 号查询**：支持输入纯数字，查询漫画名称、封面、作者、标签和章节等资料。

# 下一步

- [x] 订阅有上限，把订阅改为自己实现的追更，更新追更后按更新数量排序

- [x] 为追更和书签增加全局扫描、逐项检查及任务暂停/停止

- [ ] 检查普通的按标签匹配，是否会在更新后把checkpiont像全局扫描一样同步推进到本轮全局顶部，还是像之前的标签匹配一样是在最后一次更新的漫画的位置

- [ ] 书签栏没有转换：

  ```
  f: -> female:
  m: -> male:
  有l-》language
  ```

- [ ] 全局扫描时匹配标签也有更新延迟，改掉

- [ ] 追更列表的全局扫描会失败，然后降级成按标签

- [ ] igneous开启代理后获取失败





------

# 原作者仓库

[xiaojieonly/Ehviewer_CN_SXJ: ehviewer，用爱发电，快乐前行](https://github.com/xiaojieonly/Ehviewer_CN_SXJ)

[常见问题汇总](https://github.com/xiaojieonly/Ehviewer_CN_SXJ/blob/BiLi_PC_Gamer/feedauthor/EhviewerIssue.md)

# Build

当前功能仅针对调试版构建和验证：

- applicationId：`com.xjs.ehviewer.debug`
- versionName：`2.0.2.4`
- versionCode：`121`
- 正式版 versionCode 仍为 `112`

Windows：

```powershell
.\gradlew.bat :app:assembleAppReleaseDebug
```

Linux：

```bash
./gradlew :app:assembleAppReleaseDebug
```

生成的 APK：

```text
app/build/outputs/apk/appRelease/debug/app-appRelease-debug.apk
```

------

# 更新书签时的匹配

追更和书签都能共享最多 30 页的全局中文扫描结果，但标签解析方式不同。下文用 `type` 代表受支持的命名空间，用 `xxx`、`aaa`、`bbb` 代表示例值。

支持的完整命名空间：

```text
artist group parody character female male misc language
cosplayer mixed other reclass
```

## 追更与书签的区别

| 项目 | 追更 | 书签 |
| --- | --- | --- |
| 标签来源 | 只接受标准 `namespace:value` 标签 | 保存的完整搜索条件 |
| 空格处理 | 整个字符串是一个标签，不按空格分词 | 普通/过滤搜索会按空格分词；标签模式不会 |
| 多词示例 | `type:aaa bbb` 可以直接匹配 | 普通搜索应写成 `type:"aaa bbb"`，标签模式可直接使用 `type:aaa bbb` |
| 降级方式 | 全局游标未覆盖时改为逐标签检查 | 复杂表达式或游标问题改为逐书签检查 |

追更入库和全局结果会执行相同的规范化：去除首尾空格、把连续空白压缩成一个空格并转成小写。全局扫描直接比较完整标签，因此 `type:aaa bbb` 不会被拆成 `type:aaa` 和 `bbb`；逐标签检查也会把它作为一个完整的 `MODE_TAG` 查询。

追更标签不要附加 `$`。追更不会剥离该符号，手动导入的 `type:xxx$` 可能无法与全局结果中的 `type:xxx` 相等。命名空间简写也不是合法追更标签，应使用完整名称。非中文 `language:*` 虽然目前可以加入追更，但与固定中文全局扫描存在条件冲突。

## 书签可以全局匹配的条件

以下书签标签可以从 `GalleryInfo` 精确匹配：

```text
type:xxx
type:xxx$
type:"aaa bbb"
type:"aaa bbb$"
type:xxx other:aaa
type:xxx -other:aaa
```

- 标准标签带不带 `$` 都按完整标签匹配。
- 多个标准标签支持 AND 组合，组合标签本身不会导致降级。
- 支持正向和负向标准标签。
- 标签模式会把整个查询值作为一个标签。
- 正向 uploader、分类、最低评分、最小页数和最大页数也能本地匹配。

当前书签匹配器只把 `l:`、`lang:` 转换为 `language:`，尚未处理其他简写。例如 `f:xxx`、`m:aaa` 以及包含它们的组合查询会降级；原因是简写未识别，不是组合标签不受支持。

普通/过滤搜索中的未加引号多词值也会降级：

```text
type:aaa bbb
```

它会被解释为标签 `type:aaa` 加全文关键词 `bbb`。如果本意是一个多词标签，应使用 `type:"aaa bbb"`，或者把查询保存为标签模式。若要自动兼容这种旧写法，应先用本地标签数据库确认完整多词标签确实存在。

以下书签必须逐书签检查：

- 裸全文关键词，如 `aaa`、`aaa$`；
- 标签与全文关键词混合，如 `type:xxx aaa`；
- `OR`、`~`、`*`、`?` 等复杂或模糊表达式；
- 负向 uploader 或不支持的命名空间；
- 热门、图片搜索、榜单等特殊模式；
- 明确指定非中文正向语言条件的查询。

## “自动降级”的含义

书签界面中的“自动降级”是以下三类数量的合计，并不都代表标签无法匹配：

1. 查询表达式不能本地精确处理。
2. 书签 checkpoint 早于共享全局游标，需要桥接旧边界。
3. 扫描 30 页后仍未覆盖共享全局游标。

全局扫描产生的桥接或游标降级队列在逐书签计数完成后，会把对应书签 checkpoint 一并推进到本轮共享全局顶部，并继续使用 GID 去重。这样低频标签不会因为自己的最新结果天然较旧而在下一轮重复桥接。用户主动选择的普通逐书签检查仍保留各书签自己的独立边界。
