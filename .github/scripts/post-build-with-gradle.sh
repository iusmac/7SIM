#!/bin/env bash

set -euo pipefail

if [ "${CI:-}" != 'true' ]; then
    echo 'This script is supposed to be executed only in a GitHub Actions workflow.' >&2
    exit 1
fi

artifacts_dir=$RUNNER_TEMP/artifacts
checksums_tmpl="$(cat .github/markdown/checksums.md)"

echo 'Processing artifacts...'
mkdir -vp "$artifacts_dir"

# Move artifacts into a dedicated directory
mv -v build/outputs/apk/{debug,release}/*.apk "$artifacts_dir"
mv -v build/reports/lint-results-debug.html "$artifacts_dir"

# NOTE: MD5/SHA256sum commands output as '<hash> <path/to/file>', so we should
# cd into dir containing file to drop the 'path/to' part
cd "$artifacts_dir"

echo 'Generating APKs checksums...'
for apk in *.apk; do
    md5sum "$apk" > "$apk.md5"
    sha256sum "$apk" > "$apk.sha256"
done

MD5_CHECKSUMS=$(cat ./*.md5) SHA256_CHECKSUMS=$(cat ./*.sha256) envsubst <<< \
    "$checksums_tmpl" >> "$GITHUB_STEP_SUMMARY"

echo "ARTIFACTS_DIR=$artifacts_dir" >> "$GITHUB_ENV"
