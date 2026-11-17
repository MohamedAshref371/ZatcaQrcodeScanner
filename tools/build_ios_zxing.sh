#!/usr/bin/env bash
set -euo pipefail

sdk=$1
work=$2
bridge=$3
revision=287c85df6f961c8efbfb5ffd736cd9457b8b890e
source_dir="$work/source"
mkdir -p "$work"
if [[ ! -d "$source_dir/.git" ]]; then
    git init -q "$source_dir"
fi
if [[ "$(git -C "$source_dir" rev-parse --verify HEAD 2>/dev/null || true)" != "$revision" ]]; then
    git -C "$source_dir" fetch -q --depth=1 https://github.com/zxing-cpp/zxing-cpp.git "$revision"
    git -C "$source_dir" checkout -q --detach "$revision"
fi

sysroot=$(xcrun --sdk "$sdk" --show-sdk-path)
cmake -S "$source_dir" -B "$work/cmake" \
    -DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_SYSROOT="$sysroot" -DCMAKE_OSX_ARCHITECTURES=arm64 \
    -DCMAKE_OSX_DEPLOYMENT_TARGET=13.0 -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=OFF -DZXING_READERS=ON -DZXING_WRITERS=OFF -DZXING_EXAMPLES=OFF \
    -DZXING_C_API=ON -DZXING_EXPERIMENTAL_API=ON
cmake --build "$work/cmake" --target ZXing --parallel 3

target=arm64-apple-ios13.0
if [[ "$sdk" == iphonesimulator ]]; then target="$target-simulator"; fi
xcrun --sdk "$sdk" clang -target "$target" -isysroot "$sysroot" -O2 \
    -I"$source_dir/core/src" -I"$work/cmake/core" -DZXING_EXPERIMENTAL_API \
    -c "$bridge/invoiceqr_zxing.c" -o "$work/bridge.o"
xcrun --sdk "$sdk" libtool -static -o "$work/libInvoiceQrZxing.a" \
    "$work/bridge.o" "$work/cmake/core/libZXing.a"
