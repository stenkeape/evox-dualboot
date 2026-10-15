#!/system/bin/sh
# Action button: toggle DSU Sideloader visibility (show it to update Evolution X, then hide it again).
MODDIR=${0%/*}
ACT=$(cat "$MODDIR/act" 2>/dev/null)

if [ -z "$ACT" ]; then
  echo "Not ready yet. Reboot once and wait a minute, then press Action again."
  exit 0
fi

if [ -f "$MODDIR/unhide" ]; then
  rm -f "$MODDIR/unhide"
  pm disable "$ACT" >/dev/null 2>&1
  echo "DSU Sideloader hidden."
else
  touch "$MODDIR/unhide"
  pm enable "$ACT" >/dev/null 2>&1
  echo "DSU Sideloader visible. Press Action again to hide it."
fi
