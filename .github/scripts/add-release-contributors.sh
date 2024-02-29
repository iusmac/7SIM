#!/usr/bin/env bash

set -euo pipefail

declare -r SCRIPTNAME=${BASH_SOURCE[0]}
declare -r SHORT_OPTS=r:,i:
declare -r LONG_OPTS=release-tag-range:,set-ignored-contributors:
declare -r CONTRIBUTORS_FILE='.github/markdown/release-contributors.md'
declare -r CONTRIBUTORS_COMMENT="<!-- Automatically generated by $SCRIPTNAME -->"

function main() {
    local parent_tag="${1:-}" target_tag="${2:-}" ignored_contributors="${3:-}" description

    echo 'Loading releases...'
    if [ -n "$parent_tag" ] && [ -n "$target_tag" ]; then
        description="$(gh api "/repos/$OWNER/$REPO/releases/tags/$target_tag" -- jq '.body')"
    else
        local releases
        # shellcheck disable=SC2016 # suppress variable usage in single quotes
        releases="$(graphql --field query='query($owner: String!, $name: String!) {
            repository(owner: $owner, name: $name) {
                releases(first: 2) {
                    nodes { tagName description }
                }
            }
        }' --jq '.data.repository.releases.nodes')"

        if ! jq --exit-status '. | length > 1' <<< "$releases" >/dev/null; then
            echo '  Cannot find contributors for the latest release without the previous release.'
            exit 0
        fi
        target_tag=$(jq --raw-output '.[0].tagName' <<< "$releases")
        parent_tag=$(jq --raw-output '.[1].tagName' <<< "$releases")
        description=$(jq --raw-output '.[0].description' <<< "$releases")
    fi

    echo '  Target TAG:' "$target_tag"
    echo '  Parent TAG:' "$parent_tag"

    if grep --fixed-string --quiet "$CONTRIBUTORS_COMMENT" <<< "$description"; then
        echo "The '$target_tag' release already contains the Contributors block."
        exit 0
    fi

    echo "Loading authors that contributed since the '$parent_tag' has been released..."
    local query authors
    # shellcheck disable=SC2016 # suppress variable usage in single quotes
    query='query($owner: String!, $name: String!, $parentTag: String!, $targetTag: String!) {
        repository(owner: $owner, name: $name) {
            ref(qualifiedName: $parentTag) {
                compare(headRef: $targetTag) {
                    commits(first: 100) {
                        nodes {
                            authors(first: 50) {
                                nodes {
                                    user { login }
                                }
                            }
                        }
                    }
                }
            }
        }
    }'
    authors="$(graphql --field parentTag="$parent_tag" --field targetTag="$target_tag" \
        --field query="$query" --jq '[.. | .login? | select(. != null)] | unique[]')"

    echo 'Filter out contributors...'
    local contributor author
    declare -a contributors=()
    for contributor in $authors; do
        for author in $ignored_contributors; do
            if [ "$author" = "$contributor" ]; then
                echo "  - $contributor"
                continue 2
            fi
        done
        echo "  + $contributor"
        contributors+=("$contributor")
    done

    if [ ${#contributors[@]} -eq 0 ]; then
        echo 'No contributors.'
        exit 0
    fi

    echo "Appending the Contributors block to the '$target_tag' release description..."
    {
        echo "$description"
        echo "$CONTRIBUTORS_COMMENT"
        local str=${contributors[*]} # convert to a space-separated string
        CONTRIBUTORS=@${str// /, @} envsubst < $CONTRIBUTORS_FILE
    } | gh release edit "$target_tag" --notes-file -

    echo 'Done.'
}

function graphql() {
    gh api graphql --field owner="$OWNER" --field name="$REPO" "$@"
}

usage="$(cat << EOL
Usage: $SCRIPTNAME [flags] OWNER/REPOSITORY

Description:
  Add Contributors block to the end of a GitHub release description in a repository.

Flags:
  -r 'PREV...LATEST'
  --release-tag-range='PREV...LATEST'

    The release tag range to search for contributors. When no release tag range is provided,
    the latest and previous releases will be used instead.

    Example: v1.0.0...v1.0.1

  -i 'author1 author2 ...'
  --ignored-contributors='author1 author2 ...'

    The white-space-separated string of contributor nicknames to ignore.
EOL
)"

if ! OPTS="$(getopt --alternative --name "$SCRIPTNAME" --options "$SHORT_OPTS" \
    --longoptions "$LONG_OPTS" -- "$@")"; then
    echo "$usage" >&2
    exit 1
fi
eval set -- "$OPTS"

while true; do
    case "$1" in
        -r|--release-tag-range)
            range=${2/.../ } # use 1 char as cut doesn't support delims longer than 1 char
            if ! parent_tag="$(cut -d' ' -f1 <<< "$range")" ||
                ! target_tag="$(cut -d' ' -f2 <<< "$range")"; then
                echo "Unknown release tag range: $2" >&2
                exit 1
            fi
            shift 2
            ;;
        -i|--set-ignored-contributors)
            ignored_contributors="$2"
            shift 2
            ;;
        --) shift; break;
    esac
done

OWNER="$(cut -d'/' -f1 <<< "${1:-}")"
REPO="$(cut -d'/' -f2 <<< "${1:-}")"
declare -r OWNER REPO

if [ -z "$OWNER" ] || [ -z "$REPO" ]; then
    echo "$usage" >&2
    exit 1
fi

main "${parent_tag:-}" "${target_tag:-}" "${ignored_contributors:-}"
