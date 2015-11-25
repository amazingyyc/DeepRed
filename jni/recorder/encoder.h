/**
* 　　 ┏┓　  ┏┓
* 　　┏┛┻━━━┛┻┓
* 　　┃　　　　　┃
* 　　┃　　 ━　　┃
* 　　┃　┳┛　┗┳　┃
* 　　┃　　　　　 ┃
* 　　┃　　 ┻　　 ┃
* 　　┃　　　　　 ┃
* 　　┗━┓　　　┏━┛
* 　　　┃　　　┃
* 　　　┃　　　┃
* 　　　┃　　　┗━━━┓
* 　　　┃　　　　　 ┣┓
* 　　　┃　　　　　 ┏┛
* 　　　┗┓┓┏━┳┓┏┛
* 　　　 ┃┫┫　┃┫┫
* 　　　 ┗┻┛　┗┻┛
*
*-----神兽保佑，远离bug-----
*/

#ifndef _ENCODER_H_
#define _ENCODER_H_

#include <stdio.h>
#include <iostream>
#include <string>
#include <queue>
#include <pthread.h>

#include "content.h"
#include "loop_queue.h"

using namespace std;

//打印log进行调试
#include <android/log.h>
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "JNI_LOG", __VA_ARGS__)

extern "C"
{
#include <libavutil/opt.h>
#include "libavutil/channel_layout.h"
#include "libavutil/mathematics.h"
#include "libswscale/swscale.h"
#include "libswresample/swresample.h"
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
};




typedef struct OutputStream
{
    AVStream *stream;    //对应的流
    AVCodec *pCodec;    //解码器
    AVCodecContext *pCodecCtx;   //解码器上下文
    
    int64_t next_pts;   //视频或者音频对应的下一帧的个数 或者 已经存在的个数
    int64_t samples_count;  //我也不知道为什么加这个 好像不加 有问题
    
    /************************************************************************/
    /* 由于原始的视频数据格式是nv21所以在读取原始数据到编码过程中 不需要进行格式的转换
     而对应的音频格式是16位 进行编码时可能不支持 所以 需要进行音频格式的转码*/
    /************************************************************************/
    
    //存储未编码的 视频和音频 数据
    //对于视频：就是原始的YUV420数据
    //而对于音频：是经过16位转码后的音频数据 具体格式不确定 需要进行find——codec进行确定
    AVFrame *frame;         //对于视频存储yuv数据 对于音频不确定
    AVFrame *temp_frame;	//只针对音频数据有效 存储原始的16位音频数据
    
    //用于音频的转码 对于视频来说不需要
    /* swr_init()：初始化libswresample中的SwrContext。libswresample用于音频采样采样数据（PCM）的转换。
     swr_convert()：转换音频采样率到适合系统播放的格式。
     swr_free()：释放SwrContext。                                                                     */
    struct SwrContext *swr_ctx;
    
} OutputStream;


/************************************************************************/
/* 编码类：负责从队列中拷贝数据 然后 调用ffmpeg进行视频的编码                                                                     */
/************************************************************************/
class Encoder
{
private:
	int id;	//id用于标识当前的编码器 可能同时产生多个编码器 一次需要一个id进行标志

	queue<VideoFrame*> video_que;	//存储视频帧数据
	LoopQueue audio_que;			//存储音频数据

	//用于对队列加锁
	pthread_mutex_t video_queue_mutex;	//视频队列锁
	pthread_mutex_t audio_queue_mutex;	//音频队列锁

	bool is_encoding;	//一个标志为 当设为true的时候表示 可以进行编码 当队列数据为空且is_encoding为false的时候 才推出循环编码

    string file_path;   //编码后的视频存储的位置 是一个路径 应该以mp4结尾 从java层传入 jni不改变
    
    float video_bit_rates[2][2];    //存储视频bit率的范围 根据高度计算对应的bit率

public:
	/************************************************************************/
	/* 构造函数 传入一个id                                                                     */
	/************************************************************************/
	Encoder(int, char*);

	//析构函数
	~Encoder();

	//得到当前编码器的id
	int get_id();

	/************************************************************************/
	/* 向队列中添加视频帧                                                                     */
	/************************************************************************/
	void add_frame_to_video_que(queue<VideoFrame*>& que);

	/************************************************************************/
	/* 相队列中添加音频帧数据                                                                     */
	/************************************************************************/
	void add_frame_to_audio_que(LoopQueue& que);

	//设置编码标志位
	void set_is_encoding(bool);

	/************************************************************************/
	/* 得到编码标志位                                                                     */
	/************************************************************************/
	bool get_is_encoding();

	/************************************************************************/
	/* 开始编码 将队列中的数据编码成视频存储在相应的位置                                                                      */
	/************************************************************************/
	int start_encoding();
    
private:
    //根据视频高度得到对应的bitrate
    int get_bit_rate_by_height(int height);
    
    //添加视频流
    int add_video_stream(OutputStream* oStream, AVFormatContext *pFormatCtx, enum AVCodecID codec_id, int video_width, int video_height, float bit_rate);
    
    //添加音频流
    int add_audio_stream(OutputStream* oStream, AVFormatContext *pFormatCtx, enum AVCodecID codec_id);

    //打开视频缓存
    int open_video(OutputStream* oStream);

	//打开音频缓存
    int open_audio(OutputStream* oStream, int* temp_frame_size, uint8_t** temp_frame_buffer, int* frame_size, uint8_t** frame_buffer);

    /**
     * 对一帧视频进行编码 首先从队列中读取一帧数据 然后进行编码
     */
    bool write_video_frame(OutputStream* oStream, AVFormatContext *pFormatCtx, int width, int height);

    /**
     * 读取音频 数据并编码
     */
    bool write_audio_frame(OutputStream* oStream, AVFormatContext *pFormatCtx, uint8_t* buffer, int buffer_size);

    //清理残余的数据
    void flush_video_encoder(OutputStream* oStream, AVFormatContext *pFormatCtx);
    void flush_audio_encoder(OutputStream* oStream, AVFormatContext *pFormatCtx);
};

#endif
