#!/usr/bin/env python3
"""Generate the Ogg Opus test fixtures in repo-root testdata/.

Creates (write-if-absent; delete a file to regenerate it):
  test-opus.opus              3 s stereo sine, tags, OpusHead input rate patched to 44100
  test-opus-padding.opus      same stream with libopusenc-sized zero padding on OpusTags
  test-opus-binary-tail.opus  same stream with RFC 7845 binary data after the comments
  test-opus-in-ogg.ogg        byte copy of test-opus.opus
  test-opus-shared-page.opus  OpusTags merged onto the first audio page (RFC-violating)
  test-opus-track-total.opus  stream copy of test-opus.opus with TRACKNUMBER=3/12 DISCNUMBER=1/2

Run once by hand; the tests treat the files as ground truth. Requires ffmpeg on PATH.
The Ogg CRC is CRC-32 with polynomial 0x04C11DB7, init 0, no reflection, no final xor,
computed over the page with its CRC field zeroed.
"""

from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TESTDATA = ROOT / "testdata"

CAPTURE = b"OggS"
OPUS_HEAD = b"OpusHead"
INPUT_RATE_OFFSET = 12
PRE_SKIP_OFFSET = 10


def _crc_table() -> list[int]:
    table = []
    for i in range(256):
        r = i << 24
        for _ in range(8):
            if r & 0x80000000:
                r = ((r << 1) ^ 0x04C11DB7) & 0xFFFFFFFF
            else:
                r = (r << 1) & 0xFFFFFFFF
        table.append(r)
    return table


CRC_TABLE = _crc_table()


def ogg_crc(data: bytes) -> bytes:
    crc = 0
    for byte in data:
        index = ((crc >> 24) ^ byte) & 0xFF
        crc = ((crc << 8) ^ CRC_TABLE[index]) & 0xFFFFFFFF
    return crc.to_bytes(4, "little")


def stamp_crc(page: bytearray) -> None:
    page[22:26] = b"\x00\x00\x00\x00"
    page[22:26] = ogg_crc(bytes(page))


class Page:
    def __init__(self, offset: int, header: bytes, body: bytes):
        self.offset = offset
        self.header = bytearray(header)
        self.body = bytearray(body)

    @property
    def nseg(self) -> int:
        return self.header[26]

    @property
    def segs(self) -> bytes:
        return bytes(self.header[27 : 27 + self.nseg])

    @property
    def flags(self) -> int:
        return self.header[5]

    @property
    def granule(self) -> int:
        return int.from_bytes(self.header[6:14], "little", signed=True)

    @property
    def sequence(self) -> int:
        return int.from_bytes(self.header[18:22], "little", signed=False)

    def raw(self) -> bytes:
        return bytes(self.header) + bytes(self.body)


def parse_pages(data: bytes) -> list[Page]:
    pages = []
    pos = 0
    while pos < len(data):
        if data[pos : pos + 4] != CAPTURE:
            raise ValueError(f"OggS not found at {pos}")
        nseg = data[pos + 26]
        header_len = 27 + nseg
        body_len = sum(data[pos + 27 : pos + header_len])
        header = data[pos : pos + header_len]
        body = data[pos + header_len : pos + header_len + body_len]
        pages.append(Page(pos, header, body))
        pos += header_len + body_len
    if pos != len(data):
        raise ValueError("pages do not end at EOF")
    return pages


def rebuild_page(
    template: Page,
    body: bytes,
    segs: bytes | None = None,
    sequence: int | None = None,
    granule: int | None = None,
    flags: int | None = None,
) -> bytes:
    if segs is None:
        segs = lacing(len(body))
    if len(segs) > 255:
        raise ValueError(f"segment table too long: {len(segs)}")
    header = bytearray(27)
    header[0:4] = CAPTURE
    header[4] = template.header[4]
    header[5] = template.flags if flags is None else flags
    granule_value = template.granule if granule is None else granule
    header[6:14] = granule_value.to_bytes(8, "little", signed=True)
    header[14:18] = template.header[14:18]
    sequence_value = template.sequence if sequence is None else sequence
    header[18:22] = sequence_value.to_bytes(4, "little")
    header[22:26] = b"\x00\x00\x00\x00"
    header[26] = len(segs)
    page = bytearray(header) + segs + body
    stamp_crc(page)
    return bytes(page)


def lacing(length: int) -> bytes:
    if length == 0:
        return b"\x00"
    segs = []
    remaining = length
    while remaining >= 255:
        segs.append(255)
        remaining -= 255
    segs.append(remaining)
    return bytes(segs)


def restamp_sequence(page: Page, sequence: int) -> bytes:
    header = bytearray(page.header)
    header[18:22] = sequence.to_bytes(4, "little")
    raw = bytearray(header) + page.body
    stamp_crc(raw)
    return bytes(raw)


def run(cmd: list[str], **kwargs) -> subprocess.CompletedProcess:
    result = subprocess.run(cmd, check=False, **kwargs)
    if result.returncode != 0:
        raise RuntimeError(f"command failed ({result.returncode}): {' '.join(cmd)}")
    return result


def generate_base(path: Path) -> None:
    run(
        [
            "ffmpeg",
            "-y",
            "-fflags",
            "+bitexact",
            "-f",
            "lavfi",
            "-i",
            "sine=frequency=440:sample_rate=48000:duration=3",
            "-ac",
            "2",
            "-c:a",
            "libopus",
            "-b:a",
            "64k",
            "-flags:a",
            "+bitexact",
            "-metadata",
            "title=Opus Title",
            "-metadata",
            "artist=Opus Artist",
            "-metadata",
            "album=Opus Album",
            "-metadata",
            "genre=Rock",
            "-metadata",
            "track=3",
            "-metadata",
            "TRACKTOTAL=12",
            str(path),
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
    )


def patch_input_sample_rate(data: bytes, rate: int) -> bytes:
    pages = parse_pages(data)
    page0 = pages[0]
    if page0.body[:8] != OPUS_HEAD:
        raise ValueError("page 0 is not OpusHead")
    body = bytearray(page0.body)
    body[INPUT_RATE_OFFSET : INPUT_RATE_OFFSET + 4] = rate.to_bytes(4, "little")
    rebuilt = rebuild_page(page0, bytes(body), segs=page0.segs)
    return rebuilt + data[pages[1].offset :]


def pad_comment(data: bytes) -> bytes:
    pages = parse_pages(data)
    page1 = pages[1]
    body = bytes(page1.body)
    packet_length = (len(body) + 512 + 255) // 255 * 255 - 1
    padded = body + b"\x00" * (packet_length - len(body))
    rebuilt = rebuild_page(page1, padded)
    return data[: page1.offset] + rebuilt + data[pages[2].offset :]


def append_binary_tail(data: bytes) -> bytes:
    pages = parse_pages(data)
    page1 = pages[1]
    body = bytes(page1.body) + b"\x01binary-tail"
    rebuilt = rebuild_page(page1, body)
    return data[: page1.offset] + rebuilt + data[pages[2].offset :]


def merge_comment_with_audio(data: bytes) -> bytes:
    pages = parse_pages(data)
    page1 = pages[1]
    page2 = pages[2]
    segs = page1.segs + page2.segs
    if len(segs) > 255:
        raise AssertionError("merged lacing table exceeds 255 values")
    body = bytes(page1.body) + bytes(page2.body)
    merged = rebuild_page(
        page1,
        body,
        segs=segs,
        sequence=page1.sequence,
        granule=page2.granule,
        flags=page1.flags,
    )
    out = bytearray(data[: page1.offset] + merged)
    for page in pages[3:]:
        out += restamp_sequence(page, page.sequence - 1)
    return bytes(out)


def validate_with_ffmpeg(path: Path) -> None:
    result = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", str(path), "-f", "null", "-"],
        check=False,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
    )
    if result.returncode != 0 or result.stderr:
        raise RuntimeError(
            f"ffmpeg rejected {path.name} (exit {result.returncode}): {result.stderr.decode()}"
        )


def decoded_samples_per_channel(path: Path, channels: int) -> int:
    result = subprocess.run(
        [
            "ffmpeg",
            "-v",
            "error",
            "-i",
            str(path),
            "-map",
            "0:a",
            "-f",
            "s16le",
            "-acodec",
            "pcm_s16le",
            "-",
        ],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if result.returncode != 0 or result.stderr:
        raise RuntimeError(
            f"ffmpeg decode of {path.name} failed (exit {result.returncode}): {result.stderr.decode()}"
        )
    return len(result.stdout) // (2 * channels)


def head_pre_skip(data: bytes) -> int:
    pages = parse_pages(data)
    body = pages[0].body
    return int.from_bytes(body[PRE_SKIP_OFFSET : PRE_SKIP_OFFSET + 2], "little")


def last_granule(data: bytes) -> int:
    return parse_pages(data)[-1].granule


def write_if_absent(path: Path, producer) -> bytes:
    if path.exists():
        return path.read_bytes()
    data = producer()
    path.write_bytes(data)
    return data


def generate_track_total(src: Path, dest: Path) -> None:
    run(
        [
            "ffmpeg",
            "-y",
            "-i",
            str(src),
            "-c",
            "copy",
            "-map_metadata",
            "-1",
            "-metadata",
            "title=Opus Title",
            "-metadata",
            "track=3/12",
            "-metadata",
            "disc=1/2",
            str(dest),
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
    )


def opus_tags_body(data: bytes) -> bytes:
    for page in parse_pages(data):
        if bytes(page.body).startswith(b"OpusTags"):
            return bytes(page.body)
    raise ValueError("OpusTags packet not found")


def assert_combined_track_disc(data: bytes) -> None:
    comments = opus_tags_body(data)
    if b"TRACKNUMBER=3/12" not in comments:
        raise AssertionError("OpusTags is missing TRACKNUMBER=3/12")
    if b"DISCNUMBER=1/2" not in comments:
        raise AssertionError("OpusTags is missing DISCNUMBER=1/2")
    if b"TRACKTOTAL" in comments:
        raise AssertionError("OpusTags must not contain TRACKTOTAL")
    if b"DISCTOTAL" in comments:
        raise AssertionError("OpusTags must not contain DISCTOTAL")


def main() -> int:
    if shutil.which("ffmpeg") is None:
        print("ffmpeg not found on PATH", file=sys.stderr)
        return 1

    TESTDATA.mkdir(parents=True, exist_ok=True)
    base_path = TESTDATA / "test-opus.opus"
    if base_path.exists():
        base = base_path.read_bytes()
    else:
        generate_base(base_path)
        base = patch_input_sample_rate(base_path.read_bytes(), 44100)
        base_path.write_bytes(base)

    padding_path = TESTDATA / "test-opus-padding.opus"
    write_if_absent(padding_path, lambda: pad_comment(base))

    binary_path = TESTDATA / "test-opus-binary-tail.opus"
    write_if_absent(binary_path, lambda: append_binary_tail(base))

    ogg_path = TESTDATA / "test-opus-in-ogg.ogg"
    write_if_absent(ogg_path, lambda: base)

    shared_path = TESTDATA / "test-opus-shared-page.opus"
    write_if_absent(shared_path, lambda: merge_comment_with_audio(base))

    track_total_path = TESTDATA / "test-opus-track-total.opus"
    if not track_total_path.exists():
        generate_track_total(base_path, track_total_path)
        assert_combined_track_disc(track_total_path.read_bytes())
    else:
        assert_combined_track_disc(track_total_path.read_bytes())

    fixtures = [base_path, padding_path, binary_path, ogg_path, shared_path, track_total_path]
    print("fixture\tsamples/ch\tpre-skip\tlast granule")
    for path in fixtures:
        validate_with_ffmpeg(path)
        data = path.read_bytes()
        pages = parse_pages(data)
        channels = pages[0].body[9]
        samples = decoded_samples_per_channel(path, channels)
        pre_skip = head_pre_skip(data)
        granule = last_granule(data)
        print(f"{path.name}\t{samples}\t{pre_skip}\t{granule}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
