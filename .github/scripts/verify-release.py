"""Verify the signed universal APK before making a GitHub Release public."""

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def inspect_tool(tool, *args):
    result = subprocess.run([str(tool), *args], capture_output=True, text=True)
    require(result.returncode == 0, f"APK verification tool failed: {tool.name}")
    return result.stdout


def main():
    apk, metadata, tools = map(Path, sys.argv[1:])
    tag = os.environ["JOYFIN_RELEASE_TAG"]
    expected = os.environ["JOYFIN_SIGNING_CERT_SHA256"].replace(":", "").strip().lower()
    require(re.fullmatch(r"[0-9a-f]{64}", expected), "Invalid signing certificate SHA-256")
    output = json.loads(metadata.read_text(encoding="utf-8"))
    require(output["applicationId"] == "dev.joyfin", "Unexpected application ID")
    require(len(output["elements"]) == 1, "Expected a single universal APK")
    element = output["elements"][0]
    require(not element["filters"], "Split APKs are not supported")
    require(tag == "v" + element["versionName"], "Tag does not match APK metadata")
    require(element["versionCode"] > 0, "Invalid versionCode")

    certificates = inspect_tool(tools / "apksigner", "verify", "--verbose", "--print-certs", str(apk))
    digests = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$", certificates, re.MULTILINE)
    require(len(digests) == 1 and digests[0].lower() == expected, "APK signing certificate does not match")

    badging = inspect_tool(tools / "aapt2", "dump", "badging", str(apk))
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.MULTILINE)
    require(package is not None and package.groups() == (
        "dev.joyfin", str(element["versionCode"]), element["versionName"],
    ), "APK package/version does not match Gradle metadata")
    require("sdkVersion:'28'" in badging, "APK must support Android 9+")
    require("application-debuggable" not in badging, "Release APK must not be debuggable")
    manifest = inspect_tool(tools / "aapt2", "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk))
    require("dev.joyfin.scenario" not in manifest and ":ui_scenario" not in manifest,
            "Release APK must not expose the UI scenario entry")
    with zipfile.ZipFile(apk) as archive:
        abis = {match.group(1) for name in archive.namelist()
                if (match := re.fullmatch(r"lib/([^/]+)/[^/]+\.so", name))}
    require(abis == {"armeabi-v7a", "arm64-v8a", "x86", "x86_64"}, "APK must include all four ABIs")
    with apk.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    require((apk.parent / "SHA256SUMS.txt").read_text().strip() == f"{digest}  {apk.name}",
            "APK checksum does not match")
    (apk.parent / "SIGNING-CERT-SHA256.txt").write_text(expected + "\n", encoding="utf-8")
    print("Verified release version, certificate, checksum, manifest and four ABIs")


if __name__ == "__main__":
    main()
