# Edge Reader

![Build](https://github.com/yinxi900607-cloud/ibook/workflows/Build/badge.svg)

Edge Reader 是一款集成在 JetBrains IDE 右侧工具窗口中的本地电子书阅读插件。

打开右侧的 **Edge Reader** 即可阅读，收起后不会占用编辑区。它适合在编程间隙阅读 TXT、EPUB、PDF、Markdown 和本地 HTML，并自动保存章节、页码、滚动位置与缩放状态。

> 当前版本：`0.6.0`
>
> 当前目标平台：IntelliJ Platform `2026.2`（Build `262`）

## 主要功能

- 右侧可折叠 Tool Window，不创建独立窗口，不影响正常编程。
- 本地书架展示最近阅读、全部书籍、阅读进度和文件缺失状态。
- 自动保存阅读位置，关闭项目或重启当前 IDE 后可以继续阅读。
- 通过路径、快速指纹和后台完整哈希识别书籍，文件移动后重新选择仍可恢复记录。
- 支持目录跳转、全文搜索、书签、上一章/下一章和 PDF 翻页。
- 支持字体、字号、行距、段落间距、页边距和阅读主题设置。
- 阅读记录、书签、设置和缓存只保存在本机，不需要账号。

## 支持格式

| 格式 | 当前能力 |
| --- | --- |
| TXT | UTF-8、UTF-16 LE/BE、GB18030、GBK、Big5；自动编码探测、章节识别、分块读取、搜索和连续滚动 |
| EPUB | EPUB 2 NCX、EPUB 3 Navigation、OPF/Manifest/Spine、目录、封面、本地图片、基础 CSS、章节搜索和稳定位置恢复 |
| PDF | 按页后台渲染、目录、页码跳转、文字搜索、适应宽度、50%～400% 缩放和页码恢复 |
| Markdown | 标题目录、代码块、本地图片、链接、全文搜索和阅读进度 |
| HTML | 本地 HTML/XHTML、标题目录、搜索和阅读进度；自动清理脚本及危险内容 |

PDF 使用按需渲染，只缓存当前页和相邻页面，不会一次渲染整本文件。扫描版 PDF 仍可按页阅读，但当前版本不提供 OCR，因此无法搜索图片中的文字。

## 阅读进度

Edge Reader 使用本地 SQLite 保存书架、进度和书签。阅读位置变化后会进行防抖保存，并定期执行兜底保存。

- 同一本书在当前 IDE 的不同项目中共享进度。
- 关闭项目、隐藏工具窗口、切换书籍或退出 IDE 时会保存当前位置。
- TXT 保存字符偏移，EPUB 保存章节和文字偏移，PDF 保存页码、垂直位置与缩放设置。
- 书籍移动后，可以通过内容指纹重新关联原有记录。

不同 JetBrains 产品通常使用各自独立的 IDE System 目录。因此，同一台电脑上的 IntelliJ IDEA、PyCharm、GoLand、WebStorm 和 CLion **目前不会自动共享阅读进度**。该限制不会影响同一 IDE 内跨项目共享，当前版本也不会通过网络同步数据。

## 安装

### 从 ZIP 安装

1. 下载 `edge-reader-<version>.zip`，不要解压。
2. 打开 IDE 的 **Settings/Preferences → Plugins**。
3. 点击齿轮菜单，选择 **Install Plugin from Disk…**。
4. 选择插件 ZIP，并按 IDE 提示重启。
5. 点击右侧的 **Edge Reader**，或使用快捷键打开。

默认快捷键：

- macOS：<kbd>Option</kbd> + <kbd>R</kbd>
- Windows/Linux：<kbd>Alt</kbd> + <kbd>R</kbd>

所有动作都注册在 JetBrains Keymap 中，可以在 **Settings/Preferences → Keymap** 里重新绑定。

更完整的安装、升级和卸载说明请参阅 [INSTALL.md](INSTALL.md)。

## 当前兼容范围

本版本面向 IntelliJ Platform `2026.2`，使用 Java 25 构建，最低平台 Build 为 `262`。

- IntelliJ IDEA 2026.2
- 采用对应 262 平台版本的 GoLand、PyCharm、WebStorm 和 CLion

插件仅依赖 `com.intellij.modules.platform`，不依赖 Java、Python、Go 等语言插件。当前阶段优先保证新平台可用，暂不兼容 2024.x、2025.x 等旧版 IDE。

## 本地数据与安全

Edge Reader 不包含账号、广告、分析统计、云同步或网络书城，也不会向发布者上传书籍内容、阅读进度、搜索内容、文件路径或哈希。

默认数据位于当前 IDE 的 System 目录：

```text
<IDE-system>/edge-reader/
├── edge-reader.db
└── cache/
```

安全处理包括：

- EPUB 解压路径校验，防止 ZIP Slip。
- 限制 EPUB 文件大小、条目数量、单文件大小、总解压大小和异常压缩比例。
- 移除 HTML、Markdown 和 EPUB 中的脚本、iframe、object、embed、表单和事件属性。
- 默认阻止远程资源，只允许访问选定文档目录或插件安全缓存中的本地资源。
- 不在日志中记录电子书正文和完整书签内容。

完整的数据说明请参阅 [PRIVACY.md](PRIVACY.md)。

## 从源码构建

准备 Java 25，或者使用 IntelliJ IDEA 2026.2 自带的 JetBrains Runtime：

```shell
./gradlew clean test
./gradlew verifyPlugin
./gradlew buildPlugin
```

可安装插件包生成在：

```text
build/distributions/edge-reader-<version>.zip
```

如果需要使用本机已安装的 IDE 进行开发运行或验证：

```shell
./gradlew runIde -PedgeReaderLocalIdePath=/absolute/path/to/IDE/Contents
```

CI 和正式构建默认使用锁定版本的 IntelliJ IDEA 统一平台。

## 当前不包含

- AI 功能
- 账号和云同步
- 网络书城
- 广告和用户追踪
- DRM、Kindle DRM、MOBI、AZW3
- OCR
- DOC/DOCX
- 复杂笔记、高亮和批注系统

## 反馈问题

如果遇到无法打开文件、进度恢复异常或界面问题，请提交到 [GitHub Issues](https://github.com/yinxi900607-cloud/ibook/issues)。报告问题时可以附上 IDE 版本、插件版本、文件格式和错误日志，但请不要上传包含隐私或版权内容的完整电子书。

## 许可证

- Edge Reader 源码及原创资源：[LICENSE](LICENSE)
- 第三方开源组件：[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
- 隐私政策：[PRIVACY.md](PRIVACY.md)
