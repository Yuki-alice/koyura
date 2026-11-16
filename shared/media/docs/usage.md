# 使用指南

`shared/media` 的本地小说导入 API：打开 TXT 或 EPUB、读元数据与目录、读章节与图片、保存/恢复阅读位置、关闭。

所有示例都只依赖 `commonMain` 的公共 API 与 Okio，可直接粘贴到共享代码。最小示例有两层校验：`UsageDocExampleTest` 会**读取本文件的 ```kotlin 代码块**并断言 API 调用序列没有漂移；同一测试还会**执行**该示例的转写（转写把 `println` 换成收集列表、`FileSystem` 变为注入参数），因此 API 变更会直接编译或测试失败。注意：测试不会把 Markdown 当作代码求值（iOS 模拟器没有运行时编译器），它验证的是「文档代码块包含正确的调用序列」+「等价转写可运行」。

## 最小示例

```kotlin
import app.koyura.media.epub.openEpubNovel
import app.koyura.media.novel.NovelBlock
import app.koyura.media.novel.NovelImport
import app.koyura.media.novel.ReadingPosition
import app.koyura.media.novel.txt.openTxtNovel
import app.koyura.media.novel.useImport
import okio.FileSystem
import okio.Path.Companion.toPath

fun readLocalBook(pathText: String) {
    val path = pathText.toPath()

    // 1) 按扩展名选择导入器。二者返回同一个 NovelImport 契约。
    val novel: NovelImport =
        if (pathText.endsWith(".epub", ignoreCase = true)) {
            openEpubNovel(FileSystem.SYSTEM, path)
        } else {
            openTxtNovel(FileSystem.SYSTEM, path)
        }

    println(novel.metadata.title)
    println(novel.metadata.authors)
    println(novel.contentVersion)

    val toc = novel.tableOfContents
    val firstChapter = toc.first { it.level == 1 }

    // 2) 读一章正文。TocEntry.level 0 是卷标题，level 1 是可读章节。
    val chapter = novel.readChapter(firstChapter.id)
    for (block in chapter.blocks) {
        when (block) {
            is NovelBlock.Heading -> println("# ${block.text}")
            is NovelBlock.Paragraph -> println(block.text)
            is NovelBlock.Separator -> println("---")
            is NovelBlock.Image -> {
                // 3) 图片按需读取字节；没有 resource 的是远端/未解析引用，不应请求。
                val resource = block.reference.resource
                if (resource != null) {
                    val bytes = novel.readResource(resource.path)
                    println("image ${resource.path}: ${bytes.size} bytes")
                }
            }
        }
    }

    // 4) 保存阅读位置：blockIndex 是 blocks 列表下标（含 Heading/Separator/Image），码点偏移 0。
    val saved: ReadingPosition = novel.positionOf(firstChapter.id, blockIndex = 0, codePointOffset = 0)
    val json = kotlinx.serialization.json.Json.encodeToString(ReadingPosition.serializer(), saved)
    println("position: $json")

    // 5) 重新打开后恢复：先反序列化，再交给 novel 定位。
    val restored = kotlinx.serialization.json.Json.decodeFromString(ReadingPosition.serializer(), json)
    val resolution = novel.resolvePosition(restored)
    println("resolved to ${resolution.position.chapterId} block=${resolution.position.blockIndex} " +
        "offset=${resolution.position.codePointOffset} (${resolution.resolution})")

    // 6) 用完即关；useImport 在抛异常时也会关闭。
    novel.close()
}
```

`useImport` 是 `close()` + try/finally 的便捷写法，原生目标也适用：

```kotlin
openTxtNovel(FileSystem.SYSTEM, "/books/长夜行.txt".toPath()).useImport { novel ->
    println(novel.metadata.title)
}
```

## 只读元数据与目录

```kotlin
openEpubNovel(FileSystem.SYSTEM, "/books/novel.epub".toPath()).useImport { novel ->
    novel.metadata.title          // dc:title，缺失时取文件名（去扩展名）
    novel.metadata.authors        // 全部 dc:creator；TXT 只有「作者：…」一声明
    novel.metadata.language       // dc:language，TXT 为 null
    novel.metadata.cover?.path    // 归档内封面路径，配合 readResource 取字节

    novel.tableOfContents.forEach { entry ->
        println("${entry.level}: ${entry.title} parent=${entry.parentId}")
    }
}
```

## 进度定位语义

`ReadingPosition` 的字段：

| 字段 | 含义 |
| --- | --- |
| `chapterId` | 章节稳定 ID（目录 `id`）。相同文件字节重复打开一致 |
| `contentVersion` | 整本内容指纹。相同字节一致；任一章节的任意字符、章节顺序或章节存在性变化都会不同（含插入空行这类改变分段结构的空白编辑）。段落锚点指纹则会忽略空白差异，二者用途不同 |
| `blockIndex` | 章节 `blocks` 列表下标（含标题、分隔、图片，便于渲染定位） |
| `blockFingerprint` | 锚定段落的规范化文本指纹（SHA-256 前 16 位十六进制）。重复段落共享指纹 |
| `codePointOffset` | 段内偏移，按 **Unicode 码点**计。emoji 与补充平面字符算 1 |
| `chapterIndex` | 保存时的章节序号。仅当章节被删除时用于找相邻章节；旧数据为 -1 |

一致性：同一文件重复打开（含重建 `NovelImport`）得到的 `chapterId`、`contentVersion` 与
`positionOf(...)` 结果完全相同。

正文变化后的恢复顺序（`PositionResolution.resolution`）：

1. `EXACT` — 章节、块下标、指纹都匹配，偏移按码点收敛到段落内后原样返回。
2. `REANCHORED_BLOCK` — 同章节内段落被前后插入/删除，按指纹在附近重新找到，偏移重算到新下标。
3. `PREVIOUS_PARAGRAPH` — 锚定段落被删除，取最近的**前一幸存段落**，偏移收敛到该段**末尾**。
4. `NEXT_PARAGRAPH` — 前方没有幸存段落，取最近的**后一幸存段落**。
5. `NEIGHBOR_CHAPTER` — 章节本身被删除，按 `chapterIndex` 取相邻幸存章节，定位到其首个段落开头。
6. `CHAPTER_START` — 其余情况兜底到章节开头。

偏移收敛规则：`codePointOffset` 超过段落码点长度时收敛到段落末尾；负数收敛为 0；永远不会落在
代理对中间。重复段落用「块下标 + 指纹」共同区分：下标精确命中优先，否则在等距的相同指纹中取**较后**
的实例（插入段落后锚点右移更常见）。

读取范围：`resolvePosition` 先按目录定位锚点章节，只在规则需要时读取并解析该章正文。`EXACT` 与同章
`REANCHORED_BLOCK` 因此只解析锚点那一章——后续章节超出 `maxChapterBytes`、损坏或不可读都不影响它。
只有需要跨章回退时才按上述顺序逐个尝试候选章；候选章自身的错误（超限、归档损坏等）会原样抛出，
不会被跳过。

保存位置的调用约定：

- `blockIndex` 指向 `blocks` 列表整体（含 `Heading`/`Separator`/`Image`）。若指向非段落块，会前移到
  最近的后续段落；没有后续段落则回退到章节内任意首个段落；整章无段落时固定在块 0、偏移 0。
- 只对目录中 `level == 1` 的章节保存进度。EPUB 的 `level == 0` 分组条目没有正文，对它保存位置后
  `resolvePosition` 会落到下一章段落（`CHAPTER_START`）。
- `contentVersion` 对任意字符变化敏感（含插入空行改变分段）；`blockFingerprint` 则忽略空白差异，
  以便重新折行后锚点仍可命中。

## 支持范围

| 能力 | TXT | EPUB |
| --- | --- | --- |
| 打开与元数据 | 文件名标题、`作者：…` 作者 | `dc:title`/`dc:creator`/`dc:language`、封面 |
| 目录 | 中文章节标题识别、层级 | nav，缺失时按 spine 生成 |
| 正文 | 段落/分隔/图片引用 | XHTML → Heading/Paragraph/Separator/Image |
| 图片字节 | 无（`readResource` 抛 `UnsupportedOperationException`） | `readResource` 按需读取 |
| 编码 | UTF-8/UTF-16（BOM） | XML（UTF-8 解码） |
| 阅读位置 | 支持 | 支持 |

明确不支持：

- **DRM / 加密正文**：加密的 spine 文档、nav、封面在打开时抛 `EncryptedContent`；不提供解密。
- **远端资源**：远端图片只保留原始引用，`resource` 为 null，绝不发起网络请求。
- **CSS 排版**：不解析样式表，正文按结构顺序输出，样式由阅读器决定。
- **GBK/GB18030 等中文旧编码**、无 BOM UTF-16（见 `docs/README.md`）。
- **片段锚点拆章**：同一 XHTML 被多个 `#anchor` 列出时只生成一个章节。

## 资源关闭规则

- **谁打开谁关闭**：`openTxtNovel`/`openEpubNovel` 返回的对象必须由调用方 `close()`（或 `useImport`）。
- **打开失败不返回半开对象**：所有打开失败路径都会关闭底层句柄，调用方无需清理。
- **TXT**：打开时读完并关闭文件流；`close()` 丢弃解码文本与章节缓存。
- **EPUB**：ZIP 文件系统保持打开以支持按需读取；`close()` 清空章节缓存并关闭 ZIP（Okio 按次开关句柄）。
- **close 后**：`readChapter`、`readResource`、`positionOf`、`resolvePosition` 抛 `NovelClosedException`；
  `metadata`、`encoding`、`tableOfContents`、`contentVersion` 仍是可读快照。
- **TXT 的 `readResource`**：打开状态下抛 `UnsupportedOperationException`（TXT 无二进制资源）；已关闭则先抛
  `NovelClosedException`，与其它读取一致。
- **幂等**：重复 `close()` 安全，第二次无操作。

## 验证文档示例

`src/commonTest/kotlin/novel/UsageDocExampleTest.kt` 直接调用上面的最小示例路径
（打开 TXT 与 EPUB、读目录/章节/图片、序列化与恢复位置、关闭），因此示例一旦与 API 不符就会编译或测试失败。
