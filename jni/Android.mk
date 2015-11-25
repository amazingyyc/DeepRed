LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := avcodec-56
LOCAL_SRC_FILES := lib/libavcodec-56.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := avcodec
LOCAL_SRC_FILES := lib/libavcodec.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := avfilter-5
LOCAL_SRC_FILES := lib/libavfilter-5.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := avfilter
LOCAL_SRC_FILES := lib/libavfilter.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := avformat-56
LOCAL_SRC_FILES := lib/libavformat-56.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := avformat
LOCAL_SRC_FILES := lib/libavformat.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE :=  avutil-54
LOCAL_SRC_FILES := lib/libavutil-54.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE :=  avutil
LOCAL_SRC_FILES := lib/libavutil.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE :=  avswresample-1
LOCAL_SRC_FILES := lib/libswresample-1.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE :=  avswresample
LOCAL_SRC_FILES := lib/libswresample.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE :=  swscale-3
LOCAL_SRC_FILES := lib/libswscale-3.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE :=  swscale
LOCAL_SRC_FILES := lib/libswscale.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE := livephoto
LOCAL_SRC_FILES := livephoto.cpp \
					recorder/recorder.cpp \
					recorder/loop_queue.cpp \
					recorder/encoder.cpp

                   
LOCAL_LDLIBS := -llog -ljnigraphics -lz -landroid -lm -pthread

LOCAL_SHARED_LIBRARIES := avcodec-56 avcodec avfilter-5 avfilter avformat-56 avformat avutil-54 avutil avswresample-1 avswresample swscale-3 swscale avdevice
 
include $(BUILD_SHARED_LIBRARY)












