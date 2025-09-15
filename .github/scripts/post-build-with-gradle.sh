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

echo "ARTIFACTS_DIR=$artifacts_dir" >> "$GITHUB_ENV"

# Move artifacts into a dedicated directory
mv -v build/reports/kover/htmlDebug/ "$artifacts_dir/koverDebug" || true
mv -v build/reports/tests/test{Debug,Release}UnitTest/ "$artifacts_dir" || true
mv -v build/reports/androidTests/connected/debug/ "$artifacts_dir/androidTestsDebug" || true
mv -v build/reports/lint-results-debug.* "$artifacts_dir" || true
mv -v build/reports/problems/ "$artifacts_dir" || true
if ls build/{outputs,reports}/roborazzi >/dev/null; then
    roborazzi_dir="$artifacts_dir/roborazzi"
    mkdir -vp "$artifacts_dir/tests/test" && mv -v tests/test/roborazzi/ "$_"
    mkdir -vp "$roborazzi_dir/"{outputs,reports}
    mv -v build/outputs/roborazzi/ "$roborazzi_dir/outputs/roborazzi" || true
    mv -v build/reports/roborazzi/ "$roborazzi_dir/reports/roborazzi" || true
fi
mv -v build/outputs/apk/{debug,release}/*.apk "$artifacts_dir"

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
