# Test fixtures

Real EPUB files used by `shared/media` tests. Both are read directly from this directory at test
time; the absolute path is injected as `reader.media.fixtures` for `jvmTest` and as
`SIMCTL_CHILD_READER_MEDIA_FIXTURES` for `iosSimulatorArm64Test`.

The files were re-downloaded on 2026-11-16 and their SHA-256 values matched the values below.

## epub/pride-and-prejudice.epub

| Field | Value |
| --- | --- |
| Title | Pride and Prejudice — Jane Austen |
| Source | <https://standardebooks.org/ebooks/jane-austen/pride-and-prejudice/downloads/jane-austen_pride-and-prejudice.epub> |
| Publisher | [Standard Ebooks](https://standardebooks.org/) |
| License | Standard Ebooks dedicates its contributions to the worldwide public domain via [CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/). The underlying text is believed to be in the US public domain. |
| SHA-256 | `891e6c477ac1bfa8a3d1204d81f25bd4925aa638c52aa914b2c7c8798cc71843` |
| Size | 831,932 bytes |
| Container root file | `epub/content.opf` |

License evidence inside the file: `epub/content.opf` `dc:rights` declares the CC0 1.0 public domain
dedication, and `dc:publisher` is `Standard Ebooks`.

## epub/cole-voyage-of-life.epub

| Field | Value |
| --- | --- |
| Title | Thomas Cole — The Voyage of Life (EPUB 3 sample, fixed layout) |
| Source | <https://github.com/IDPF/epub3-samples/releases/download/20230704/cole-voyage-of-life.epub> |
| Project | [IDPF epub3-samples](https://github.com/IDPF/epub3-samples) (`30/cole-voyage-of-life`) |
| License | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) |
| SHA-256 | `e38ed606ff604e638f737efaf66e0fd0b2997c3eab7432bcba9a00a81a925cf3` |
| Size | 970,980 bytes |
| Container root file | `EPUB/cole.opf` |

License evidence: the `epub3-samples` README states samples are licensed under CC BY-SA 3.0 unless
noted otherwise, and `EPUB/cole.opf` `dc:rights` plus its `cc:license` link declare
`http://creativecommons.org/licenses/by-sa/3.0/`.

## Readability check

Both archives list normally with `unzip -l` and contain an uncompressed `mimetype` entry, a
`META-INF/container.xml`, and a package document at the path above. The common test
`EpubFixtureSelfCheckTest` opens each one through `okio.FileSystem.SYSTEM` and asserts the
container root file.

## txt/

TXT fixtures are raw bytes written by `fixtures/txt/generate_fixtures.py`; re-running that script
must reproduce every file byte for byte. They are marked `binary` in `shared/media/.gitattributes`
so Git never rewrites their BOMs, byte order or CRLF endings on checkout.

| File | Purpose |
| --- | --- |
| `sample-utf8.txt` | Chinese prose with supplementary-plane characters: U+20BB7 (𠮷) and an emoji (😀), LF endings. |
| `sample-utf8-bom.txt` | Same text as `sample-utf8.txt`, UTF-8 with BOM (`EF BB BF`). |
| `sample-utf16le-bom.txt` | Same text, UTF-16 little endian with BOM (`FF FE`); surrogate pairs cover U+20BB7 and U+1F600. |
| `sample-utf16be-bom.txt` | Same text, UTF-16 big endian with BOM (`FE FF`). |
| `sample-crlf.txt` | Same text, UTF-8 with CRLF line endings; proves `\r` never reaches paragraphs. |
| `chapters-utf8.txt` | Three-volume/three-chapter novel with `第一卷 启程`, `第一章 夜雨` (inline title), `第二章 旧信`, `第二卷 远行`, `第三章 归途`, and `第一节 灯火`; also carries a title/author front-matter preamble. |
| `headings-utf8.txt` | Every supported heading form in one file: `序章 起风`, `楔子`, `前言`, `第一章 初遇`, `第一节 试探`, `第二回 重逢`, `第一篇 旧梦`, `第4章 数字编号`, `后记`, `尾声`, `番外一 春日`. |
| `no-headings-utf8.txt` | Complete body with no chapter headings at all; must still read in full. |
| `image-ref-utf8.txt` | Markdown image reference `![渡口](images/ferry.png)` plus a `***` separator, proving the unified block model without image bytes. |
| `empty.txt` | Zero bytes; must report `NovelImportException.Empty`, not an encoding error. |
| `invalid-utf8.txt` | Valid UTF-8 prefix, then the invalid bytes `C3 28` at byte offset 16; must report `NovelImportException.Encoding` with that offset and line 2. |
| `unsupported-format.txt` | Starts with the ZIP magic `50 4B 03 04`; must report `NovelImportException.UnsupportedFormat`, not an encoding error. |

The preamble in `chapters-utf8.txt` is intentional: it exercises the front-matter section, which
must appear in the table of contents and keep the title and author lines, while chapter heading
lines must not repeat inside chapter bodies.

## epub/ synthetic fixtures

`fixtures/epub/generate_fixtures.py` writes these with `python3 -m zipfile`-equivalent API and fixed
timestamps, so re-running reproduces every archive byte for byte. They exist to isolate one EPUB rule
each; the two real fixtures above cover realistic books.

| File | Purpose | Origin |
| --- | --- | --- |
| `spine-order.epub` | Manifest order (`chapter-2`, `chapter-1`, nav) differs from spine order (`chapter-1`, `chapter-2`). Tests assert reading order follows the spine. | Synthetic, generated here |
| `refs.epub` | Chapter references cover `%20` encoding, a literal space in a file name, `./` + `../` folding, a `#fragment`, and a Chinese UTF-8 percent-encoded name. Includes `images/a b.png` and `images/deep/c.png` for byte checks. | Synthetic, generated here |
| `corrupted.epub` | Plain text, not a ZIP. Must report `CorruptedArchive(not-a-zip)`, not `Io`. | Synthetic, generated here |
| `missing-entry.epub` | OPF manifest and spine name `OEBPS/text/chapter-2.xhtml`, which the archive omits. Must report `MissingEntry` at open. | Synthetic, generated here |
| `unsafe-ref.epub` | Chapter images reference `../../../outside.png` (escapes root), `https://example.com/remote.png` (remote), `/absolute.png` (absolute), plus one safe image. Also contains a `<script>`. | Synthetic, generated here |
| `encrypted.epub` | `META-INF/encryption.xml` encrypts a spine XHTML document (`chapter-2`). Must report `EncryptedContent` at open. | Synthetic, generated here |
| `encrypted-font.epub` | `encryption.xml` encrypts only a font (`OEBPS/fonts/serif.otf`). Reading must succeed; non-reading resources do not block. | Synthetic, generated here |

| `textloss.epub` | Bare text inside `div`/`blockquote`/`aside`/`figure` plus `<br>`-separated poetry; must not be dropped. | Synthetic, generated here |
| `nav-landmarks.epub` | First `<nav>` is `landmarks`, second is `toc`; landmarks must not become the TOC. | Synthetic, generated here |
| `nav-prefixed.epub` | Nav type written as `ops:type="toc"` bound to the OPS namespace. | Synthetic, generated here |
| `nav-groups.epub` | Two sibling groups both titled `Part`, plus a trailing ungrouped chapter. | Synthetic, generated here |
| `dup-spine.epub` | Spine repeats the same `itemref`; ids must stay unique. | Synthetic, generated here |
| `opf-prefixed.epub` | OPF structure elements written with an `opf:` prefix. | Synthetic, generated here |
| `images-in-links.epub` | Images wrapped in `<a>`, `<span>` and `<li>` must still become image blocks. | Synthetic, generated here |
| `no-nav.epub` | No nav item; the TOC comes from the spine (documented lazy-open exception). | Synthetic, generated here |
| `encrypted-slash-uri.epub` | `encryption.xml` URI written as `/OEBPS/text/c1.xhtml`; must still block reading. | Synthetic, generated here |
| `oversized-metadata.epub` | `container.xml` inflates past `maxMetadataBytes` (8 MiB of comment); must be `LimitExceeded`. | Synthetic, generated here |
| `declared-size.epub` | Central-directory size patched to 200 while the entry is 20 KB; read must be `CorruptedArchive`. | Synthetic, generated here |
| `crc-mismatch.epub` | Stored entry's bytes altered after the CRC was written; read must be `CorruptedArchive`. | Synthetic, generated here |
| `big-central-dir-trunc.epub` | 1205 entries push the central directory past 64 KiB; `c1`'s declared size is shrunk to 200. Must be `CorruptedArchive`, and the index must cover all 1205 entries. | Synthetic, generated here |
| `big-central-dir-crc.epub` | Same size, stored chapter corrupted in place; CRC must be checked for early entries too. | Synthetic, generated here |
| `deflate-swap.epub` | Deflated chapter replaced by a same-length, different-content stream with the original CRC kept; must be `CorruptedArchive`, never served. | Synthetic, generated here |
| `empty-encryption.epub` | Legal empty `<encryption/>`; must open normally. | Synthetic, generated here |
| `nav-mixed.epub` | Nav has a direct `<ol>` and a `div`-wrapped `<ol>`; both must contribute TOC entries. | Synthetic, generated here |
| `block-image.epub` | Block-level `<a><img/></a>` inside a div and directly under body, plus a ruby annotation; images must survive and ruby must stay one paragraph. | Synthetic, generated here |
| `encrypted-no-method.epub` | Encrypted spine doc whose `EncryptedData` omits `EncryptionMethod`; must still block (algorithm is null). | Synthetic, generated here |
| `encrypted-no-cipherref.epub` | `EncryptedData` with no usable `CipherReference`; must fail closed. | Synthetic, generated here |
| `encrypted-lowercase.epub` | Lowercase `<encrypteddata>`/`<cipherreference>` tags; must still block. | Synthetic, generated here |
Licensing: the synthetic fixtures are authored for this repository and dedicated to the public domain
under [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/); they contain no third-party
content beyond the fake `FAKE-` payload bytes.

The real fixtures are unchanged from the earlier milestone:

| File | Source | License |
| --- | --- | --- |
| `pride-and-prejudice.epub` | Standard Ebooks | CC0 1.0 public domain dedication (see `epub/content.opf` `dc:rights`) |
| `cole-voyage-of-life.epub` | IDPF epub3-samples | CC BY-SA 3.0 (see `EPUB/cole.opf` `dc:rights`) |
