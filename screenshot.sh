#!/bin/bash
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
adb shell screencap -p /sdcard/screenshot.png && adb pull /sdcard/screenshot.png ~/Desktop/chromis_screenshot/screenshot_${TIMESTAMP}.png && adb shell rm /sdcard/screenshot.png && echo "Saved: ~/Desktop/chromis_screenshot/screenshot_${TIMESTAMP}.png"
