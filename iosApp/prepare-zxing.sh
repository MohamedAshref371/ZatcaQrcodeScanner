#!/usr/bin/env bash
set -euo pipefail

app_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
version=v3.1.1
revision=287c85df6f961c8efbfb5ffd736cd9457b8b890e
patch="$app_dir/patches/zxing-cpp-ios.patch"
destination="$app_dir/build/ZXingCpp"

if [[ -f "$destination/Package.swift" ]] &&
   [[ "$(git -C "$destination" rev-parse HEAD 2>/dev/null)" == "$revision" ]] &&
   git -C "$destination" apply --reverse --check "$patch" 2>/dev/null; then
    exit 0
fi

mkdir -p "$app_dir/build"
checkout="$(mktemp -d "$app_dir/build/zxing.XXXXXX")"
trap 'rm -rf "$checkout"' EXIT
git -c advice.detachedHead=false clone --quiet --depth 1 --branch "$version" https://github.com/zxing-cpp/zxing-cpp.git "$checkout"
if [[ "$(git -C "$checkout" rev-parse HEAD)" != "$revision" ]]; then
    echo "Unexpected ZXing commit for $version" >&2
    exit 1
fi
git -C "$checkout" apply "$patch"
rm -rf "$destination"
mv "$checkout" "$destination"
