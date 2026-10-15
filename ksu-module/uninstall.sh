#!/system/bin/sh
MODDIR=${0%/*}
ACT=$(cat "$MODDIR/act" 2>/dev/null)
[ -n "$ACT" ] && pm enable "$ACT" >/dev/null 2>&1
