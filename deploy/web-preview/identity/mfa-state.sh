#!/bin/sh
# State file of the D18 migration backup (read by `migrate-mfa.sh rollback`). It is DATA, never shell: it is parsed line by line,
# keys come from a fixed list, and every value is validated before use. The previous browser flow's name is Keycloak data an
# administrator chose, so it is stored base64-encoded and decoded after validation; no value is ever evaluated or sourced.
# Source this file only for its two functions: state_write FILE STAMP PREVIOUS_FLOW, state_read FILE KEY.

# Keys: STAMP, PREVIOUS_BROWSER_FLOW_B64, DB_DUMP, REALM_EXPORT (plain keys; the flow name is read with state_flow).
_state_valid() { # key value
  case "$1" in
    STAMP) printf '%s' "$2" | grep -Eq '^[0-9]{8}T[0-9]{6}Z$' ;;
    PREVIOUS_BROWSER_FLOW_B64) printf '%s' "$2" | grep -Eq '^[A-Za-z0-9+/]*={0,2}$' ;;
    DB_DUMP) printf '%s' "$2" | grep -Eq '^keycloak-db-[0-9]{8}T[0-9]{6}Z\.dump$' ;;
    REALM_EXPORT) printf '%s' "$2" | grep -Eq '^realm-before-[0-9]{8}T[0-9]{6}Z\.json$' ;;
    *) return 1 ;;
  esac
}

state_write() { # file stamp previous_flow
  _b64="$(printf '%s' "$3" | base64 | tr -d '\n')"
  _state_valid STAMP "$2" && _state_valid PREVIOUS_BROWSER_FLOW_B64 "$_b64" || return 1
  printf 'STAMP=%s\nPREVIOUS_BROWSER_FLOW_B64=%s\nDB_DUMP=keycloak-db-%s.dump\nREALM_EXPORT=realm-before-%s.json\n' "$2" "$_b64" "$2" "$2" > "$1"
}

state_read() { # file key -> validated value on stdout; fails (empty) when absent or invalid
  [ -f "$1" ] || return 1
  _v="$(grep -E "^$2=" "$1" | head -1 | cut -d= -f2-)"
  [ -n "$_v" ] || return 1
  _state_valid "$2" "$_v" || return 1
  printf '%s' "$_v"
}

state_flow() { # file -> the previous browser flow name (decoded); fails when absent or not a printable single line
  _b="$(state_read "$1" PREVIOUS_BROWSER_FLOW_B64)" || return 1
  _f="$(printf '%s' "$_b" | base64 -d 2>/dev/null)" || return 1
  [ -n "$_f" ] || return 1
  [ "$(printf '%s' "$_f" | wc -l)" -eq 0 ] || return 1
  printf '%s' "$_f" | grep -q '[[:cntrl:]]' && return 1
  printf '%s' "$_f"
}
