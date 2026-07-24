![Icon](fastlane/metadata/android/en-US/images/icon.png)

这是一个 E-Hentai Android 平台的浏览器。


An E-Hentai Application for Android.


# 下载
[GitHub](https://github.com/meidorei/Ehviewer_CN_SXJ_manbo/releases)

# 新增功能简介

- **上次看到这里**：记录画廊列表的上次浏览位置，再次进入时使用分割线标出新旧内容边界，方便从上次的位置继续浏览。
- **追更**：在画廊列表右侧菜单的“订阅”页一键检查已订阅标签的新内容，并显示更新进度、上次更新时间及各标签的未查看数量。
- **自动拼接中文**：开启后，普通搜索、订阅搜索、标签搜索和上传者搜索会自动添加中文语言条件；已指定语言时不会重复添加。
- **JM 号查询**：支持输入纯数字，查询漫画名称、封面、作者、标签和章节等资料。

# 追更

1. 登录账号并确保“我的标签”中已经设置需要订阅的标签。
2. 在画廊列表页面打开右侧菜单，切换到“订阅”。
3. 点击“更新”按钮检查订阅内容。
4. 更新过程中，按钮旁会依次显示“同步标签”“已扫描 N 页”和“正在保存”；完成后会显示上次更新时间，失败或取消时也会显示对应状态。
5. 每个订阅标签旁会显示累计未查看数量。未打开标签时，多次更新发现的数量会继续累加。
6. 打开订阅标签并成功加载第一页后，该标签会标记为已查看并清零数量；本次页面仍显示原来的“上次看到这里”，下次进入时分割线才移动。
7. 每个订阅标签分别保存查看位置；打开聚合页或其他标签不会改变它。

首次更新只建立追更基准，不会把历史内容算作未查看。旧版本没有逐标签查看记录，因此升级后第一次进入已有标签时会建立查看基线。

# 自动拼接中文

在左侧菜单中开启“自动拼接中文”后，普通搜索、订阅搜索、标签搜索和上传者搜索会自动附加中文语言条件。已经明确指定语言的搜索不会重复添加。

# JM号查询

1. 打开左侧菜单，点击“JM号查询”。
2. 点击“查询”后会显示漫画名称、封面、作者、标签、章节及服务器提供的其他资料。
3. 可以复制漫画名称，或点击“浏览器打开”前往当前 JM 网页。

JM 使用易变的内部接口。出现“JM服务暂时不可用”时，请先检查 VPN 和网络后重试；封面加载失败不会影响名称和文字资料。JM 查询使用独立的临时 Cookie，不会读取或修改 EH 登录状态，也不会保存查询历史。

# 登录与里站

## `igneous` 失效或显示 `mystery`

如果出现“错误的 igneous”、无法进入里站，或者身份 Cookie 中的 `igneous` 显示为 `mystery`，请按以下步骤重新登录：

1. 在应用中退出登录。
2. 连接稳定的欧美 VPN 节点，并在整个登录过程中保持同一个节点。
3. 重新打开应用，通过网页登录进入 E-Hentai 表站并完成登录。
4. 登录完成后，在“设置 → EH → 画廊站点”中切换到 `exhentai`。
5. 如果仍然失败，请再次退出登录，确认 VPN 节点后重复以上步骤。

`igneous` 可能会过期，过期后需要重新登录获取。身份 Cookie 属于账号敏感信息，请勿截图、公开或发送给他人。

## 加入手动刷新，目前需要关闭代理后使用




# 原作者仓库
### [常见问题汇总](https://github.com/xiaojieonly/Ehviewer_CN_SXJ/blob/BiLi_PC_Gamer/feedauthor/EhviewerIssue.md)

# Build

Windows

    > git clone https://github.com/xiaojieonly/Ehviewer_CN_SXJ.git
    > cd EhViewer
    > gradlew app:assembleDebug

Linux

    $ git clone https://github.com/xiaojieonly/Ehviewer_CN_SXJ.git
    $ cd EhViewer
    $ ./gradlew app:assembleDebug

生成的 apk 文件在 app\build\outputs\apk 目录下
