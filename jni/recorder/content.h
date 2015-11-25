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

#ifndef _CONTENT_H_
#define _CONTENT_H_

extern "C"
{
#include "libavformat/avformat.h"
}

/************************************************************************/
/* 存储 需要的常量                                                                     */
/************************************************************************/

#define CAMERA_FACING_FRONT 0	//标志前后摄像头   
#define CAMERA_FACING_BACK  1
//#define MIN_PREVIEW_WIDTH 1080	//图片的最小宽度从android传过来的图片的最小宽度 就是1080
//#define MIN_PREVIEW_HEIGHT 1080	//图片的最小宽度从android传过来的图片的最小宽度 就是1080

#define TIME_DURATION 1.5	//存储的视频和音频都是 1.5s的长度 前后1.5s

#define VIDEO_FPS 15	//1s存储多少视频帧 同时代表视频帧率
#define VIDEO_FRAME_INTER 1000/VIDEO_FPS	//两个视频帧之间的时间间隔
#define VIDEO_FRAME_NUM TIME_DURATION*VIDEO_FPS	//需要存储的视频帧的个数
#define VIDEO_PIX_FMT AV_PIX_FMT_YUV420P	//视频编码需要这个格式
#define SRC_VIDEO_PIX_FMT AV_PIX_FMT_NV21    //从java传入的数据就是nv21格式 需要进行转码缩放等操作


#define AUDIO_SAMPLE_RATE 44100	//采样率 即 1s采样多少次
#define AUDIO_CHANNEL_NUM 1	//声道个数 1 个 音频轨道个数
#define AUDIO_SAMPLE_BYTE_NUM 2	//每一个采样点 占2byte 即 采样格式是:数据为16bit
#define AUDIO_FRAME_NUM TIME_DURATION*AUDIO_SAMPLE_RATE*AUDIO_CHANNEL_NUM*AUDIO_SAMPLE_BYTE_NUM	//1.5需要的byte个数 时间*采样率*声道个数*每个采样点所占的byte
#define AUDIO_PIX_FMT AV_SAMPLE_FMT_S16  //队列中音频数据的格式
#define AUDIO_BIT_RATE 64000	//音频bit率
#define AUDIO_CHANNEL_LAYOUT AV_CH_LAYOUT_MONO	//音频编码器对应的CHANNEL_LAYOUT 单声道?

/************************************************************************/
/* 存储一帧 视频数据                                                                      */
/************************************************************************/
struct VideoFrame
{
	uint8_t* data;	//byte数组 存储一帧的视频数据
	int width;	//视频的宽度与高度
	int height;

	VideoFrame()
	{
		data = NULL;
		width  = -1;
		height = -1;
	}

	VideoFrame(VideoFrame* frame)
	{
		if (NULL == frame)	return;

		width  = frame->width;
		height = frame->height;

		int len = width * height * 3 / 2;

		data   = (uint8_t*)malloc(len);

		//拷贝数据
		for(int i = 0; i < len; ++i)
			data[i] = frame->data[i];
	}

	VideoFrame(uint8_t* d, int w, int h)
	{
		if(NULL == d || 0 >= w || 0 >= h)
			return;

		int len = w * h * 3 / 2;
		data = (uint8_t*)malloc(len);
		for(int i = 0; i < len; ++i)
			data[i] = d[i];

		width = w;
		height = h;
	}

	//清理内存
	~VideoFrame()
	{
		if(NULL != data)
			free(data);
	}
};

#endif




















