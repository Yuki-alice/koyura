# 本地小说导入（TXT）

`shared/media` 的本地小说导入契约与 TXT 实现。全部代码在 `commonMain`，只依赖 Okio；不依赖 Compose 或 Android，可直接被书架、数据库和阅读器复用。

## 支持范围

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

尚未支持：GBK/GB18030 等中文旧编码；无 BOM 的 UTF-16（ASCII 为主的字节会按 NUL 分布识别为不支持格式，中文为主的字节无法与二进制安全区分，会报编码错误）；正文章节内容的分页缓存；图片字节解析。

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
- `TocEntry(id, title, level, parentId)`：`id` 形如 `txt-` + 16 位十六进制，由「层级 + 字符偏移 + 标题」的 FNV-1a 计算，**同一文件字节重复打开得到相同 id**，可存为阅读进度键。
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

## 构建与测试

模块是独立 Gradle 项目根：

```bash
./gradlew -p shared/media --max-workers=1 jvmTest iosSimulatorArm64Test
```

测试只在 `commonTest` 写一份，JVM 与 iOS 模拟器各自真实读取 `fixtures/txt/` 下的二进制夹具。夹具由 `fixtures/txt/generate_fixtures.py` 以原始字节生成，用途见 `fixtures/README.md`。
