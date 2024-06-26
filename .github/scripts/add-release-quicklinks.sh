#!/usr/bin/env bash

set -euo pipefail

declare -r SCRIPTNAME=${BASH_SOURCE[0]}
declare -r QUICKLINKS_FILE='.github/markdown/release-quicklinks.md'
declare -r QUICKLINKS_COMMENT="<!-- Automatically generated by $SCRIPTNAME -->"

function main() {
    local target_tag="${1:-}" description

    echo 'Loading release...'
    if [ -z "$target_tag" ]; then
        local release; release="$(gh api "/repos/$OWNER/$REPO/releases/latest")"
        target_tag="$(jq --raw-output '.tag_name' <<< "$release")"
        description="$(jq --raw-output '.body' <<< "$release")"
    else
        description="$(gh api "/repos/$OWNER/$REPO/releases/tags/$target_tag" --jq '.body')"
    fi

    echo '  Target TAG:' "$target_tag"

    if grep --fixed-string --quiet "$QUICKLINKS_COMMENT" <<< "$description"; then
        echo "The '$target_tag' already contains the 'quick links' block."
        exit 0
    fi

    echo "Prepending the 'quick links' block to the '$target_tag' release description..."
    {
        echo "$QUICKLINKS_COMMENT"
        cat $QUICKLINKS_FILE
        echo "$description"
    } | gh release edit "$target_tag" --notes-file -
}

usage="$(cat << EOL
Usage: $SCRIPTNAME OWNER/REPOSITORY [TAG]

Prepend the "quick links" block to a GitHub release description in a repository.

If release TAG is unspecified, the latest one will be used.
EOL
)"

OWNER="$(cut -d'/' -f1 <<< "${1:-}")"
REPO="$(cut -d'/' -f2 <<< "${1:-}")"
declare -r OWNER REPO

if [ -z "$OWNER" ] || [ -z "$REPO" ]; then
    echo "$usage" >&2
    exit 1
fi

target_tag="${2:-}"

main "$target_tag"
