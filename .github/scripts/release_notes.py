"""Extract one version's notes, or sync existing Releases without touching assets."""
import argparse
import json
import os
import re
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


def sections(text):
    headings = list(re.finditer(r"^# (v\S+)\s*$", text, re.MULTILINE))
    result = {}
    for index, heading in enumerate(headings):
        end = headings[index + 1].start() if index + 1 < len(headings) else len(text)
        body = text[heading.start():end].strip()
        body = re.sub(r"\n---\s*$", "", body).strip()
        if heading[1] in result:
            raise ValueError(f"Duplicate changelog version: {heading[1]}")
        result[heading[1]] = body + "\n"
    return result


def sync_releases(notes):
    repository = os.environ["GITHUB_REPOSITORY"]
    token = os.environ["GITHUB_TOKEN"]
    base = f"https://api.github.com/repos/{repository}/releases"
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def request(url, payload=None):
        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8") if payload is not None else None,
            method="PATCH" if payload is not None else "GET",
            headers={"Authorization": f"Bearer {token}",
                     "Accept": "application/vnd.github+json",
                     "Content-Type": "application/json",
                     "X-GitHub-Api-Version": "2022-11-28"},
        )
        with opener.open(req, timeout=30) as response:
            return json.load(response)

    for tag, body in notes.items():
        try:
            release = request(base + "/tags/" + urllib.parse.quote(tag, safe=""))
        except urllib.error.HTTPError as error:
            if error.code == 404:
                print(f"{tag}: not published; skipped")
                continue
            raise
        if release.get("draft"):
            print(f"{tag}: draft; skipped")
            continue
        if release.get("body", "").replace("\r\n", "\n").strip() == body.strip():
            print(f"{tag}: already up to date")
            continue
        updated = request(base + "/" + str(release["id"]), {"body": body})
        if updated.get("body", "").replace("\r\n", "\n").strip() != body.strip():
            raise RuntimeError(f"{tag}: release note verification failed")
        print(f"{tag}: updated notes; APK assets unchanged")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag")
    parser.add_argument("--output")
    parser.add_argument("--sync", action="store_true")
    args = parser.parse_args()
    notes = sections(Path("CHANGELOG.md").read_text(encoding="utf-8"))
    if args.sync:
        sync_releases(notes)
    elif args.tag and args.output:
        if args.tag not in notes:
            raise SystemExit(f"No changelog entry for {args.tag}")
        Path(args.output).write_text(notes[args.tag], encoding="utf-8")
    else:
        parser.error("Use --sync, or --tag VERSION --output FILE")
