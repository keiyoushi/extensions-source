#!/bin/bash
set -e
shopt -s globstar nullglob extglob

# Get APKs from previous jobs' artifacts
cp -R ~/apk-artifacts/ $PWD
APKS=( **/*".apk" )

# Fail if too little extensions seem to have been built
#if [ "${#APKS[@]}" -le "100" ]; then
#    echo "Insufficient amount of APKs found. Please check the project configuration."
#    exit 1
#else
#    echo "Moving ${#APKS[@]} APKs"
#fi

DEST=$PWD/apk
rm -rf $DEST && mkdir -p $DEST

for APK in ${APKS[@]}; do
    BASENAME=$(basename $APK)
    APKNAME="${BASENAME%%+(-release*)}.apk"
    APKDEST="$DEST/$APKNAME"

    cp $APK $APKDEST
done
