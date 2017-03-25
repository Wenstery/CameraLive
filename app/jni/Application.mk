LOCAL_PATH:= $(call my-dir)
APP_OPTIM := release
APP_STL := gnustl_static
APP_CPPFLAGS := -frtti -fexceptions -Wno-error=format-security
APP_CFLAGS += -Wno-error=format-security
APP_BUILD_SCRIPT :=$(LOCAL_PATH)/Android.mk
APP_PLATFORM := android-18
APP_ABI := armeabi-v7a
