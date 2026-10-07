#!/usr/bin/env python3
"""Generates Kotlin FieldKey -> ID3 frame key mappings from the Java sources."""
import re
import sys

SRC = "../src/org/jaudiotagger/tag/id3"
OUT = "src/commonMain/kotlin/org/jaudiotagger/kt/tag/id3/Id3FrameMappings.kt"

def read(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()

def parse_string_constants(text, clazz):
    """FRAME_ID_X = "TALB" style constants, qualified by class name."""
    consts = {}
    for m in re.finditer(r'String\s+([A-Z0-9_]+)\s*=\s*"([^"]*)"', text):
        consts[f"{clazz}.{m.group(1)}"] = m.group(2)
    return consts

consts = {}
consts.update(parse_string_constants(read(f"{SRC}/ID3v24Frames.java"), "ID3v24Frames"))
consts.update(parse_string_constants(read(f"{SRC}/ID3v23Frames.java"), "ID3v23Frames"))
consts.update(parse_string_constants(read(f"{SRC}/ID3v22Frames.java"), "ID3v22Frames"))
consts.update(parse_string_constants(read(f"{SRC}/framebody/FrameBodyTXXX.java"), "FrameBodyTXXX"))
consts.update(parse_string_constants(read(f"{SRC}/framebody/FrameBodyCOMM.java"), "FrameBodyCOMM"))
consts.update(parse_string_constants(read(f"{SRC}/framebody/FrameBodyWXXX.java"), "FrameBodyWXXX"))
consts.update(parse_string_constants(read(f"{SRC}/framebody/FrameBodyUFID.java"), "FrameBodyUFID"))
consts.update(parse_string_constants(read(f"{SRC}/framebody/FrameBodyIPLS.java"), "FrameBodyIPLS"))
consts.update(parse_string_constants(read(f"{SRC}/framebody/FrameBodyTIPL.java"), "FrameBodyTIPL"))

# StandardIPLSKey enum: NAME("value")
ipls = read("../src/org/jaudiotagger/tag/id3/valuepair/StandardIPLSKey.java")
for m in re.finditer(r'^\s*([A-Z0-9_]+)\("([^"]*)"\)', ipls, re.M):
    consts[f"StandardIPLSKey.{m.group(1)}.getKey()"] = m.group(2)

def resolve(expr):
    expr = expr.strip()
    if expr in consts:
        return consts[expr]
    # unqualified constant referenced within its own class file
    for key, val in consts.items():
        if key.endswith("." + expr):
            return val
    raise KeyError(expr)

def parse_field_key_enum(path):
    """NAME(frameExpr[, subExpr], Id3FieldType.X), -> name: (frame, sub, type)"""
    entries = {}
    text = read(path)
    for m in re.finditer(
        r'^\s{4}([A-Z0-9_]+)\(([^)]*?)Id3FieldType\.([A-Z_]+)\)\s*[,;]*\s*$', text, re.M
    ):
        name, args, ftype = m.group(1), m.group(2).rstrip().rstrip(','), m.group(3)
        parts = [p.strip() for p in args.split(',') if p.strip()]
        frame = resolve(parts[0])
        sub = resolve(parts[1]) if len(parts) > 1 else None
        entries[name] = (frame, sub, ftype)
    return entries

def parse_tag_field_map(path, enum_name):
    pairs = []
    for m in re.finditer(
        r'tagFieldToId3\.put\(FieldKey\.([A-Z0-9_]+),\s*' + enum_name + r'\.([A-Z0-9_]+)\)', read(path)
    ):
        pairs.append((m.group(1), m.group(2)))
    return pairs

versions = [
    ("id3v24FrameKeys", f"{SRC}/ID3v24FieldKey.java", f"{SRC}/ID3v24Frames.java", "ID3v24FieldKey"),
    ("id3v23FrameKeys", f"{SRC}/ID3v23FieldKey.java", f"{SRC}/ID3v23Frames.java", "ID3v23FieldKey"),
    ("id3v22FrameKeys", f"{SRC}/ID3v22FieldKey.java", f"{SRC}/ID3v22Frames.java", "ID3v22FieldKey"),
]

out = []
out.append("package org.jaudiotagger.kt.tag.id3")
out.append("")
out.append("import org.jaudiotagger.kt.tag.FieldKey")
out.append("")
out.append("/**")
out.append(" * Where a [FieldKey] lives in an ID3v2 tag: the frame id plus, for container")
out.append(" * frames (TXXX/WXXX/COMM/IPLS...), the sub-key (description) inside it.")
out.append(" *")
out.append(" * Generated from ID3v2xFieldKey/ID3v2xFrames in the Java sources; do not edit.")
out.append(" */")
out.append("class Id3FrameKey(val frameId: String, val subId: String? = null, val isBinary: Boolean = false)")
out.append("")

for kotlin_name, fieldkey_path, frames_path, enum_name in versions:
    entries = parse_field_key_enum(fieldkey_path)
    pairs = parse_tag_field_map(frames_path, enum_name)
    missing = [e for _, e in pairs if e not in entries]
    if missing:
        print(f"WARNING {enum_name}: unresolved {missing}", file=sys.stderr)
    out.append(f"internal val {kotlin_name}: Map<FieldKey, Id3FrameKey> = mapOf(")
    for field_key, id3_key in pairs:
        if id3_key not in entries:
            continue
        frame, sub, ftype = entries[id3_key]
        args = [f'"{frame}"']
        if sub is not None:
            args.append(f'"{sub}"')
        if ftype == "BINARY":
            args.append("isBinary = true")
        out.append(f"    FieldKey.{field_key} to Id3FrameKey({', '.join(args)}),")
    out.append(")")
    out.append("")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(out))
print(f"wrote {OUT}: {len(out)} lines")
