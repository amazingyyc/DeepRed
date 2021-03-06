#include "livephoto.h"

/**
 * 设置相机的预览大小 和 用户的预览的比例
 */
JNIEXPORT jint Java_com_deepcolor_deepred_shot_ShotInstance_jniSetPreviewSizeAndRatio(JNIEnv *env, jobject obj,
		jint width,
		jint height,
		jfloat ratio)
{
	int ret = recorder.set_preview_size_and_ratio(width, height, ratio);

	return ret;
}


/**
 * 当收到一帧视频时调用
 */
JNIEXPORT void JNICALL Java_com_deepcolor_deepred_shot_ShotInstance_jniOnReceiveVideoFrame(JNIEnv *env, jobject obj,
		jbyteArray data,
		jint len,
		jint width,
		jint height,
		jfloat ratio,
		jint camera_facing)
{
	uint8_t* temp_data = (uint8_t*)(env->GetByteArrayElements(data, NULL));

	recorder.on_receive_video_frame(temp_data, len, width, height, ratio, camera_facing);

	env->ReleaseByteArrayElements(data, (jbyte*)temp_data, 0);
}

/**
 * 当收到音频数据时
 */
JNIEXPORT void JNICALL Java_com_deepcolor_deepred_shot_ShotInstance_jniOnReceiveAudioFrame(JNIEnv *env, jobject obj,
		jbyteArray data,
		jint len)
{
	uint8_t* temp_data = (uint8_t*)(env->GetByteArrayElements(data, NULL));

	recorder.on_receive_audio_frame(temp_data, len);

	env->ReleaseByteArrayElements(data, (jbyte*)temp_data, 0);
}

/************************************************************************/
/* 初始化一个编码器 当前编码器 保存当前的数据
并返回编码器的id
小于0表示编码器初始化失败*/
/************************************************************************/
//int init_encoder(char* file_path);
JNIEXPORT jint Java_com_deepcolor_deepred_shot_ShotInstance_jniInitEncoder(JNIEnv *env, jobject obj,
		jstring path)
{
	char* file_path = (char*)env->GetStringUTFChars(path, NULL);

	int ret = recorder.init_encoder(file_path);

	env->ReleaseStringUTFChars(path, file_path);

	return ret;
}


/*
将id对应的encdoer开始进行编码  如果失败返回小于0
*/
//int start_encoding(int id);
JNIEXPORT jint Java_com_deepcolor_deepred_shot_ShotInstance_jniStartEncoding(JNIEnv *env, jobject obj,
		jint id)
{
	int ret = recorder.start_encoding(id);

	return ret;
}


/************************************************************************/
/* 将第二部分的数据添加到 encoder中
因为视频的录制是 前1.5s和后1.5s 第一次复制的数据为前1.5s 需要发送一个延迟消息 将第二部分的数据 添加到对应的编码器中*/
/************************************************************************/
//int add_second_part_to_encoder(int id);
JNIEXPORT jint Java_com_deepcolor_deepred_shot_ShotInstance_jniAddSecondPartToEncoder(JNIEnv *env, jobject obj,
		jint id)
{
	int ret = recorder.add_second_part_to_encoder(id);

	return ret;
}

/************************************************************************/
/* 结束所有的编码器操作 将所有的编码器 的标志位均设置为false
切换摄像头的时候需要调用 这时前后摄像头不能混用*/
/************************************************************************/
//void end_all_encoder();
JNIEXPORT void Java_com_deepcolor_deepred_shot_ShotInstance_jniEndAllEncoder(JNIEnv *env, jobject obj)
{
	recorder.end_all_encoder();
}

/**
 * 清理视频 和 音频 队列的数据
 */
//void clear_data();
JNIEXPORT void Java_com_deepcolor_deepred_shot_ShotInstance_jniClearData(JNIEnv *env, jobject obj)
{
	recorder.clear_data();
}











