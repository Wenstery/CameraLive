#include <android/log.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include "rtmp.h"
#include "rtmp_sys.h"

RTMP* m_pRtmp;
#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jboolean JNICALL nativeRtmpInit(JNIEnv *env, jobject thiz, jstring rtmp_url);
JNIEXPORT jint JNICALL  nativeWriteVideoFrame(JNIEnv *env, jobject thiz, jbyteArray video_data , jlong pts, jint SpsPpsFlag);
JNIEXPORT jint JNICALL  nativeWriteAudioFrame(JNIEnv *env,jobject thiz, jbyteArray audio_data, jlong pts);
JNIEXPORT void JNICALL  nativeRtmpRelease(JNIEnv *env, jobject thiz);
#ifdef __cplusplus
}
#endif

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "librtmp_jni", __VA_ARGS__)
#define RTMP_HEAD_SIZE   (sizeof(RTMPPacket)+RTMP_MAX_HEADER_SIZE)

int RTMP264_Connect(const char* url)
{
	m_pRtmp = RTMP_Alloc();
	RTMP_Init(m_pRtmp);

	if (RTMP_SetupURL(m_pRtmp,(char*)url) == FALSE)
	{
		RTMP_Free(m_pRtmp);
        return FALSE;
    }

	RTMP_EnableWrite(m_pRtmp);

	if (RTMP_Connect(m_pRtmp, NULL) == FALSE)
	{
		RTMP_Free(m_pRtmp);
		return FALSE;
	}

	if (RTMP_ConnectStream(m_pRtmp,0) == FALSE)
	{
		RTMP_Close(m_pRtmp);
		RTMP_Free(m_pRtmp);
		return FALSE;
	}
	return TRUE;
}

void RTMP264_Close()
{
	if(m_pRtmp)
	{
		RTMP_Close(m_pRtmp);
		RTMP_Free(m_pRtmp);
		m_pRtmp = NULL;
	}
}

int SendVideoSpsPps(unsigned char *data,int size,int dts)
{
	RTMPPacket  *packet = NULL;
	unsigned char * body = NULL;
    packet = (RTMPPacket *)malloc(RTMP_HEAD_SIZE + 1024);
    memset(packet,0,RTMP_HEAD_SIZE);
    packet->m_body = (char *)packet + RTMP_HEAD_SIZE;
	body = (unsigned char *)packet->m_body;
    memcpy(body,data,size);
	packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
	packet->m_nBodySize = size;
	packet->m_nChannel = 0x04;
	packet->m_nTimeStamp = 0;
	packet->m_hasAbsTimestamp = 0;
	packet->m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet->m_nInfoField2 = m_pRtmp->m_stream_id;
    int nRet = 0;
    if (RTMP_IsConnected(m_pRtmp)) {
	  nRet = RTMP_SendPacket(m_pRtmp,packet,TRUE);
    }
	free(packet);
    return nRet;
}

int SendH264Packet(unsigned char *data,unsigned int size,unsigned int nTimeStamp)
{
	if(data == NULL && size<11){
		return FALSE;
	}

	RTMPPacket  *packet = NULL;
    packet = (RTMPPacket *) malloc(RTMP_HEAD_SIZE + size);
    memset(packet, 0, RTMP_HEAD_SIZE);
    packet->m_body = (char *) packet + RTMP_HEAD_SIZE;
    packet->m_nBodySize = size;
    char * body = (unsigned char *)packet->m_body;
    memset(body, 0, size);
    memcpy(body,data,size);
    packet->m_hasAbsTimestamp = 0;
    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nInfoField2 = m_pRtmp->m_stream_id;
    packet->m_nChannel = 0x04;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nTimeStamp = nTimeStamp;
    int nRet =0;
    if (RTMP_IsConnected(m_pRtmp))
    {
    	nRet = RTMP_SendPacket(m_pRtmp,packet,TRUE);
    }
    free(packet);
    return nRet;
}

int SendAudioPacket(unsigned char *data, unsigned int size, unsigned int nTimeStamp){
    RTMPPacket packet;
    RTMPPacket_Reset(&packet);
    RTMPPacket_Alloc(&packet, size);
    memcpy(packet.m_body, data, size);

    packet.m_headerType  = RTMP_PACKET_SIZE_MEDIUM;
    packet.m_packetType = RTMP_PACKET_TYPE_AUDIO;
    packet.m_hasAbsTimestamp = 0;
    packet.m_nChannel   = 0x04;
    packet.m_nTimeStamp = nTimeStamp;
    packet.m_nInfoField2 = m_pRtmp->m_stream_id;
    packet.m_nBodySize = size;

    int nRet = RTMP_SendPacket(m_pRtmp, &packet, TRUE);
    RTMPPacket_Free(&packet);
    return nRet;
}

#ifdef __cplusplus
extern "C" {
#endif
    JNIEXPORT jboolean JNICALL nativeRtmpInit(JNIEnv *env, jobject thiz, jstring rtmp_url){
        const jbyte * rtmpUrl = (*env)->GetStringUTFChars(env, rtmp_url, NULL);
        int ret = RTMP264_Connect(rtmpUrl);
        (*env)->ReleaseStringUTFChars(env, rtmp_url, rtmpUrl);
        if (ret > 0){
            LOGI("rtmp init ok!");
            return JNI_TRUE;
        } else {
            LOGI("rtmp init failed!");
            return JNI_FALSE;
        }
    }

    JNIEXPORT jint JNICALL  nativeWriteVideoFrame(JNIEnv *env, jobject thiz, jbyteArray video_data , jlong pts, jint SpsPpsFlag){
        int dts = ((int)(pts/1000)) & 0x7fffffff;
        int length = (*env)->GetArrayLength(env, video_data);
        unsigned char *video = (*env)->GetByteArrayElements(env,video_data,0);
        int nRet = 0;
        if (SpsPpsFlag == 0) {
            nRet =  SendVideoSpsPps(video, length, dts);
        } else {
            nRet = SendH264Packet(video, length, dts);
        }
        return nRet;
    }

    JNIEXPORT jint JNICALL  nativeWriteAudioFrame(JNIEnv *env,jobject thiz, jbyteArray audio_data, jlong pts){
        int dts = ((int)(pts/1000)) & 0x7fffffff;
        int length = (*env)->GetArrayLength(env, audio_data);
        unsigned char *audio = (*env)->GetByteArrayElements(env, audio_data, 0);
        int nRet = 0;
        nRet = SendAudioPacket(audio, length, dts);
        return nRet;
    }

    JNIEXPORT void JNICALL  nativeRtmpRelease(JNIEnv *env, jobject thiz){
        RTMP264_Close();
    }

    static const char *classPathName = "com/view/cameralive/RtmpWorker";


    static JNINativeMethod method_table[] = {
        {"nativeRtmpInit", "(Ljava/lang/String;)Z", (void *)&nativeRtmpInit},
        {"nativeWriteVideoFrame", "([BJI)I", (void *)&nativeWriteVideoFrame},
        {"nativeWriteAudioFrame", "([BJ)I", (void *)&nativeWriteAudioFrame},
        {"nativeRtmpRelease", "()V", (void *)&nativeRtmpRelease},
    };
    static int method_table_size = sizeof(method_table) / sizeof(JNINativeMethod);

    JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
        JNIEnv* env = NULL;
        if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK) {
             return JNI_ERR;
        }

        jclass clazz = (*env)->FindClass(env, classPathName);
        if (clazz) {
            jint ret = (*env)->RegisterNatives(env, clazz, method_table, method_table_size);
            (*env)->DeleteLocalRef(env, clazz);
            if (ret != 0) {
                return JNI_ERR;
            }
        } else {
             return JNI_ERR;
        }
        LOGI("JNI_OnLoad rtmp successfully");
        return JNI_VERSION_1_4;
    }

#ifdef __cplusplus
}
#endif
