#ifndef _RECORDER_H_
#define _RECORDER_H_

#include <iostream>
#include <queue>

#include <stdio.h>    
#include <sys/time.h>
#include <time.h>

#include <pthread.h>

#include <map>  

/*
#ifdef __GNUC__  
#include <ext/hash_map>  
#else  
#include <hash_map>  
#endif 
*/

#include <hash_map>

#include "content.h"
#include "loop_queue.h"
#include "encoder.h"

//打印log进行调试
#include <android/log.h>
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "JNI_LOG", __VA_ARGS__)

using namespace std;

/************************************************************************/
/* 录制视频类 初始化两个队列 保存视频和音频数据 
每次有新的数据过来时  加入到队列中 
队列的长度是 1.5s 包含的视频和音频长度*/
/************************************************************************/
class Recorder
{
private:
	long pre_video_frame_time;	//上次存储到队列中的视频帧的时间 两个视频帧之间的时间间隔需要>= VIDEO_FRAME_INTER
	queue<VideoFrame*> video_que;	//一个队列 存储 视频帧数据

	LoopQueue audio_que;	//存储音频数据 队列

	//用于对队列加锁
	pthread_mutex_t video_queue_mutex;	//视频队列锁
	pthread_mutex_t audio_queue_mutex;	//音频队列锁

private:
	int encoder_id;	//编码器id的增长
	hash_map<int, Encoder*> encoders;	//id 和对应的编码器

	//hashmap的锁
	pthread_mutex_t encoders_mutex;	//encoders锁

private:
	/**
	 * 和视频的缩放有关
	 */

	/**
	 * 从上层传入一张图片 经过裁剪旋转之后存入到src_frame_buffer中
	 * 这是src的宽高比例和用户预览到的比例相同
	 */
	int src_width;
	int src_height;
	uint8_t* src_frame_buffer;

	/**
	 * 图片只进行转码 而不缩放
	 */
	int dst_width;
	int dst_height;
	uint8_t* dst_frame_buffer;

	/**
	 * 用于图片的缩放
	 */
	AVFrame* src_frame;
	AVFrame* dst_frame;

	//用于图片的缩放 将图片从src_frame缩放到 dst_frame
	struct SwsContext *sws_ctx;

public:
	Recorder();	//构造函数
	~Recorder();

	/**
	 * 设置相机的预览大小 和 用户的预览的比例
	 */
	int set_preview_size_and_ratio(int width, int height, float ratio);

	/************************************************************************/
	/* 当有视频帧过来时  当前的视频帧是为经过旋转和裁剪过的视频帧 
	不论是前置摄像头还是后置摄像头都需要对视频帧进行裁剪和旋转
	裁剪主要根据 摄像头的预览比例进行裁剪
	数据格式为nv21*/
	/************************************************************************/
	void on_receive_video_frame(uint8_t* data, int len, int width, int height, float ratio, int camera_facing);

	/************************************************************************/
	/* 当收到 音频数据时                                                                     */
	/************************************************************************/
	void on_receive_audio_frame(uint8_t* data, int len);

	/************************************************************************/
	/* 初始化一个编码器 当前编码器 保存当前的数据    
	并返回编码器的id
	小于0表示编码器初始化失败*/
	/************************************************************************/
	int init_encoder(char* file_path);

	/*
	将id对应的encdoer开始进行编码  如果失败返回小于0
	*/
	int start_encoding(int id);

	/************************************************************************/
	/* 将第二部分的数据添加到 encoder中
	因为视频的录制是 前1.5s和后1.5s 第一次复制的数据为前1.5s 需要发送一个延迟消息 将第二部分的数据 添加到对应的编码器中*/
	/************************************************************************/
	int add_second_part_to_encoder(int id);

	/************************************************************************/
	/* 结束所有的编码器操作 将所有的编码器 的标志位均设置为false
	切换摄像头的时候需要调用 这时前后摄像头不能混用*/
	/************************************************************************/
	void end_all_encoder();

	/**
	 * 清理视频 和 音频 队列的数据
	 */
	void clear_data();
private:
	//顺时针旋转90度 然后裁剪 数据格式为nv21
	//处理后置摄像头图片
	//从左上角开始裁剪
	int rotate_cw90_cut_nv21(uint8_t* dst, int dst_w, int dst_h, uint8_t* src, int src_w, int src_h);

	//逆时针旋转90度 然后进行裁剪
	//处理前置摄像头的图片
	//从左上角开始裁剪
	int rotate_acw90_cut_nv21(uint8_t* dst, int dst_w, int dst_h, uint8_t* src, int src_w, int src_h);

	/************************************************************************/
	/* 得到当前的系统时间 毫秒                                                                     */
	/************************************************************************/
	long get_cur_time();
};

#endif
