#!/usr/bin/env python3
"""Generate the small synthetic EPUB fixtures used by ``shared/media`` tests.

Each fixture isolates one behavior so a failure names the rule it broke:

- ``spine-order.epub``  manifest order differs from spine order.
- ``refs.epub``         references with spaces, percent-encoding, ``./``, ``../``, ``#frag``.
- ``corrupted.epub``    plain text, not a ZIP at all.
- ``missing-entry.epub`` OPF manifest names a spine document the ZIP does not contain.
- ``unsafe-ref.epub``   chapter image references ``../`` above the root and a remote URL.
- ``encrypted.epub``    ``META-INF/encryption.xml`` encrypts a spine document.
- ``textloss.epub``     bare text inside div/blockquote/aside/figure containers and br-separated poetry.
- ``nav-landmarks.epub`` first nav is landmarks, second is toc.
- ``nav-prefixed.epub`` nav type written with ``ops:`` prefix bound to the OPS namespace.
- ``nav-groups.epub``   two groups with the same title plus an ungrouped chapter.
- ``dup-spine.epub``    spine repeats the same itemref.
- ``opf-prefixed.epub`` OPF elements written with an ``opf:`` namespace prefix.
- ``images-in-links.epub`` image wrapped in a link/span/list item inside a paragraph.
- ``no-nav.epub``       no nav document at all; TOC must come from the spine.
- ``encrypted-slash-uri.epub`` encryption URI written with a leading slash.
- ``oversized-metadata.epub`` container.xml that inflates past maxMetadataBytes.
- ``declared-size.epub`` central-directory size deliberately smaller than real content.
- ``crc-mismatch.epub`` stored entry whose bytes were altered after CRC was written.
- ``big-central-dir-trunc.epub`` 1200 filler entries push the directory past 64 KiB; the chapter's declared size is shrunk.
- ``big-central-dir-crc.epub`` same size, stored chapter corrupted in place.
- ``deflate-swap.epub`` deflated chapter replaced by a same-length, different-content stream.
- ``empty-encryption.epub`` legal but empty ``<encryption/>``; must open normally.
- ``nav-mixed.epub`` nav with a direct ``<ol>`` and a ``div``-wrapped ``<ol>``; both must be read.
- ``block-image.epub`` block-level ``<a><img/></a>`` and ``<body><a><img/></a></body>``.
- ``ruby.epub`` Japanese ruby annotation inside a block container.
- ``encrypted-no-method.epub`` encrypted spine doc whose ``EncryptedData`` omits ``EncryptionMethod``.
- ``encrypted-no-cipherref.epub`` encrypted ``EncryptedData`` with no ``CipherReference``.
- ``encrypted-lowercase.epub`` lowercase ``<encrypteddata>`` tags with a real cipher reference.

Deterministic: re-running must reproduce every archive byte for byte (fixed timestamps and fixed
compression settings), so the checked-in files stay stable.
"""

from pathlib import Path
import zipfile

HERE = Path(__file__).resolve().parent

# Fixed timestamp so ZIP metadata does not change between runs or machines.
FIXED_DATE = (2026, 1, 1, 0, 0, 0)

CONTAINER = """<?xml version="1.0" encoding="UTF-8"?>
<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
  <rootfiles>
    <rootfile full-path="{opf}" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""

MIMETYPE = "application/epub+zip"


def xhtml(title: str, body: str) -> str:
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">\n'
        f"<head><title>{title}</title></head>\n"
        f"<body>{body}</body>\n"
        "</html>\n"
    )


def opf(
    *,
    title: str,
    language: str = "en",
    manifest_items: str,
    spine_items: str,
    extra_metadata: str = "",
) -> str:
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">\n'
        '  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">\n'
        f'    <dc:title id="title">{title}</dc:title>\n'
        '    <dc:creator>Fixture Author</dc:creator>\n'
        f"    <dc:language>{language}</dc:language>\n"
        '    <dc:identifier id="uid">urn:uuid:fixture</dc:identifier>\n'
        f"    {extra_metadata}\n"
        "  </metadata>\n"
        "  <manifest>\n"
        f"{manifest_items}"
        "  </manifest>\n"
        "  <spine>\n"
        f"{spine_items}"
        "  </spine>\n"
        "</package>\n"
    )


def nav(documents: list[tuple[str, str]]) -> str:
    items = "\n".join(f'      <li><a href="{href}">{label}</a></li>' for href, label in documents)
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">\n'
        "<head><title>Contents</title></head>\n"
        "<body>\n"
        '  <nav epub:type="toc" id="toc">\n'
        "    <ol>\n"
        f"{items}\n"
        "    </ol>\n"
        "  </nav>\n"
        "</body>\n"
        "</html>\n"
    )


def write_epub(name: str, entries: dict[str, bytes], stored: set[str] | None = None) -> None:
    """Writes a deterministic EPUB: mimetype stored first and uncompressed, then the rest.

    Entries in [stored] are written uncompressed so a test can corrupt their bytes in place and rely
    on a CRC check rather than on an inflate failure.
    """
    stored = stored or set()
    path = HERE / name
    with zipfile.ZipFile(path, "w") as archive:
        info = zipfile.ZipInfo("mimetype", FIXED_DATE)
        info.compress_type = zipfile.ZIP_STORED
        archive.writestr(info, MIMETYPE)
        for entry_name in sorted(entries):
            data = entries[entry_name]
            entry_info = zipfile.ZipInfo(entry_name, FIXED_DATE)
            entry_info.compress_type = (
                zipfile.ZIP_STORED if entry_name in stored else zipfile.ZIP_DEFLATED
            )
            entry_info.external_attr = 0o644 << 16
            archive.writestr(entry_info, data)
    print(f"{name:<24} {path.stat().st_size:>7} bytes  entries={len(entries) + 1}")


def spine_order() -> None:
    """Manifest order is chapter-2, chapter-1, nav; spine order is chapter-1, chapter-2."""
    entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "OEBPS/content.opf": opf(
            title="Spine Order",
            manifest_items=(
                '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
                '    <item id="c2" href="text/chapter-2.xhtml" media-type="application/xhtml+xml"/>\n'
                '    <item id="c1" href="text/chapter-1.xhtml" media-type="application/xhtml+xml"/>\n'
            ),
            spine_items='    <itemref idref="c1"/>\n    <itemref idref="c2"/>\n',
        ).encode(),
        "OEBPS/nav.xhtml": nav(
            [
                ("text/chapter-2.xhtml", "Second In Nav"),
                ("text/chapter-1.xhtml", "First In Nav"),
            ]
        ).encode(),
        "OEBPS/text/chapter-1.xhtml": xhtml(
            "Chapter One",
            "<h1>Chapter One</h1><p>This is the FIRST spine document.</p>",
        ).encode(),
        "OEBPS/text/chapter-2.xhtml": xhtml(
            "Chapter Two",
            "<h1>Chapter Two</h1><p>This is the SECOND spine document.</p>",
        ).encode(),
    }
    write_epub("spine-order.epub", entries)


def refs() -> None:
    """References exercise spaces, percent-encoding, ./ and ../ plus a fragment."""
    styles_css = b"body { margin: 0; }"
    picture = b"\x89PNG\r\n\x1a\nFAKE-CHILD-IMAGE"
    deep_image = b"\x89PNG\r\n\x1a\nFAKE-DEEP-IMAGE"
    entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "OEBPS/content.opf": opf(
            title="References",
            manifest_items=(
                '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
                '    <item id="c1" href="text/chapter.xhtml" media-type="application/xhtml+xml"/>\n'
                '    <item id="css" href="styles/main.css" media-type="text/css"/>\n'
                '    <item id="img" href="images/a b.png" media-type="image/png"/>\n'
                '    <item id="deep" href="images/deep/c.png" media-type="image/png"/>\n'
            ),
            spine_items='    <itemref idref="c1"/>\n',
        ).encode(),
        "OEBPS/nav.xhtml": nav([("text/chapter.xhtml", "Only Chapter")]).encode(),
        "OEBPS/styles/main.css": styles_css,
        "OEBPS/images/a b.png": picture,
        "OEBPS/images/deep/c.png": deep_image,
        "OEBPS/text/chapter.xhtml": xhtml(
            "References",
            (
                "<h1>References</h1>"
                # Same directory, percent-encoded space in a real space name.
                '<p><img src="../images/a%20b.png" alt="space encoded"/></p>'
                # Literal space in the URL.
                '<p><img src="../images/a b.png" alt="literal space"/></p>'
                # ./ and ../ folding.
                '<p><img src="./../images/deep/c.png" alt="deep"/></p>'
                # Fragment on an image reference.
                '<p><img src="../images/deep/c.png#frag" alt="fragment"/></p>'
                # Chinese percent-encoded name resolves to a file that does not exist; resolution must
                # still be safe and normalized (the test only asserts the path).
                '<p><img src="../images/%E6%B8%A1%E5%8F%A3.png" alt="cjk"/></p>'
            ),
        ).encode(),
    }
    write_epub("refs.epub", entries)


def corrupted() -> None:
    path = HERE / "corrupted.epub"
    path.write_bytes(b"this file is deliberately not a zip archive\n")
    print(f"{'corrupted.epub':<24} {path.stat().st_size:>7} bytes  (not a zip)")


def missing_entry() -> None:
    """Spine points at chapter-2.xhtml, which the archive omits."""
    entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "OEBPS/content.opf": opf(
            title="Missing Entry",
            manifest_items=(
                '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
                '    <item id="c1" href="text/chapter-1.xhtml" media-type="application/xhtml+xml"/>\n'
                '    <item id="c2" href="text/chapter-2.xhtml" media-type="application/xhtml+xml"/>\n'
            ),
            spine_items='    <itemref idref="c1"/>\n    <itemref idref="c2"/>\n',
        ).encode(),
        "OEBPS/nav.xhtml": nav(
            [
                ("text/chapter-1.xhtml", "One"),
                ("text/chapter-2.xhtml", "Two"),
            ]
        ).encode(),
        "OEBPS/text/chapter-1.xhtml": xhtml("One", "<h1>One</h1><p>Present.</p>").encode(),
        # chapter-2.xhtml intentionally absent.
    }
    write_epub("missing-entry.epub", entries)


def unsafe_ref() -> None:
    """A chapter image points above the archive root and another points at a remote URL."""
    entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "OEBPS/content.opf": opf(
            title="Unsafe Reference",
            manifest_items=(
                '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
                '    <item id="c1" href="text/chapter.xhtml" media-type="application/xhtml+xml"/>\n'
                '    <item id="ok" href="images/ok.png" media-type="image/png"/>\n'
            ),
            spine_items='    <itemref idref="c1"/>\n',
        ).encode(),
        "OEBPS/nav.xhtml": nav([("text/chapter.xhtml", "Unsafe")]).encode(),
        "OEBPS/images/ok.png": b"\x89PNG\r\n\x1a\nFAKE-OK",
        "OEBPS/text/chapter.xhtml": xhtml(
            "Unsafe",
            (
                "<h1>Unsafe</h1>"
                "<p>Before.</p>"
                '<p><img src="../../../outside.png" alt="escapes"/></p>'
                '<p><img src="https://example.com/remote.png" alt="remote"/></p>'
                '<p><img src="/absolute.png" alt="absolute"/></p>'
                '<p><img src="../images/ok.png" alt="ok"/></p>'
                "<script>fetch('https://example.com/evil.js')</script>"
                "<p>After.</p>"
            ),
        ).encode(),
    }
    write_epub("unsafe-ref.epub", entries)


def encrypted() -> None:
    """encryption.xml encrypts a spine XHTML document (chapter-2), leaving chapter-1 readable."""
    encryption = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"\n'
        '            xmlns:enc="http://www.w3.org/2001/04/xmlenc#">\n'
        "  <enc:EncryptedData>\n"
        '    <enc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes256-cbc"/>\n'
        "    <enc:CipherData>\n"
        '      <enc:CipherReference URI="OEBPS/text/chapter-2.xhtml"/>\n'
        "    </enc:CipherData>\n"
        "  </enc:EncryptedData>\n"
        "</encryption>\n"
    )
    entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "META-INF/encryption.xml": encryption.encode(),
        # Font-only encryption must NOT block reading; covered by a second fixture below.
        "OEBPS/content.opf": opf(
            title="Encrypted Chapter",
            manifest_items=(
                '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
                '    <item id="c1" href="text/chapter-1.xhtml" media-type="application/xhtml+xml"/>\n'
                '    <item id="c2" href="text/chapter-2.xhtml" media-type="application/xhtml+xml"/>\n'
            ),
            spine_items='    <itemref idref="c1"/>\n    <itemref idref="c2"/>\n',
        ).encode(),
        "OEBPS/nav.xhtml": nav(
            [
                ("text/chapter-1.xhtml", "One"),
                ("text/chapter-2.xhtml", "Two"),
            ]
        ).encode(),
        "OEBPS/text/chapter-1.xhtml": xhtml("One", "<h1>One</h1><p>Readable.</p>").encode(),
        "OEBPS/text/chapter-2.xhtml": xhtml("Two", "<h1>Two</h1><p>Encrypted.</p>").encode(),
    }
    write_epub("encrypted.epub", entries)


def encrypted_font_only() -> None:
    """Only a font is encrypted, so the whole book must remain readable."""
    encryption = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"\n'
        '            xmlns:enc="http://www.w3.org/2001/04/xmlenc#">\n'
        "  <enc:EncryptedData>\n"
        '    <enc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes256-cbc"/>\n'
        "    <enc:CipherData>\n"
        '      <enc:CipherReference URI="OEBPS/fonts/serif.otf"/>\n'
        "    </enc:CipherData>\n"
        "  </enc:EncryptedData>\n"
        "</encryption>\n"
    )
    entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "META-INF/encryption.xml": encryption.encode(),
        "OEBPS/content.opf": opf(
            title="Font Encrypted",
            manifest_items=(
                '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
                '    <item id="c1" href="text/chapter-1.xhtml" media-type="application/xhtml+xml"/>\n'
                '    <item id="font" href="fonts/serif.otf" media-type="font/otf"/>\n'
            ),
            spine_items='    <itemref idref="c1"/>\n',
        ).encode(),
        "OEBPS/nav.xhtml": nav([("text/chapter-1.xhtml", "One")]).encode(),
        "OEBPS/text/chapter-1.xhtml": xhtml("One", "<h1>One</h1><p>Still readable.</p>").encode(),
        "OEBPS/fonts/serif.otf": b"\x00\x01\x00\x00FAKE-FONT",
    }
    write_epub("encrypted-font.epub", entries)


def textloss() -> None:
    """Bare text inside block containers must not be dropped."""
    entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "OEBPS/content.opf": opf(
            title="Text Loss",
            manifest_items=(
                '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
                '    <item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/>\n'
            ),
            spine_items='    <itemref idref="c1"/>\n',
        ).encode(),
        "OEBPS/nav.xhtml": nav([("text/c1.xhtml", "Only")]).encode(),
        "OEBPS/text/c1.xhtml": xhtml(
            "Text Loss",
            (
                "<h1>Heading</h1>"
                "<p>first para</p>"
                "<div>Volume heading text<p>inner para</p>trailing sentence</div>"
                "<blockquote>Plain quoted text without inner p.</blockquote>"
                "<div>line one<br/>line two<br/>line three</div>"
                "<aside>Editor note.</aside>"
                "<figure><img src=\"images/f.png\" alt=\"fig\"/><figcaption>Figure caption text.</figcaption></figure>"
                "<p>final</p>"
            ),
        ).encode(),
        "OEBPS/images/f.png": b"\x89PNG\r\n\x1a\nFAKE-FIGURE",
    }
    write_epub("textloss.epub", entries)


def nav_landmarks() -> None:
    """The first nav is landmarks; only the second is the toc."""
    entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "OEBPS/content.opf": opf(
            title="Landmarks First",
            manifest_items=(
                '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
                '    <item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/>\n'
                '    <item id="c2" href="text/c2.xhtml" media-type="application/xhtml+xml"/>\n'
            ),
            spine_items='    <itemref idref="c1"/>\n    <itemref idref="c2"/>\n',
        ).encode(),
        "OEBPS/nav.xhtml": (
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">\n'
            "<head><title>Nav</title></head><body>\n"
            '<nav epub:type="landmarks"><ol><li><a href="text/c1.xhtml">Start</a></li></ol></nav>\n'
            '<nav epub:type="toc"><ol>'
            '<li><a href="text/c1.xhtml">Chapter One</a></li>'
            '<li><a href="text/c2.xhtml">Chapter Two</a></li>'
            "</ol></nav>\n"
            "</body></html>\n"
        ).encode(),
        "OEBPS/text/c1.xhtml": xhtml("One", "<h1>Chapter One</h1><p>one</p>").encode(),
        "OEBPS/text/c2.xhtml": xhtml("Two", "<h1>Chapter Two</h1><p>two</p>").encode(),
    }
    write_epub("nav-landmarks.epub", entries)


def nav_prefixed() -> None:
    """Nav type uses an `ops:` prefix bound to the OPS namespace instead of `epub:`."""
    entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "OEBPS/content.opf": opf(
            title="Prefixed Nav",
            manifest_items=(
                '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
                '    <item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/>\n'
            ),
            spine_items='    <itemref idref="c1"/>\n',
        ).encode(),
        "OEBPS/nav.xhtml": (
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<html xmlns="http://www.w3.org/1999/xhtml" xmlns:ops="http://www.idpf.org/2007/ops">\n'
            "<head><title>Nav</title></head><body>\n"
            '<nav ops:type="toc"><ol><li><a href="text/c1.xhtml">Prefixed Title</a></li></ol></nav>\n'
            "</body></html>\n"
        ).encode(),
        "OEBPS/text/c1.xhtml": xhtml("One", "<h1>One</h1><p>one</p>").encode(),
    }
    write_epub("nav-prefixed.epub", entries)


def nav_groups() -> None:
    """Two groups share a title; an ungrouped chapter must not inherit a group."""
    entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "OEBPS/content.opf": opf(
            title="Groups",
            manifest_items="".join(
                f'    <item id="{i}" href="text/{i}.xhtml" media-type="application/xhtml+xml"/>\n'
                for i in ("c1", "c2", "c3")
            )
            + '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n',
            spine_items="".join(f'    <itemref idref="{i}"/>\n' for i in ("c1", "c2", "c3")),
        ).encode(),
        "OEBPS/nav.xhtml": (
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">\n'
            "<head><title>Nav</title></head><body><nav epub:type=\"toc\"><ol>\n"
            '<li><span>Part</span><ol><li><a href="text/c1.xhtml">One</a></li></ol></li>\n'
            '<li><span>Part</span><ol><li><a href="text/c2.xhtml">Two</a></li></ol></li>\n'
            '<li><a href="text/c3.xhtml">Three</a></li>\n'
            "</ol></nav></body></html>\n"
        ).encode(),
        "OEBPS/text/c1.xhtml": xhtml("One", "<p>one</p>").encode(),
        "OEBPS/text/c2.xhtml": xhtml("Two", "<p>two</p>").encode(),
        "OEBPS/text/c3.xhtml": xhtml("Three", "<p>three</p>").encode(),
    }
    write_epub("nav-groups.epub", entries)


def dup_spine() -> None:
    """Spine repeats c1, so ids must still be unique."""
    entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "OEBPS/content.opf": opf(
            title="Dup Spine",
            manifest_items=(
                '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
                '    <item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/>\n'
                '    <item id="c2" href="text/c2.xhtml" media-type="application/xhtml+xml"/>\n'
            ),
            spine_items='    <itemref idref="c1"/>\n    <itemref idref="c2"/>\n    <itemref idref="c1"/>\n',
        ).encode(),
        "OEBPS/nav.xhtml": nav([
            ("text/c1.xhtml", "One"),
            ("text/c2.xhtml", "Two"),
        ]).encode(),
        "OEBPS/text/c1.xhtml": xhtml("One", "<p>one</p>").encode(),
        "OEBPS/text/c2.xhtml": xhtml("Two", "<p>two</p>").encode(),
    }
    write_epub("dup-spine.epub", entries)


def opf_prefixed() -> None:
    """OPF structure elements use an `opf:` prefix."""
    opf_xml = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<opf:package xmlns:opf="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/" version="3.0">\n'
        "  <opf:metadata>\n"
        "    <dc:title>Prefixed OPF</dc:title>\n"
        "    <dc:creator>Prefixed Author</dc:creator>\n"
        "    <dc:language>en</dc:language>\n"
        "  </opf:metadata>\n"
        "  <opf:manifest>\n"
        '    <opf:item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
        '    <opf:item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/>\n'
        "  </opf:manifest>\n"
        "  <opf:spine><opf:itemref idref=\"c1\"/></opf:spine>\n"
        "</opf:package>\n"
    )
    entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "OEBPS/content.opf": opf_xml.encode(),
        "OEBPS/nav.xhtml": nav([("text/c1.xhtml", "Prefixed Chapter")]).encode(),
        "OEBPS/text/c1.xhtml": xhtml("One", "<h1>One</h1><p>prefixed opf works</p>").encode(),
    }
    write_epub("opf-prefixed.epub", entries)


def images_in_links() -> None:
    """Images nested inside <a>, <span> and <li> must still become Image blocks."""
    entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "OEBPS/content.opf": opf(
            title="Nested Images",
            manifest_items=(
                '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
                '    <item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/>\n'
            ),
            spine_items='    <itemref idref="c1"/>\n',
        ).encode(),
        "OEBPS/nav.xhtml": nav([("text/c1.xhtml", "Nested")]).encode(),
        "OEBPS/text/c1.xhtml": xhtml(
            "Nested",
            (
                '<p><a href="big.html"><img src="img/x.png" alt="linked"/></a></p>'
                '<p><span><img src="img/y.png" alt="spanned"/></span></p>'
                '<ul><li><img src="img/z.png" alt="inlist"/></li></ul>'
            ),
        ).encode(),
        "OEBPS/text/img/x.png": b"\x89PNG\r\n\x1a\nX",
        "OEBPS/text/img/y.png": b"\x89PNG\r\n\x1a\nY",
        "OEBPS/text/img/z.png": b"\x89PNG\r\n\x1a\nZ",
    }
    write_epub("images-in-links.epub", entries)


def no_nav() -> None:
    """No nav item at all; TOC comes from the spine."""
    entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "OEBPS/content.opf": opf(
            title="No Nav",
            manifest_items=(
                '    <item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/>\n'
                '    <item id="c2" href="text/c2.xhtml" media-type="application/xhtml+xml"/>\n'
            ),
            spine_items='    <itemref idref="c1"/>\n    <itemref idref="c2"/>\n',
        ).encode(),
        "OEBPS/text/c1.xhtml": xhtml("Derived One", "<h1>Derived One</h1><p>a</p>").encode(),
        "OEBPS/text/c2.xhtml": xhtml("Derived Two", "<h1>Derived Two</h1><p>b</p>").encode(),
    }
    write_epub("no-nav.epub", entries)


def encrypted_slash_uri() -> None:
    """encryption.xml URI is written with a leading slash; it must still match."""
    encryption = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"\n'
        '            xmlns:enc="http://www.w3.org/2001/04/xmlenc#">\n'
        "  <enc:EncryptedData>\n"
        '    <enc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes256-cbc"/>\n'
        "    <enc:CipherData>\n"
        '      <enc:CipherReference URI="/OEBPS/text/c1.xhtml"/>\n'
        "    </enc:CipherData>\n"
        "  </enc:EncryptedData>\n"
        "</encryption>\n"
    )
    entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "META-INF/encryption.xml": encryption.encode(),
        "OEBPS/content.opf": opf(
            title="Slash Uri",
            manifest_items=(
                '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
                '    <item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/>\n'
            ),
            spine_items='    <itemref idref="c1"/>\n',
        ).encode(),
        "OEBPS/nav.xhtml": nav([("text/c1.xhtml", "One")]).encode(),
        "OEBPS/text/c1.xhtml": xhtml("One", "<p>ciphertext-ish</p>").encode(),
    }
    write_epub("encrypted-slash-uri.epub", entries)


def oversized_metadata() -> None:
    """container.xml inflates well past maxMetadataBytes (default 4 MiB)."""
    huge = "<!--" + ("x" * (8 * 1024 * 1024)) + "-->"
    container_xml = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        f"{huge}\n"
        '<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">\n'
        "  <rootfiles>\n"
        '    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>\n'
        "  </rootfiles>\n"
        "</container>\n"
    )
    entries = {
        "META-INF/container.xml": container_xml.encode(),
        "OEBPS/content.opf": opf(
            title="Big Metadata",
            manifest_items='    <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>\n',
            spine_items='    <itemref idref="c1"/>\n',
        ).encode(),
        "OEBPS/c1.xhtml": xhtml("One", "<p>one</p>").encode(),
    }
    write_epub("oversized-metadata.epub", entries)


def declared_size_and_crc() -> None:
    """Two hostile archives built from the same base content.

    - ``declared-size.epub``: the central-directory uncompressed size says 200 while the real entry is
      much larger, so a reader that trusts the declaration silently truncates the body.
    - ``crc-mismatch.epub``: a stored entry's bytes are altered after the CRC was written.
    """
    body = "ABCDEFGHIJ" * 2000  # 20,000 chars
    page = xhtml("Declared", f"<h1>Declared</h1><p>{body}</p>")
    base_entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "OEBPS/content.opf": opf(
            title="Declared",
            manifest_items=(
                '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
                '    <item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/>\n'
            ),
            spine_items='    <itemref idref="c1"/>\n',
        ).encode(),
        "OEBPS/nav.xhtml": nav([("text/c1.xhtml", "One")]).encode(),
        "OEBPS/text/c1.xhtml": page.encode(),
    }
    # Build a normal archive first, then patch the central directory of the target entry.
    target = "OEBPS/text/c1.xhtml"

    write_epub("declared-size.epub", base_entries)
    _patch_central_directory(HERE / "declared-size.epub", target, new_uncompressed=200)
    print(f"{'declared-size.epub':<24} declared size patched to 200 for {target}")

    write_epub(
        "crc-mismatch.epub",
        {**base_entries},
        # A stored entry so the corruption is a CRC failure, not an inflate error.
        stored={target},
    )
    _corrupt_stored_entry(HERE / "crc-mismatch.epub", target)
    print(f"{'crc-mismatch.epub':<24} stored entry bytes altered for {target}")


def _patch_central_directory(path: Path, entry_name: str, new_uncompressed: int) -> None:
    """Rewrites the uncompressed-size field of one central-directory record."""
    data = bytearray(path.read_bytes())
    name_bytes = entry_name.encode()
    index = 0
    while True:
        index = data.find(b"PK\x01\x02", index)
        if index < 0:
            raise SystemExit(f"central header not found for {entry_name}")
        name_start = index + 46
        name_len = int.from_bytes(data[index + 28:index + 30], "little")
        if bytes(data[name_start:name_start + name_len]) == name_bytes:
            # Uncompressed size is a 4-byte little-endian field at offset 24.
            data[index + 24:index + 28] = new_uncompressed.to_bytes(4, "little")
            path.write_bytes(bytes(data))
            return
        index += 4


def _corrupt_stored_entry(path: Path, entry_name: str) -> None:
    """Flips bytes in a stored entry's data without updating its CRC."""
    data = bytearray(path.read_bytes())
    name_bytes = entry_name.encode()
    index = 0
    while True:
        index = data.find(b"PK\x01\x02", index)
        if index < 0:
            raise SystemExit(f"central header not found for {entry_name}")
        name_start = index + 46
        name_len = int.from_bytes(data[index + 28:index + 30], "little")
        if bytes(data[name_start:name_start + name_len]) == name_bytes:
            local_offset = int.from_bytes(data[index + 42:index + 46], "little")
            # Local header: 30-byte fixed part + name + extra, then the stored data.
            local_name_len = int.from_bytes(data[local_offset + 26:local_offset + 28], "little")
            local_extra_len = int.from_bytes(data[local_offset + 28:local_offset + 30], "little")
            data_start = local_offset + 30 + local_name_len + local_extra_len
            for i in range(5):
                data[data_start + i] = data[data_start + i] ^ 0xFF
            path.write_bytes(bytes(data))
            return
        index += 4


def big_central_directory_fixtures() -> None:
    """Archives whose central directory exceeds the old 64 KiB tail window."""
    body = "ABCDEFGHIJ" * 2000  # 20,000 chars
    page = xhtml("Big", f"<h1>Big</h1><p>{body}</p>")
    target = "OEBPS/text/c1.xhtml"
    base = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "OEBPS/content.opf": opf(
            title="Big Central Directory",
            manifest_items=(
                '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
                '    <item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/>\n'
            ),
            spine_items='    <itemref idref="c1"/>\n',
        ).encode(),
        "OEBPS/nav.xhtml": nav([("text/c1.xhtml", "One")]).encode(),
        "OEBPS/text/c1.xhtml": page.encode(),
    }
    # 1200 filler entries: long names guarantee the directory passes 64 KiB.
    for index in range(1200):
        base[f"OEBPS/filler/filler-{index:04d}-padding-padding-padding.bin"] = b"x"

    write_epub("big-central-dir-trunc.epub", base)
    _patch_central_directory(HERE / "big-central-dir-trunc.epub", target, new_uncompressed=200)
    print(f"{'big-central-dir-trunc.epub':<28} declared 200 for {target}")

    write_epub("big-central-dir-crc.epub", base, stored={target})
    _corrupt_stored_entry(HERE / "big-central-dir-crc.epub", target)
    print(f"{'big-central-dir-crc.epub':<28} stored bytes altered for {target}")


def deflate_swap() -> None:
    """Replaces a deflated entry's stream with a same-length stream of different content."""
    import zlib

    original = xhtml("Swap", "<h1>Swap</h1><p>ORIGINAL-TEXT-HERE</p>")
    # Same document length as ORIGINAL-TEXT-HERE, and its raw-deflate stream is also exactly the same
    # length, so the swap cannot be detected by sizes alone (chosen by search over candidate words).
    tampered = xhtml("Swap", "<h1>Swap</h1><p>YOPUMZGD-TEXT-HERE</p>")
    if len(original) != len(tampered):
        raise SystemExit("swap fixture requires equal-length documents")
    entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "OEBPS/content.opf": opf(
            title="Deflate Swap",
            manifest_items=(
                '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
                '    <item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/>\n'
            ),
            spine_items='    <itemref idref="c1"/>\n',
        ).encode(),
        "OEBPS/nav.xhtml": nav([("text/c1.xhtml", "One")]).encode(),
        "OEBPS/text/c1.xhtml": original.encode(),
    }
    write_epub("deflate-swap.epub", entries)
    # Rebuild the archive with the tampered compressed stream spliced in, preserving the original
    # CRC and sizes so only a CRC check can detect it.
    path = HERE / "deflate-swap.epub"
    _splice_deflate_stream(path, "OEBPS/text/c1.xhtml", original.encode(), tampered.encode())
    print(f"{'deflate-swap.epub':<28} stream replaced (same length)")


def _splice_deflate_stream(path: Path, entry_name: str, original: bytes, replacement: bytes) -> None:
    """Rewrites a deflated entry with a same-length compressed stream, keeping the old CRC."""
    import struct
    import zlib

    compressed = zlib.compress(replacement)[2:-4]  # raw deflate
    data = bytearray(path.read_bytes())
    name_bytes = entry_name.encode()
    index = 0
    while True:
        index = data.find(b"PK\x01\x02", index)
        if index < 0:
            raise SystemExit(f"central header not found for {entry_name}")
        name_start = index + 46
        name_len = int.from_bytes(data[index + 28:index + 30], "little")
        if bytes(data[name_start:name_start + name_len]) == name_bytes:
            local_offset = int.from_bytes(data[index + 42:index + 46], "little")
            local_name_len = int.from_bytes(data[local_offset + 26:local_offset + 28], "little")
            local_extra_len = int.from_bytes(data[local_offset + 28:local_offset + 30], "little")
            data_start = local_offset + 30 + local_name_len + local_extra_len
            original_compressed = zlib.compress(original)[2:-4]
            if len(compressed) != len(original_compressed):
                raise SystemExit(
                    f"compressed lengths differ ({len(compressed)} vs {len(original_compressed)}); "
                    "choose different fixture text"
                )
            data[data_start:data_start + len(compressed)] = compressed
            # Deliberately leave the central-directory CRC and sizes untouched.
            path.write_bytes(bytes(data))
            return
        index += 4


def empty_encryption() -> None:
    """A legal, empty encryption.xml must not block opening."""
    entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "META-INF/encryption.xml": (
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"\n'
            '            xmlns:enc="http://www.w3.org/2001/04/xmlenc#"/>\n'
        ).encode(),
        "OEBPS/content.opf": opf(
            title="Empty Encryption",
            manifest_items=(
                '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
                '    <item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/>\n'
            ),
            spine_items='    <itemref idref="c1"/>\n',
        ).encode(),
        "OEBPS/nav.xhtml": nav([("text/c1.xhtml", "One")]).encode(),
        "OEBPS/text/c1.xhtml": xhtml("One", "<h1>One</h1><p>readable</p>").encode(),
    }
    write_epub("empty-encryption.epub", entries)


def nav_mixed() -> None:
    """One direct <ol> and one <ol> wrapped in a div; both must contribute entries."""
    entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "OEBPS/content.opf": opf(
            title="Mixed Nav",
            manifest_items="".join(
                f'    <item id="{i}" href="text/{i}.xhtml" media-type="application/xhtml+xml"/>\n'
                for i in ("c1", "c2")
            )
            + '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n',
            spine_items="".join(f'    <itemref idref="{i}"/>\n' for i in ("c1", "c2")),
        ).encode(),
        "OEBPS/nav.xhtml": (
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">\n'
            "<head><title>Nav</title></head><body><nav epub:type=\"toc\">\n"
            '<ol><li><a href="text/c1.xhtml">Direct A</a></li></ol>\n'
            '<div><ol><li><a href="text/c2.xhtml">Wrapped B</a></li></ol></div>\n'
            "</nav></body></html>\n"
        ).encode(),
        "OEBPS/text/c1.xhtml": xhtml("A", "<p>a</p>").encode(),
        "OEBPS/text/c2.xhtml": xhtml("B", "<p>b</p>").encode(),
    }
    write_epub("nav-mixed.epub", entries)


def block_image_and_ruby() -> None:
    """Block-level linked image and ruby annotation must survive conversion."""
    entries = {
        "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
        "OEBPS/content.opf": opf(
            title="Block Image",
            manifest_items=(
                '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
                '    <item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/>\n'
            ),
            spine_items='    <itemref idref="c1"/>\n',
        ).encode(),
        "OEBPS/nav.xhtml": nav([("text/c1.xhtml", "Block")]).encode(),
        "OEBPS/text/c1.xhtml": xhtml(
            "Block",
            (
                '<div class="cover"><a href="x"><img src="cover.jpg" alt="cover"/></a></div>'
                '<a href="y"><img src="plate.jpg" alt="plate"/></a>'
                '<div>これは<ruby>漢字<rp>(</rp><rt>かんじ</rt><rp>)</rp></ruby>です。</div>'
            ),
        ).encode(),
        "OEBPS/text/cover.jpg": b"\xff\xd8\xff\xe0FAKE-COVER",
        "OEBPS/text/plate.jpg": b"\xff\xd8\xff\xe0FAKE-PLATE",
    }
    write_epub("block-image.epub", entries)


def encrypted_variants() -> None:
    """Encrypted content that omits or mangles parts of the encryption record."""
    def book(*, encryption: str) -> dict[str, bytes]:
        return {
            "META-INF/container.xml": CONTAINER.format(opf="OEBPS/content.opf").encode(),
            "META-INF/encryption.xml": encryption.encode(),
            "OEBPS/content.opf": opf(
                title="Encrypted Variant",
                manifest_items=(
                    '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
                    '    <item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/>\n'
                ),
                spine_items='    <itemref idref="c1"/>\n',
            ).encode(),
            "OEBPS/nav.xhtml": nav([("text/c1.xhtml", "One")]).encode(),
            "OEBPS/text/c1.xhtml": xhtml("One", "<p>CIPHERTEXT-SHOULD-NOT-BE-SERVED</p>").encode(),
        }

    ns = 'xmlns="urn:oasis:names:tc:opendocument:xmlns:container" xmlns:enc="http://www.w3.org/2001/04/xmlenc#"'

    write_epub("encrypted-no-method.epub", book(encryption=(
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        f"<encryption {ns}>\n"
        "  <enc:EncryptedData>\n"
        '    <enc:CipherData><enc:CipherReference URI="OEBPS/text/c1.xhtml"/></enc:CipherData>\n'
        "  </enc:EncryptedData>\n"
        "</encryption>\n"
    )))

    write_epub("encrypted-no-cipherref.epub", book(encryption=(
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        f"<encryption {ns}>\n"
        "  <enc:EncryptedData>\n"
        '    <enc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes256-cbc"/>\n'
        "    <enc:CipherData/>\n"
        "  </enc:EncryptedData>\n"
        "</encryption>\n"
    )))

    write_epub("encrypted-lowercase.epub", book(encryption=(
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        f"<encryption {ns}>\n"
        "  <enc:encrypteddata>\n"
        '    <enc:cipherdata><enc:cipherreference URI="OEBPS/text/c1.xhtml"/></enc:cipherdata>\n'
        "  </enc:encrypteddata>\n"
        "</encryption>\n"
    )))


def main() -> None:
    spine_order()
    refs()
    corrupted()
    missing_entry()
    unsafe_ref()
    encrypted()
    encrypted_font_only()
    textloss()
    nav_landmarks()
    nav_prefixed()
    nav_groups()
    dup_spine()
    opf_prefixed()
    images_in_links()
    no_nav()
    encrypted_slash_uri()
    oversized_metadata()
    declared_size_and_crc()
    big_central_directory_fixtures()
    deflate_swap()
    empty_encryption()
    nav_mixed()
    block_image_and_ruby()
    encrypted_variants()


if __name__ == "__main__":
    main()
