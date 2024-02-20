#!/bin/env bash

set -euo pipefail

url="$(./SettingsLib/fetch.sh --get-repo-url)"
tag="$(./SettingsLib/fetch.sh --get-repo-tag)"
key="$(echo -n "$url+$tag" | base64 --wrap=0)"
echo "settingslibs-$key"
