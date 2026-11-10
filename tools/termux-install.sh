#!/data/data/com.termux/files/usr/bin/bash
# Downloads the newest plainDebug APK built by the "Fork APK" workflow and
# installs it on this phone. Run from Termux.
#
# One-time setup:
#   pkg install gh jq android-tools
#   gh auth login
#   Optional, for silent installs: enable Developer options > Wireless debugging,
#   then `adb pair localhost:<pairing port>` and `adb connect localhost:<port>`.
#   Without adb the script opens the system installer instead.
#
# Usage: termux-install.sh [branch]
#   ANDROIDBIBLE_REPO overrides the repository (default tobiowo/androidbible).
set -euo pipefail

repo="${ANDROIDBIBLE_REPO:-tobiowo/androidbible}"
branch="${1:-}"

list_args=(--repo "$repo" --workflow fork-apk.yml --status success --limit 1
  --json databaseId,headBranch,headSha,createdAt)
if [[ -n "$branch" ]]; then
  list_args+=(--branch "$branch")
fi

run_json="$(gh run list "${list_args[@]}")"
run_id="$(jq -r '.[0].databaseId // empty' <<<"$run_json")"
if [[ -z "$run_id" ]]; then
  echo "No successful Fork APK run found in $repo${branch:+ on branch $branch}." >&2
  exit 1
fi
jq -r '.[0] | "Run \(.databaseId): \(.headBranch) @ \(.headSha[0:7]), built \(.createdAt)"' <<<"$run_json"

dest="$HOME/androidbible-apk"
rm -rf "$dest"
gh run download "$run_id" --repo "$repo" --name plain-debug-apk --dir "$dest"
apk="$(find "$dest" -name '*.apk' -print -quit)"
echo "Downloaded $apk"

if command -v adb >/dev/null && adb get-state >/dev/null 2>&1; then
  adb install -r "$apk"
else
  # The system installer cannot read Termux's private storage.
  shared="$HOME/storage/downloads"
  if [[ -d "$shared" ]]; then
    cp "$apk" "$shared/"
    apk="$shared/$(basename "$apk")"
  fi
  termux-open "$apk"
fi
