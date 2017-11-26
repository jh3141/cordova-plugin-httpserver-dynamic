#!/bin/sh

# Script for building and executing the sample with the current version of the plugin on an android device.
# If a parameter is specified, it should be an ip-address:port pair for an android device running ADB-over-wireless
# Otherwise a single local USB device is expected to be connected.

RED='\033[1;31m'
GRN='\033[0;32m'
WHT='\033[0;37m'

# if running under cygwin, we need to make sure we run the windows .bat versions of node commands
# rather than the unix-style shell scripts, because the latter get confused by cygwin paths

case `/bin/uname -s` in
    CYGWIN*)  NODEEXEC="cmd /c" ;;
    *)        NODEEXEC=""       ;;
esac

echo -e "${RED}Run on android device $1 (executing scripts using '$NODEEXEC')..."

if [ -d platforms/android/cordova/plugins/uk.org.dsf.cordova.dynamichttp ]; then
    echo -e "${GRN}Plugman uninstall...${WHT}"
    $NODEEXEC plugman uninstall --platform android --project "platforms/android" --plugin "uk.org.dsf.cordova.dynamichttp" || exit 1
    echo -e "${GRN}OK"
fi

echo -e "${GRN}Plugman install...${WHT}"
$NODEEXEC plugman install --platform android --project "platforms/android" --plugin "../plugin" || exit 1
echo -e "${GRN}OK"

echo -e "${GRN}Build...${WHT}"
$NODEEXEC cordova build android || exit 1
echo -e "${GRN}Build OK${WHT}"

if [ "$1" != "" ]; then 
    echo -e "${GRN}ADB connect...${WHT}"
    adb connect $1 || exit 1

    echo -e "${GRN}Run...${WHT}"
    $NODEEXEC cordova run android --noprepare --nobuild --target=$1
else
    echo -e "${GRN}Run...${WHT}"
    $NODEEXEC cordova run android --noprepare --nobuild
fi
