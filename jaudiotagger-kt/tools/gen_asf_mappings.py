#!/usr/bin/env python3
"""Generates the Kotlin FieldKey -> ASF descriptor name mapping from the Java sources."""
import re

FIELDKEY_SRC = "../src/org/jaudiotagger/tag/asf/AsfFieldKey.java"
TAG_SRC = "../src/org/jaudiotagger/tag/asf/AsfTag.java"
OUT = "src/commonMain/kotlin/org/jaudiotagger/kt/tag/asf/AsfFieldMappings.kt"

def read(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()

entries = {}
for m in re.finditer(r'^\s{4}([A-Z0-9_]+)\("([^"]+)"', read(FIELDKEY_SRC), re.M):
    entries[m.group(1)] = m.group(2)
# legacy Content Description fields reference ContentDescription.KEY_* constants;
# they are addressed by the same pseudo-names in the Kotlin AsfTag
for m in re.finditer(r'^\s{4}([A-Z0-9_]+)\(ContentDescription\.KEY_([A-Z]+)', read(FIELDKEY_SRC), re.M):
    entries[m.group(1)] = m.group(2)

pairs = re.findall(
    r'tagFieldToAsfField\.put\(FieldKey\.([A-Z0-9_]+),\s*AsfFieldKey\.([A-Z0-9_]+)\)', read(TAG_SRC)
)

out = []
out.append("package org.jaudiotagger.kt.tag.asf")
out.append("")
out.append("import org.jaudiotagger.kt.tag.FieldKey")
out.append("")
out.append("/**")
out.append(" * ASF content descriptor names for common fields.")
out.append(" * Generated from AsfFieldKey/AsfTag in the Java sources; do not edit.")
out.append(" */")
out.append("internal val asfFieldNames: Map<FieldKey, String> = mapOf(")
missing = []
for field_key, asf_key in pairs:
    if asf_key not in entries:
        missing.append(asf_key)
        continue
    out.append(f'    FieldKey.{field_key} to "{entries[asf_key]}",')
out.append(")")
out.append("")

if missing:
    print(f"WARNING unresolved: {missing}")
with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(out))
print(f"wrote {OUT}: {len(out)} lines")
