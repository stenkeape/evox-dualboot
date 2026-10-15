#!/system/bin/sh
# Hide the DSU Sideloader launcher icon (the app keeps working).
MODDIR=${0%/*}

until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 3; done
sleep 10

[ -f "$MODDIR/unhide" ] && exit 0

PKG=$(pm list packages 2>/dev/null | grep -i dsusideloader | head -n 1 | cut -d: -f2 | tr -d '\r')
[ -z "$PKG" ] && exit 0

ACT=$(cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$PKG" 2>/dev/null | tail -n 1 | tr -d '\r')
case "$ACT" in
  */*)
    echo "$ACT" > "$MODDIR/act"
    pm disable "$ACT" >/dev/null 2>&1
    ;;
esac
