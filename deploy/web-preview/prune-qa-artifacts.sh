#!/bin/sh
# Removes non-sensitive QA artifacts (screenshots, reports; synthetic data only) older than QA_ARTIFACT_RETENTION_DAYS
# (proposed staging default 90, not an approved policy: O06). Dry run unless called with --apply.
#   QA_ARTIFACT_DIR=/srv/matbakhy-qa-artifacts QA_ARTIFACT_RETENTION_DAYS=90 ./prune-qa-artifacts.sh [--apply]
# Only regular files below QA_ARTIFACT_DIR are touched; the directory itself must be a dedicated one (never a system path).
set -eu
dir="${QA_ARTIFACT_DIR:-}"
days="${QA_ARTIFACT_RETENTION_DAYS:-90}"
die() { echo "prune-qa-artifacts: $*" >&2; exit 1; }
[ -n "$dir" ] || die "QA_ARTIFACT_DIR is not set"
[ -d "$dir" ] || die "$dir is not a directory"
resolved=$(cd "$dir" && pwd -P)
case "$resolved" in
  /|/bin|/boot|/dev|/etc|/home|/lib|/lib64|/opt|/proc|/root|/run|/sbin|/srv|/sys|/usr|/var|/var/lib|/var/log) die "refusing $resolved: use a dedicated directory below it" ;;
esac
case "$days" in
  ''|*[!0-9]*) die "QA_ARTIFACT_RETENTION_DAYS must be a whole number of days" ;;
esac
[ "$days" -ge 1 ] || die "QA_ARTIFACT_RETENTION_DAYS must be at least 1"
list=$(find "$resolved" -type f -mtime "+$days" -print)
if [ -z "$list" ]; then echo "nothing older than $days days in $resolved"; exit 0; fi
if [ "${1:-}" = "--apply" ]; then
  printf '%s\n' "$list" | while IFS= read -r f; do rm -f -- "$f"; done
  find "$resolved" -mindepth 1 -type d -empty -delete
  echo "removed files older than $days days from $resolved:"
else
  echo "dry run (add --apply to delete); files older than $days days in $resolved:"
fi
printf '%s\n' "$list" | sed "s|^$resolved/||"
