#!/usr/bin/env python3
"""Generates the Kotlin FieldKey -> MP4 atom mapping from the Java sources."""
import re

FIELDKEY_SRC = "../src/org/jaudiotagger/tag/mp4/Mp4FieldKey.java"
TAG_SRC = "../src/org/jaudiotagger/tag/mp4/Mp4Tag.java"
OUT = "src/commonMain/kotlin/org/jaudiotagger/kt/tag/mp4/Mp4FrameMappings.kt"

def read(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()

# Mp4FieldKey entries, two shapes:
#   NAME("atom", Mp4TagFieldSubType.X, TYPE[, ...])
#   NAME("issuer", "identifier", TYPE[, ...])       -- reverse-DNS "----" atoms
entries = {}
for m in re.finditer(
    r'^\s{4}([A-Z0-9_]+)\("([^"]+)",\s*(?:"([^"]+)",)?\s*([A-Za-z0-9_.]+)',
    read(FIELDKEY_SRC), re.M,
):
    name, first, second, third = m.groups()
    if second is not None:
        entries[name] = ("----", first, second)  # reverse-dns: issuer + identifier
    else:
        entries[name] = (first, None, None)

pairs = re.findall(
    r'tagFieldToMp4Field\.put\(FieldKey\.([A-Z0-9_]+),\s*Mp4FieldKey\.([A-Z0-9_]+)\)', read(TAG_SRC)
)

out = []
out.append("package org.jaudiotagger.kt.tag.mp4")
out.append("")
out.append("import org.jaudiotagger.kt.tag.FieldKey")
out.append("")
out.append("/**")
out.append(" * Where a [FieldKey] lives in an MP4 ilst box: either a plain atom (e.g. ©nam)")
out.append(" * or a reverse-DNS \"----\" atom addressed by issuer + identifier.")
out.append(" *")
out.append(" * Generated from Mp4FieldKey/Mp4Tag in the Java sources; do not edit.")
out.append(" */")
out.append("class Mp4FrameKey(val atomId: String, val issuer: String? = null, val identifier: String? = null)")
out.append("")
out.append("internal val mp4FrameKeys: Map<FieldKey, Mp4FrameKey> = mapOf(")
missing = []
for field_key, mp4_key in pairs:
    if mp4_key not in entries:
        missing.append(mp4_key)
        continue
    atom, issuer, identifier = entries[mp4_key]
    if issuer is not None:
        out.append(f'    FieldKey.{field_key} to Mp4FrameKey("{atom}", "{issuer}", "{identifier}"),')
    else:
        out.append(f'    FieldKey.{field_key} to Mp4FrameKey("{atom}"),')
out.append(")")
out.append("")

if missing:
    print(f"WARNING unresolved: {missing}")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(out))
print(f"wrote {OUT}: {len(out)} lines")
