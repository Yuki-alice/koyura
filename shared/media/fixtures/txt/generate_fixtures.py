#!/usr/bin/env python3
"""Regenerate the TXT fixtures used by ``shared/media`` tests.

The files are written as raw bytes so the checked-in fixtures keep the exact
encoding, byte order mark and line endings the tests rely on. Run from any
directory:

    python3 shared/media/fixtures/txt/generate_fixtures.py

The script is deterministic; re-running it must not change any file content.
"""

import hashlib
from pathlib import Path

HERE = Path(__file__).resolve().parent

UTF8_BOM = b"\xef\xbb\xbf"
UTF16_LE_BOM = b"\xff\xfe"
UTF16_BE_BOM = b"\xfe\xff"

SAMPLE_TEXT = (
    "长夜行 · 补充平面字符\n"
    "\n"
    "𠮷野家的猫在屋顶打盹。😀\n"
    "雨声落在瓦上，像一首慢歌。\n"
)

CHAPTERS_TEXT = (
    "长夜行\n"
    "作者：佚名\n"
    "\n"
    "第一卷 启程\n"
    "\n"
    "第一章 夜雨\n"
    "　　雨下了一整夜。\n"
    "　　林舟推开木门，看见檐下站着一个陌生人。\n"
    "\n"
    "第二章 旧信\n"
    "　　信纸已经泛黄，落款处只剩一个模糊的字。\n"
    "\n"
    "第二卷 远行\n"
    "\n"
    "第三章 归途\n"
    "　　天亮了，河面上浮着薄雾。\n"
    "\n"
    "第一节 灯火\n"
    "　　远处的灯火一盏一盏亮起。\n"
)

HEADINGS_TEXT = (
    "序章 起风\n"
    "　　风从北边来。\n"
    "\n"
    "楔子\n"
    "　　旧事重提。\n"
    "\n"
    "前言\n"
    "　　写在前面的话。\n"
    "\n"
    "第一章 初遇\n"
    "　　她在渡口等他。\n"
    "\n"
    "第一节 试探\n"
    "　　话没有说完。\n"
    "\n"
    "第二回 重逢\n"
    "　　三年之后。\n"
    "\n"
    "第一篇 旧梦\n"
    "　　梦醒时分。\n"
    "\n"
    "第4章 数字编号\n"
    "　　阿拉伯数字的章节号。\n"
    "\n"
    "后记\n"
    "　　谢谢阅读。\n"
    "\n"
    "尾声\n"
    "　　春天来了。\n"
    "\n"
    "番外一 春日\n"
    "　　他们养了一只猫。\n"
)

NO_HEADINGS_TEXT = (
    "这是没有章节标题的完整正文。\n"
    "\n"
    "第一段内容，讲述清晨的渡口。\n"
    "第二段内容，讲到午后的雨。\n"
    "第三段内容，包含补充平面字符：𠮷野和 😀。\n"
    "结尾直接结束，没有任何章标题行。\n"
)

IMAGE_REF_TEXT = (
    "插图测试\n"
    "\n"
    "第一章 有图\n"
    "\n"
    "正文之前。\n"
    "\n"
    "***\n"
    "\n"
    "![渡口](images/ferry.png)\n"
    "\n"
    "正文之后。\n"
)

INVALID_UTF8 = (
    "长夜行\n".encode("utf-8")
    + "你好".encode("utf-8")
    + b"\xc3\x28"
    + "\n后续内容。\n".encode("utf-8")
)

UNSUPPORTED_ZIP_LIKE = b"PK\x03\x04\x14\x00\x00\x00\x08\x00not a novel\x00\x00"


def write(name: str, data: bytes) -> None:
    path = HERE / name
    path.write_bytes(data)
    print(f"{name:<28} {len(data):>6} bytes  sha256={hashlib.sha256(data).hexdigest()}")


def main() -> None:
    utf8 = SAMPLE_TEXT.encode("utf-8")
    write("sample-utf8.txt", utf8)
    write("sample-utf8-bom.txt", UTF8_BOM + utf8)
    write("sample-utf16le-bom.txt", UTF16_LE_BOM + SAMPLE_TEXT.encode("utf-16-le"))
    write("sample-utf16be-bom.txt", UTF16_BE_BOM + SAMPLE_TEXT.encode("utf-16-be"))
    write("sample-crlf.txt", SAMPLE_TEXT.replace("\n", "\r\n").encode("utf-8"))

    write("chapters-utf8.txt", CHAPTERS_TEXT.encode("utf-8"))
    write("headings-utf8.txt", HEADINGS_TEXT.encode("utf-8"))
    write("no-headings-utf8.txt", NO_HEADINGS_TEXT.encode("utf-8"))
    write("image-ref-utf8.txt", IMAGE_REF_TEXT.encode("utf-8"))

    write("empty.txt", b"")
    write("invalid-utf8.txt", INVALID_UTF8)
    write("unsupported-format.txt", UNSUPPORTED_ZIP_LIKE)


if __name__ == "__main__":
    main()
