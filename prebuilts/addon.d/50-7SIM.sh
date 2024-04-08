#!/sbin/sh
#
# ADDOND_VERSION=3
#
# /system/addon.d/50-7SIM.sh
# During an OTA update, this script backs up the app and all relative files;
# /system is formatted and reinstalled, then files are restored.
#

. /tmp/backuptool.functions

# update-binary|updater <RECOVERY_API_VERSION> <OUTFD> <ZIPFILE>
OUTFD=$(ps | grep -v 'grep' | grep -oE 'update(.*) 3 [0-9]+' | cut -d" " -f3)
[ -z $OUTFD ] && OUTFD=$(ps -Af | grep -v 'grep' | grep -oE 'update(.*) 3 [0-9]+' | cut -d" " -f3)
# update_engine_sideload --payload=file://<ZIPFILE> --offset=<OFFSET> --headers=<HEADERS> --status_fd=<OUTFD>
[ -z $OUTFD ] && OUTFD=$(ps | grep -v 'grep' | grep -oE 'status_fd=[0-9]+' | cut -d= -f2)
[ -z $OUTFD ] && OUTFD=$(ps -Af | grep -v 'grep' | grep -oE 'status_fd=[0-9]+' | cut -d= -f2)
if [ -z $OUTFD ]; then
  ui_print() { echo "$1"; }
else
  ui_print() { echo -e "ui_print $1\nui_print" >> /proc/self/fd/$OUTFD; }
fi

list_files() {
# NOTE: avoid using 'cat <<HERE-DOC' here, as it attempts to create a temporary file
# in $TMPDIR that on some recoveries is set to /data/local/tmp, but the /data
# can be missing if the recovery doesn't support decryption, so this func will
# output nothing but a "can't create temporary file : No such file or directory"
# error to stderr. Therefore, we echo everything instead.
echo 'system_ext/priv-app/7SIM/7SIM.apk'
echo 'system_ext/etc/permissions/privapp_whitelist_com.github.iusmac.sevensim.xml'
echo 'priv-app/7SIM/7SIM.apk'
echo 'etc/permissions/privapp_whitelist_com.github.iusmac.sevensim.xml'
}

case "$1" in
  backup)
    ui_print "- Backing up 7SIM"
    list_files | while read FILE DUMMY; do
      backup_file $S/"$FILE"
    done
  ;;
  restore)
    ui_print "- Restoring 7SIM"
    list_files | while read FILE REPLACEMENT; do
      R=""
      [ -n "$REPLACEMENT" ] && R="$S/$REPLACEMENT"
      [ -f "$C/$S/$FILE" ] && restore_file $S/"$FILE" "$R"
    done
  ;;
  pre-backup)
    # Stub
  ;;
  post-backup)
    # Stub
  ;;
  pre-restore)
    # Stub
  ;;
  post-restore)
    # Stub
  ;;
esac
