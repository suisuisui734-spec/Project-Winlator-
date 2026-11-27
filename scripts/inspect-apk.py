#!/usr/bin/env python3
"""Inspect APK metadata, native ABI payload, and large bundled assets."""

import argparse
import json
import re
import sys
import zipfile
from collections import defaultdict
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--badging", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    if not args.apk.is_file():
        raise SystemExit(f"APK not found: {args.apk}")
    if not args.badging.is_file():
        raise SystemExit(f"aapt badging output not found: {args.badging}")

    badging = args.badging.read_text(encoding="utf-8", errors="replace")
    match = re.search(
        r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'",
        badging,
        re.MULTILINE,
    )
    if not match:
        raise SystemExit("aapt did not report valid package/version metadata")

    with zipfile.ZipFile(args.apk) as apk:
        entries = [item for item in apk.infolist() if not item.is_dir()]
        names = {item.filename for item in entries}
        if "AndroidManifest.xml" not in names:
            raise SystemExit("APK is missing AndroidManifest.xml")

        native = [
            item for item in entries
            if item.filename.startswith("lib/") and item.filename.endswith(".so")
        ]
        abis = sorted({
            item.filename.split("/")[1]
            for item in native if len(item.filename.split("/")) > 2
        })
        if "arm64-v8a" not in abis:
            raise SystemExit(f"APK does not contain arm64-v8a native libraries: {abis}")
        if any(abi != "arm64-v8a" for abi in abis):
            raise SystemExit(f"Unexpected native ABI packaged: {abis}")

        asset_groups = defaultdict(lambda: {
            "entries": 0, "uncompressed_bytes": 0, "compressed_bytes": 0
        })
        assets = [item for item in entries if item.filename.startswith("assets/")]
        for item in assets:
            parts = item.filename.split("/")
            group = parts[1] if len(parts) > 1 else "(root)"
            asset_groups[group]["entries"] += 1
            asset_groups[group]["uncompressed_bytes"] += item.file_size
            asset_groups[group]["compressed_bytes"] += item.compress_size

        largest = sorted(entries, key=lambda item: item.file_size, reverse=True)[:20]
        report = {
            "apk_path": str(args.apk),
            "apk_size_bytes": args.apk.stat().st_size,
            "package_name": match.group(1),
            "version_code": match.group(2),
            "version_name": match.group(3),
            "zip_entry_count": len(entries),
            "native_abis": abis,
            "native_library_count": len(native),
            "native_library_uncompressed_bytes": sum(item.file_size for item in native),
            "asset_entry_count": len(assets),
            "asset_uncompressed_bytes": sum(item.file_size for item in assets),
            "asset_compressed_bytes": sum(item.compress_size for item in assets),
            "asset_groups": dict(sorted(asset_groups.items())),
            "largest_entries": [
                {
                    "path": item.filename,
                    "uncompressed_bytes": item.file_size,
                    "compressed_bytes": item.compress_size,
                }
                for item in largest
            ],
        }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
