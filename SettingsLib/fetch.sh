#!/bin/bash

# Exit on error
set -e

declare -r SCRIPTNAME=${BASH_SOURCE[0]}
declare -r SHORT_OPTS=u:,t:
declare -r LONG_OPTS=set-repo-url:,set-repo-tag:,get-repo-url,get-repo-tag
declare -r FWB_DIR='fwb'
declare REPO_URL='https://android.googlesource.com/platform/frameworks/base.git'
declare REPO_TAG='android-13.0.0_r75'
declare -a LIBS=(
    'BannerMessagePreference'
    'CollapsingToolbarBaseActivity'
    'MainSwitchPreference'
    'SettingsTheme'
    'TwoTargetPreference'
    'Utils'
)

function main() {
    echo "Preparing the repo..."
    echo "  URL: $REPO_URL"
    echo "  Branch/Tag: $REPO_TAG"
    if [ "$(cd $FWB_DIR 2>/dev/null && git rev-parse --is-inside-work-tree 2>/dev/null)" != 'true' ] || # repo doesn't exist
        [ "$(git-fwb config --get remote.origin.url 2>/dev/null)" != "$REPO_URL" ] || # repo URL diverged
        [ "$(git-fwb describe --match="$REPO_TAG" 2>/dev/null)" != "$REPO_TAG" ]; then # repo tag/branch diverged
        rm -rf $FWB_DIR
        git clone --depth=1 --filter=blob:none --no-checkout --single-branch --branch "$REPO_TAG" \
            "$REPO_URL" $FWB_DIR
    else
        echo '  The repo has not diverged!'
    fi

    echo 'Initializing sparse fetch...'
    git-fwb sparse-checkout set --no-cone
    local lib target
    for lib in "${LIBS[@]}"; do
        target=packages/SettingsLib/"$lib"
        echo "  + $lib"
        git-fwb sparse-checkout add "$target"
    done
    git-fwb checkout

    echo 'Verifying integrity...'
    for lib in "${LIBS[@]}"; do
        target=packages/SettingsLib/"$lib"
        echo -n "  ${lib} ... "
        # The lib is only valid if it's shipped with a Soong configuration file
        if ! git-fwb ls-files --error-unmatch "$target"/Android.bp >/dev/null; then
            # Print what's inside and abort
            git-fwb ls-files --error-unmatch "$target"
            exit 1
        fi
        echo 'OK!'
    done
    echo 'Done.'
}

function git-fwb() {
    git --git-dir=$FWB_DIR/.git --work-tree=$FWB_DIR "$@"
}

if ! OPTS=$(getopt --alternative --name "$SCRIPTNAME" \
    --options $SHORT_OPTS --longoptions $LONG_OPTS -- "$@"); then
    echo "Usage: $SCRIPTNAME [-u <url>|--set-repo-url=<url>] [-t <tag>|--set-repo-tag=<tag>] [lib ...]"
    exit 1
fi
eval set -- "$OPTS"

while true; do
    case "$1" in
        -u|--set-repo-url)
            REPO_URL="${2-}"
            shift 2
            ;;
        -t|--set-repo-tag)
            REPO_TAG="${2-}"
            shift 2
            ;;
        --) shift; break;;
        *) echo "Unexpected option: $1"; exit 1
    esac
done

if [ $# -gt 0 ]; then
    declare -a LIBS=("$@")
fi

(
    # Before starting, CWD to where this script is
    cd -P -- "$(dirname -- "$SCRIPTNAME")"
    main
)
