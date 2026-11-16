# 本地小说导入（TXT 与 EPUB）

`shared/media` 的本地小说导入契约与 TXT、EPUB 3 实现。全部代码在 `commonMain`，只依赖 Okio、Ksoup 与 kotlinx.serialization；不依赖 Compose 或 Android，可直接被书架、数据库和阅读器复用。TXT 与 EPUB 共用同一组 `NovelImport` / `NovelBlock` / `ReadingPosition` 模型，差异只在实现。

可直接复制的最小调用示例、支持范围、资源关闭与进度定位语义见 **[使用指南](usage.md)**。

## TXT 支持范围

| 项目 | 支持 |
| --- | --- |
| 容器格式 | 纯文本 `.txt`。ZIP（含改名的 EPUB）、PDF、gzip、UTF-32 与无 BOM 的 UTF-16 会被识别为不支持格式 |
| 编码 | UTF-8、UTF-8 BOM、UTF-16 LE BOM、UTF-16 BE BOM |
| 换行 | LF、CRLF 与单独的 CR；三者解码后都归一为 LF |
| 章节标题 | `第一章`/`第1章`/`第１章`/`第一节`/`第1節`/`第二回`/`第一卷`/`第二部`/`第三章`；`卷一`/`卷1`；`序`/`序章`/`序言`/`引子`/`楔子`/`前言`/`后记`/`终章`/`終章`/`尾声`/`跋`/`番外`/`番外篇`/`番外一`；以及 `第一章 标题`/`第一章：标题` 这种同一行带标题的写法 |
| 目录层级 | `卷`/`部`/`篇` 是 level 0 的结构标题，其余是 level 1 的可读章节；level 1 的 `parentId` 指向最近的 level 0 项 |
| 正文块 | 段落、分隔线、图片引用；TXT 没有图片时同样使用统一模型 |
| 补充平面字符 | 完整支持，包括 U+20BB7（𠮷）与 emoji（如 😀）的代理对 |
| 无章标题文件 | 全文作为一个可读章节返回，不丢内容 |
| 内嵌目录页 | 文件开头“仅列章名、无正文”的目录清单（`目录`/`目次`/`Contents` 标记可省略，允许 `目　录`/`目 录` 字间空白）会被剔除，不产生空章节；清单后的书名/作者页保留为卷首，紧接的真实卷标题保留 |

尚未支持：GBK/GB18030 等中文旧编码；无 BOM 的 UTF-16。这类字节按 NUL 分布识别不支持格式，并额外拒绝解码后出现 C0/C1 控制字符的情形（BOM-less UTF-16 误当 UTF-8 的典型产物），因此真实篇幅的中文小说会被拒绝而不是静默乱码；只有 1–2 个字符的极短文件仍无法与合法文本区分，可能按 UTF-8 解出无意义但无控制字符的内容。正文章节内容的分页缓存、图片字节解析亦未支持。

## API 概览

入口是一个函数，其余都是数据模型：

```kotlin
import dev.reader.media.novel.NovelImport
import dev.reader.media.novel.txt.openTxtNovel
import okio.FileSystem
import okio.Path.Companion.toPath

val novel: NovelImport = openTxtNovel(
    fileSystem = FileSystem.SYSTEM,        // 测试可注入自定义 FileSystem
    path = "/books/长夜行.txt".toPath(),
)

novel.metadata.title                      // 文件名去扩展名
novel.metadata.author                     // 前五行内的「作者：…」，否则 null
novel.encoding                            // TextEncoding.UTF_8 / UTF_16_LE / UTF_16_BE
novel.tableOfContents                     // List<TocEntry>，id 稳定，含 level 与 parentId

val chapter = novel.readChapter(novel.tableOfContents.first().id)
chapter.title                             // 目录标题
chapter.blocks                            // List<NovelBlock>
novel.close()
```

### 模型

- `NovelMetadata(title, author)`：TXT 无内嵌元数据，标题取文件名，作者取前五行中的 `作者：…` 行。
- `TocEntry(id, title, level, parentId)`：`id` 形如 `txt-` + 16 位十六进制。TXT 由「层级 + 同标题出现序号 + 标题」的 FNV-1a 计算，**不含文本偏移**（否则编辑靠前章节会让后面所有 id 变化、已存进度失效）；EPUB 由文档路径计算。**同一文件字节重复打开得到相同 id**，可存为阅读进度键。
- `NovelBlock`：
  - `Paragraph(text)`：段落。首部缩进已去除，换行由阅读器决定。折行判定按整章统计：缩进散文行占全部散文行 ≥ 1/4 视为**折行排版**（两行段落 50%、三行 33%、四行 25% 都覆盖），否则视为**逐行排版**。折行排版下，缩进行或句末标点后的行起新段，未结束句子的无缩进行并入上一段；逐行排版下每行一段。因此「缩进叙述 + 无缩进对白」逐行保留，零散缩进（低于 1/4）也不会把整章并成一段。
  - `Separator(style)`：`***`/`---`/`...` 一类的分隔线，`style` 区分星号、横线与点。
  - `Image(reference)`：`ImageReference(source, altText)`，`source` 是文件里原样的引用（如 `images/ferry.png`），解析路径与加载字节属于后续导入阶段。
- `NovelChapter(id, title, level, parentId, blocks)`：`readChapter` 的返回值。

### 章节识别规则

- 只识别位于行首、且数字/关键词后紧跟分隔符或行尾的短行（≤ 40 字符）。
- 标题行不重复进入正文章节。
- 保守优先：以标题词开头但含句末标点（`。`、`；`、`？`）的行按正文处理，例如「第一天他走了。」不会被切成章节。
- 第一个标题之前的非空文本作为「卷首」章节保留；若首行像书名（≤ 30 字符且无句末标点），目录项用它命名，否则用「卷首」。
- 内嵌目录页：只处理文件开头“连续标题且无正文”的前缀。取从首个标题开始、每条正文都不含散文的最长前缀；若该前缀与后文一段连续标题（≥2 条）同序重复，或单条非卷标题在后文重复且后者带正文，就丢弃这些**标题行**。前缀在遇到与首条相同的后文标题前先截断，因此目录清单里的卷标题不会连带删除正文中真实的同名卷。书名/作者页不是散文，作为卷首保留而非并入末章。`目录` 等标记词仅在文件首个标题前或已确认清单前的标签位置丢弃；出现在章节正文中的 `目录` 是内容，保留。真实章节、对白与正文行永不删除。

## 错误

`NovelImportException` 是可区分的密封类，`message` 都带文件路径与可定位上下文：

| 类型 | 触发 | 关键字段 |
| --- | --- | --- |
| `Encoding` | 字节不是合法 UTF-8/UTF-16 | `byteOffset`（相对整个文件）、`line`、`encoding` |
| `Empty` | 0 字节，或解码后没有任何可读文本 | `byteSize` |
| `UnsupportedFormat` | ZIP/PDF/gzip/UTF-32；以及 NUL 分布明显属于无 BOM UTF-16 的字节 | `format`、`detectedSignature` |
| `Io` | 打开或读取失败 | `phase`（`open` 或 `read`；读完后 close 失败会被吞掉，不会报 `close`）、`cause` |
| `NovelClosedException` | `close()` 后读取章节 | — |

编码错误与空文件、不支持格式互不混淆：空白文件不会报编码错误，改名成 `.txt` 的 EPUB 报 `UnsupportedFormat` 而不是 `Encoding`。

## 资源关闭规则

- `openTxtNovel` 在调用期间读完整个文件并立即关闭底层 source；**成功和所有失败路径都不会遗留文件句柄**。打开失败、读取失败、编码错误、格式不支持、空文件的关闭行为都有测试覆盖（`TxtResourceReleaseTest` 统计 source 的 open/close 次数）。
- 打开成功后不再持有文件句柄；`NovelImport.close()` 幂等，并**确定性释放**已解析章节缓存与章节正文（实例不再保留整本书文本），不再依赖 GC。若书架长期持有 `NovelImport`，调用 `close()` 后内存即回落。
- `close()` 之后 `readChapter` 抛 `NovelClosedException`；`metadata`、`encoding`、`tableOfContents` 是不可变快照，仍可读取，便于列表页展示已关闭的书。
- 异常路径无需额外清理：`openTxtNovel` 失败时不会返回半开对象。
- Kotlin `use` 在部分原生目标不可用，因此提供 `novel.useImport { … }`，行为等同 try/finally 关闭；block 抛异常时同样关闭（有测试覆盖）。

## 阅读进度与定位

`ReadingPosition`（`kotlinx.serialization` 可序列化）由六部分组成：

| 字段 | 说明 |
| --- | --- |
| `chapterId` | 目录项稳定 ID。TXT 用「层级 + 同标题出现序号 + 标题」哈希，**不含文本偏移**，因此前面的章节增删不会改变后面章节的 ID；EPUB 用文档路径 |
| `contentVersion` | 整本内容指纹（SHA-256 前 16 位十六进制），对任意字符变化敏感。TXT 由各章节 body 的**原始文本**顺序哈希（不做空白归一，因此插入空行改变分段也会变）；EPUB 由中央目录中 spine 文档的 path/size/CRC 哈希，保持懒加载。段落锚点指纹 `blockFingerprint` 则忽略空白差异，二者用途不同 |
| `blockIndex` | 章节 `blocks` 下标（含 Heading/Separator/Image） |
| `blockFingerprint` | 锚定段落的**空白归一**后文本指纹；重复段落共享指纹，靠 `blockIndex` 区分。归一让重新折行不影响定位，但也意味着仅空白变化不改变锚点 |
| `codePointOffset` | 段内 **Unicode 码点**偏移；emoji 与补充平面字符计 1 |
| `chapterIndex` | 保存时的章节序号，仅用于章节被删除时的相邻回退（旧数据为 -1） |

`positionOf(chapterId, blockIndex, codePointOffset)` 生成位置。若指定块不是段落（标题/分隔/图片），先前移到最近的后续段落；没有后续段落时回退到章节内任意首个段落；整章无段落时固定在块 0、偏移 0。EPUB 的 level-0 分组条目本身没有正文，对它保存位置会在解析时落到下一章的段落（`CHAPTER_START`），调用方应只对 level-1 章节保存进度。`resolvePosition(stored)` 返回 `PositionResolution`，其 `resolution` 依次为：

`EXACT` → `REANCHORED_BLOCK`（同章内插删，按指纹重定位）→ `PREVIOUS_PARAGRAPH`（锚定段落被删，取前一幸存段，
偏移收敛到段末）→ `NEXT_PARAGRAPH` → `NEIGHBOR_CHAPTER`（整章被删，按 `chapterIndex` 取相邻章节）→
`CHAPTER_START`。

码点工具在 `CodePoints`：`length` 计码点，`toStringIndex`/`toCodePointOffset` 在码点与 UTF-16 下标间转换，
`clamp` 把越界偏移收敛到段落范围，永不落在代理对中间。同一文件重复打开得到相同的 ID、版本与位置。

## 构建与测试

模块是独立 Gradle 项目根：

```bash
./gradlew -p shared/media --max-workers=1 jvmTest iosSimulatorArm64Test
```

测试只在 `commonTest` 写一份，JVM 与 iOS 模拟器各自真实读取 `fixtures/txt/` 下的二进制夹具。夹具由 `fixtures/txt/generate_fixtures.py` 以原始字节生成，用途见 `fixtures/README.md`。

## EPUB 支持范围

| 项目 | 支持 |
| --- | --- |
| 版本 | EPUB 3（nav、`properties="cover-image"`），并兼容 EPUB 2 的 `meta name="cover"` 封面约定 |
| 读取链路 | `META-INF/container.xml` → OPF → manifest/spine；ZIP 由 Okio `FileSystem.openZip` 只读访问 |
| 打开时读取 | 仅 container、OPF、`META-INF/encryption.xml` 与 nav（各受 `maxMetadataBytes` 限制）。章节 XHTML 与图片按需读取。唯一例外：**无可用 nav 时**由 spine 生成目录，会读每章 `<title>` 一次（见下文） |
| 正文顺序 | **严格按 spine**。`linear="no"` 的辅助项默认排除；过滤后为空则回退为全部 spine 项。manifest 顺序与 spine 不同时以 spine 为准 |
| 目录 | 优先 EPUB 3 nav：`properties` 含 `nav` 的 manifest 项中，`type` 属性（任意前缀绑定 OPS，如 `epub:type`/`ops:type`）含 `toc` 的 `<nav>`。**只**接受 toc，`landmarks`/`page-list` 不会被误当作目录。nav 锚点按文档映射到 spine 文档，标题来自锚点文本；nav 未提及的 spine 文档仍按 spine 顺序出现（标题取文件名），因此不会丢内容。nav 缺失、损坏或没有可用条目时由 spine 生成目录，标题取 XHTML 的 `<title>` 或首个标题元素 |
| 目录层级 | nav 中没有自身文档、只有子项的分组（如 `Volume I`）是 level 0，可读锚点是 level 1。层级按 nav **嵌套**确定：章节的 `parentId` 是其实际所在分组；不在任何分组内的章节 `parentId` 为 `null`。分组按位置区分，允许两个同名分组 |
| 元数据 | `dc:title`、全部 `dc:creator`（`metadata.authors`，`author` 取首个）、`dc:language`；封面优先 `properties="cover-image"`，兼容 `meta name="cover"` |
| 正文转换 | `h1`–`h6` → `Heading`（层级 1–6）；`p` 与块级容器 → `Paragraph`，容器内的裸文本（诗歌、信件、图注）也保留。块容器内的**行内元素**（`em`/`span`/`a`/`abbr`/`ruby`/`rt`/`rp` 等）并入同一段（`rp` 的回退括号丢弃），只有真正的块级元素（`p`/`div`/`h*`/`ul`/`table`/`hr` 等）才另起段落；`br` 在段内合并为空格；`hr` → `Separator`；本地 `img` → `Image`，且 `<a>/<span>/<li>` 包裹的图片同样识别，**包括块级位置**（`div`/`figure`/`body` 直接子级的 `a>img`，例如整页封面）；`script`/`style` 与远端图片被排除，绝不发起请求 |
| 路径解析 | 支持相对路径、`./`、`../`、百分号编码（含中文 UTF-8，如 `%E6%B8%A1`）、含空格文件名、`#fragment`；全部规范化为归档根内路径。拒绝 `../` 越出根、绝对路径、`http(s)://`、`data:`、`//host` 与非法百分号编码 |
| 图片/封面字节 | 通过 `readResource(path)` 按需返回，不返回远端请求。path 会先规范化（前导 `/` 视为归档根相对），再用规范路径查加密表与读取；远端引用只保留原始文本，`resource == null` |
| 加密 | 读 `META-INF/encryption.xml`，CipherReference URI 允许前导 `/`、`./`、`//` 等等价写法，统一规范化后再比对。判定依据是**路径是否出现在加密表**，而不是是否带算法名：缺少 `EncryptionMethod` 的条目仍算加密。spine 文档、nav、封面加密 → 打开即抛 `EncryptedContent`；章内图片加密 → 读取时抛 `EncryptedContent`；`EncryptedData` 缺 `CipherReference`/`CipherData`/URI 无法映射 → 见 `UNRESOLVED_ENCRYPTION_KEY` 哨兵，打开即抛 `EncryptedContent`（fail-closed）。`encryption.xml` 为空元素、仅 XML 声明、仅注释（没有元素或没有任何 `EncryptedData`）属合法 OCF，正常打开；标签不匹配、未闭合、多根元素、纯文本等结构性错误 → `CorruptedArchive(invalid-encryption)`。只加密字体等阅读非必需资源不阻断 |
| 懒加载 | 有 nav 时打开不读章节；单章读取只打开该 XHTML；章节缓存为**有界 LRU**（`maxCachedChapters`，默认 8）；资源只在 `readResource` 时读取。无 nav 时按 spine 读标题一次，属文档化例外 |
| 完整性 | 打开时解析 ZIP 中央目录：解压大小与 CRC 用于校验。声明大小被改小 → `CorruptedArchive`；**stored 与 deflate 条目**的 CRC 不符（含等长流替换）→ `CorruptedArchive`，均不会产出静默截断或损坏的正文 |

### 读取上限 `LocalNovelLimits`

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| `maxChapterBytes` | 8 MiB | 单章 XHTML 最大**解压后**字节数 |
| `maxResourceBytes` | 32 MiB | 单张封面/图片最大字节数 |
| `maxChapterCount` | 5000 | 可读章节（spine 文档）数量上限 |
| `maxMetadataBytes` | 4 MiB | container/OPF/nav/encryption 单个 XML 的上限；防止打开阶段 zip bomb。超限的 `limit` 为 `metadata-bytes` |
| `maxCachedChapters` | 8 | 内存中保留的已解析章节数（LRU） |

超限抛 `NovelImportException.LimitExceeded`，`limit` 为 `chapter-bytes` / `resource-bytes` / `chapter-count` / `metadata-bytes`，并带 `maximum`、`actual` 与 `entry`。限制同时按中央目录的未压缩大小与声明大小在分配前检查，再按实际读取字节复核。

## EPUB 目录与 spine 规则（要点）

1. 正文序列 = 过滤掉 `linear="no"` 后的 spine 顺序；过滤为空时使用完整 spine。
2. nav 只提供标题与分组，不改变正文顺序，也不能引入 spine 之外的文档。
3. nav 中未列出的 spine 文档仍按 spine 位置出现，标题取文件名（读取该章时可再取 XHTML 标题）。
4. 同一 spine 文档出现多次（重复 `itemref`）时，每个位置仍是独立章节，ID 含出现序号而保持唯一。
5. 章节 ID 由「文档路径 + 出现序号」的 FNV-1a 计算（`epub-` + 16 位十六进制），同一文件重复打开一致；分组条目使用「分组序号 + 标题」的合成键。
6. 片段锚点（`#ch1`）目前不拆章：同一文档只生成一个章节，`fragment` 用于标识该文档的导航目标但不产生重复章节（已知限制）。
7. nav 列表可以是直接 `<ol>`/`<ul>`，也可以被 `div`/`section`/`header` 等容器包裹；所有顶层列表都会被解析。

## EPUB 资源与关闭

- ZIP 文件系统在打开后保持打开，用于按需读取；`close()` 清空章节缓存与正文并调用 ZIP 文件系统的 `close()`，幂等。Okio 的 `ZipFileSystem` 按次打开/关闭底层句柄，`close()` 因此主要作用是让实例失效并释放缓存，不会遗留打开的文件描述符。
- **打开失败路径同样关闭 ZIP 文件系统**（`openArchiveFileSystem` 与 `open` 都在失败时关闭）；有测试用计数 FileSystem 断言恰好关闭一次。
- `close()` 后 `readChapter` 与 `readResource` 抛 `NovelClosedException`；`metadata`、`encoding`、`tableOfContents` 仍是可读快照。
- EPUB 的 `encoding` 统一报告 `UTF_8`：XHTML/OPF 是 XML，解码后为 Unicode，不存在 TXT 那样的编码探测。

## EPUB 错误

| 类型 | 触发 | 关键字段 |
| --- | --- | --- |
| `CorruptedArchive` | 非 ZIP（`not-a-zip`）、XML 损坏、无 rootfile、空 spine、解压失败或 CRC/大小不符（`decompression-failed`/`crc-mismatch`/`size-mismatch`）、encryption.xml 不可解析（`invalid-encryption`） | `entry`、`detail` |
| `MissingEntry` | container/OPF 不存在、spine 指向的 manifest 项或归档条目不存在。**nav 缺失不算错误**：会回退到 spine 生成目录（文档化行为） | `entry`、`declaredBy` |
| `UnsafeReference` | 越出归档根、绝对路径、远端 URL、非法百分号编码 | `reference`、`document`、`reason` |
| `EncryptedContent` | 阅读所需内容被加密 | `entry`、`algorithm` |
| `LimitExceeded` | 超过 `LocalNovelLimits` | `limit`、`maximum`、`actual`、`entry` |
| `NovelClosedException` | `close()` 后读取章节或资源 | — |

## 共用 API

```kotlin
import dev.reader.media.epub.openEpubNovel
import dev.reader.media.novel.LocalNovelLimits

val book = openEpubNovel(
    fileSystem = okio.FileSystem.SYSTEM,
    path = "/books/novel.epub".toPath(),
    limits = LocalNovelLimits.DEFAULT,
)

book.metadata.title        // dc:title
book.metadata.authors      // 全部 dc:creator
book.metadata.language     // dc:language
book.metadata.cover?.path  // 归档内封面路径，配合 readResource 取字节
book.contentVersion        // 内容指纹，随正文变化
book.tableOfContents       // 按 spine 顺序的目录
book.readChapter(id).blocks   // Heading / Paragraph / Separator / Image
book.readResource("OEBPS/images/a b.png")  // 按需取图片/封面字节

// 阅读位置：保存与恢复
val saved = book.positionOf(chapterId = id, blockIndex = 0, codePointOffset = 0)
val json = kotlinx.serialization.json.Json.encodeToString(ReadingPosition.serializer(), saved)
val resolution = book.resolvePosition(
    kotlinx.serialization.json.Json.decodeFromString(ReadingPosition.serializer(), json),
)
book.close()
```

## 测试与夹具

```bash
./gradlew -p shared/media --max-workers=1 jvmTest iosSimulatorArm64Test
```

`commonTest` 一套测试在 JVM 和 iOS 模拟器上真实读取 `fixtures/epub/`。真实夹具
`pride-and-prejudice.epub`（CC0）与 `cole-voyage-of-life.epub`（CC BY-SA 3.0）验证目录、正文、
封面、元数据与章内图片；`spine-order.epub`、`refs.epub`、`corrupted.epub`、`missing-entry.epub`、
`unsafe-ref.epub`、`encrypted.epub`、`encrypted-font.epub` 由
`fixtures/epub/generate_fixtures.py` 生成，用途见 `fixtures/README.md`。懒加载通过包装 ZIP 文件系统并
记录条目读取来验证（`EpubLazyLoadingTest`）。

## EPUB 完整性校验

打开时读取由 EOCD 记录的偏移与长度指定的完整中央目录（`ZipCentralDirectory`，含 ZIP64；条目数与目录字节数均有硬上限），记录每个条目的未压缩大小与 CRC32。**索引缺失或不完整即视为 `CorruptedArchive(no-central-directory)`**，不会退化为跳过校验。读取时：

- 先按中央目录大小与 `metadata.size` 在分配前检查上限；
- 读取后与中央目录大小比对，发现「声明更小」的攻击时抛 `CorruptedArchive(size-mismatch)`；
- 对所有条目（stored 与 deflate）复核 CRC：stored 原地损坏、deflate 等长流替换都会抛 `CorruptedArchive(crc-mismatch)`；压缩流本身损坏由 Okio 抛 IOException，映射为 `CorruptedArchive(decompression-failed)`。

这些检查保证损坏归档要么被明确报告，要么在 Okio 自身拒绝时报告，不会静默输出被截断或错乱的正文。由于索引覆盖整个目录，条数很多的书籍前段同样受校验；不存在「只校验尾部条目」的缺口。

## EPUB 已知限制

- **片段锚点不拆章**：nav 用多个 `#anchor` 指向同一 XHTML 时只生成一个章节。
- **文件名含 `%` 或 `#`**：条目名里的这两个字符与百分号转义/片段冲突，引用这类名字（即使已转义）可能解析成不可达路径并报 `MissingEntry`。不含这两个字符的名字（覆盖夹具与常见书籍）不受影响。
- **结构化条目可读**：`readResource` 允许读取 `META-INF/` 等归档内路径，因为渲染层只使用书籍自己生成的引用；它只保证不做远端请求，并不做“正文资源白名单”。若未来需要收紧，可在 API 层加白名单而不改解析。
- **EOCD 条目数被改小**：中央目录按 EOCD 记录解析，篡改 total entries 会让部分条目不在索引中，读取时按 `MissingEntry` 报告。条目实际存在，因此分类是“缺失”而非“损坏”；不影响安全，只是错误文案不够精确。
- **无 nav 回退读标题**：没有可用 nav 时，打开会读每个 spine 文档的 `<title>` 一次以生成目录（文档化例外）。
