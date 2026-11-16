# shared/media

本地小说导入的媒体模块。包含：真实与合成 EPUB 夹具、TXT 夹具、跨平台夹具路径注入、`commonMain` 的本地 TXT 导入（`dev.reader.media.novel`）与 EPUB 3 解析（`dev.reader.media.epub`）。支持范围、目录/spine 规则、资源读取与错误见 [`README.md`](README.md)。

## 独立构建

模块自带 `settings.gradle.kts`，是独立 Gradle 项目根，不从仓库根 `settings.gradle.kts` 引入：

```bash
./gradlew -p shared/media --max-workers=1 jvmTest iosSimulatorArm64Test compileKotlinIosArm64 assembleAndroidMain
```

- Kotlin 2.4.21，AGP `com.android.kotlin.multiplatform.library` 9.2.1。
- 目标：`jvm()`、`iosArm64()`、`iosSimulatorArm64()`、Android KMP library。
- `commonMain` 依赖 Okio 3.18.2（`api`，测试直接用其 `FileSystem`）与 Ksoup 0.2.6。
- Android SDK 路径由 `local.properties` 的 `sdk.dir` 提供；该文件被根 `.gitignore` 忽略。

## 源码布局

| 路径 | 内容 |
| --- | --- |
| `src/commonMain/kotlin/dev/reader/media/` | `MediaModule` 模块信息；`epub/` 只读 EPUB 归档与解析 |
| `src/commonMain/kotlin/dev/reader/media/novel/` | 共用契约（`NovelContent`、`NovelImportException`、`LocalNovelLimits`）、TXT 编码与章节识别；`novel/txt/TxtNovel` 是 TXT 实现 |
| `src/commonMain/kotlin/dev/reader/media/epub/` | `EpubNovel`（懒加载 EPUB 3）、`EpubPathResolver`、`EpubPackageParser`、`EpubNavParser`、`EpubXhtmlParser` |
| `src/commonTest/kotlin/dev/reader/media/` | `fixtureRoot()` 的 expect 声明、EPUB 夹具自检 |
| `src/commonTest/kotlin/dev/reader/media/novel/` | TXT 导入测试：编码、章节、正文块、生命周期、失败路径与资源关闭 |
| `src/commonTest/kotlin/dev/reader/media/novel/epub/` | EPUB 测试：真实夹具、spine 顺序、路径解析、错误、限制、懒加载与关闭 |
| `src/jvmTest/kotlin/dev/reader/media/` | 读系统属性 `reader.media.fixtures` 的 actual |
| `src/iosSimulatorArm64Test/kotlin/dev/reader/media/` | 读 `READER_MEDIA_FIXTURES` 环境变量的 actual |
| `fixtures/txt/` | 用 `fixtures/txt/generate_fixtures.py` 生成的 TXT 二进制夹具，用途见 `fixtures/README.md` |
| `fixtures/epub/` | 两个真实 EPUB 加 28 个合成 EPUB；合成夹具由 `fixtures/epub/generate_fixtures.py` 生成 |

## 夹具路径注入

`build.gradle.kts` 把 `fixtures/` 的绝对路径传给两端测试：

- `jvmTest`：`systemProperty("reader.media.fixtures", …)`。
- iOS 模拟器测试：`environment("SIMCTL_CHILD_READER_MEDIA_FIXTURES", …)`；CoreSimulator 去掉 `SIMCTL_CHILD_` 前缀后，测试二进制看到 `READER_MEDIA_FIXTURES`。

测试通过 `okio.FileSystem.SYSTEM` 真实打开夹具文件，因此两端都在验证磁盘上的实际归档，而不是内存字符串。
