#include "recorder.h"

Recorder::Recorder():pre_video_frame_time(-1), audio_que(AUDIO_FRAME_NUM), encoder_id(0)
{
	/**
	 *初始化原始值
	 */
	src_width = -1;
	src_height = -1;
	src_frame_buffer = NULL;

	dst_width = -1;
	dst_height = -1;
	dst_frame_buffer = NULL;

	src_frame = NULL;
	dst_frame = NULL;

	sws_ctx = NULL;

	//初始化 线程锁
	pthread_mutex_init(&video_queue_mutex, NULL);
	pthread_mutex_init(&audio_queue_mutex, NULL);
	pthread_mutex_init(&encoders_mutex, NULL);
}

//析构函数
Recorder::~Recorder()
{


	while (!video_que.empty())
	{
		VideoFrame* frame = video_que.front();
		video_que.pop();

		if(NULL != frame)
			delete frame;
	}

	//删除hashmap中的数据
	hash_map<int, Encoder*>::iterator it = encoders.begin();
	for (; it != encoders.end(); ++it)
	{
		Encoder* encoder = it->second;

		if (NULL != encoder)
			delete encoder;
	}
	encoders.clear();

	/**
	 * 释放和缩放有关的属性
	 */
	if(NULL != src_frame_buffer)
		av_free(src_frame_buffer);

	if(NULL != dst_frame_buffer)
		av_free(dst_frame_buffer);

	if(NULL != src_frame)
		av_frame_free(&src_frame);

	if(NULL != dst_frame)
		av_frame_free(&dst_frame);

	if (NULL != sws_ctx)
		sws_freeContext(sws_ctx);

	//销毁 互斥锁
	pthread_mutex_destroy(&video_queue_mutex);
	pthread_mutex_destroy(&audio_queue_mutex);
	pthread_mutex_destroy(&encoders_mutex);
}

/**
 * 设置相机的预览大小 和 用户的预览的比例
 */
int Recorder::set_preview_size_and_ratio(int width, int height, float ratio)
{
	if(0 >= width || 0 >= height || 0 >= ratio)
		return -1;

	/**
	 * 根据传入的参数重新设置当前缩放的属性
	 */
	if(NULL != src_frame_buffer)
		av_free(src_frame_buffer);

	if(NULL != dst_frame_buffer)
		av_free(dst_frame_buffer);

	if(NULL != src_frame)
		av_frame_free(&src_frame);

	if(NULL != dst_frame)
		av_frame_free(&dst_frame);

	if (NULL != sws_ctx)
		sws_freeContext(sws_ctx);

	src_width  = -1;
	src_height = -1;
	src_frame_buffer = NULL;

	dst_width = -1;
	dst_height = -1;
	dst_frame_buffer = NULL;

	src_frame = NULL;
	dst_frame = NULL;

	sws_ctx = NULL;

	/**
	 * 根据camera预览比例 和 用于看到的比例重新计算大小
	 */
	//注意图片还需要进行旋转 camera的图片方向和用户的预览不一致
	if (1.0 * height / width > ratio)
	{
		src_width  = (int)(1.0 * ratio * width);
		src_height = width;
	}
	else
	{
		src_width = height;
		src_height = (int)(1.0 * height / ratio);
	}

	//标准化成 2的倍数
	src_width  -= src_width  % 2;
	src_height -= src_height % 2;

	/**
	 * 只进行转码 而不进行缩放操作
	 */
	dst_width  = src_width;
	dst_height = src_height;

	/**
	 * 初始化存储原始图片的内存
	 */
	src_frame = av_frame_alloc();
	if(!src_frame)
		return -1;

	int src_frame_size = avpicture_get_size(SRC_VIDEO_PIX_FMT, src_width, src_height);
	src_frame_buffer = (uint8_t*)av_malloc(src_frame_size);
	avpicture_fill((AVPicture *)src_frame, src_frame_buffer, SRC_VIDEO_PIX_FMT, src_width, src_height);

	src_frame->width  = src_width;
	src_frame->height = src_height;
	src_frame->format = SRC_VIDEO_PIX_FMT;

	/**
	 * 初始化内存
	 */
	dst_frame = av_frame_alloc();
	if(!dst_frame)
		return -1;

	int dst_frame_size = avpicture_get_size(VIDEO_PIX_FMT, dst_width, dst_height);
	dst_frame_buffer = (uint8_t*)av_malloc(dst_frame_size);
	avpicture_fill((AVPicture *)dst_frame, dst_frame_buffer, VIDEO_PIX_FMT, dst_width, dst_height);

	dst_frame->width = dst_width;
	dst_frame->height = dst_height;
	dst_frame->format = VIDEO_PIX_FMT;

	/**
	 * 缩放函数
	 */
	//初始化图片缩放函数 从ccut_video_height 缩放到 FRAME_HEIGHT
	sws_ctx = sws_getContext(src_width, src_height, SRC_VIDEO_PIX_FMT,
			dst_width, dst_height, VIDEO_PIX_FMT,
		SWS_FAST_BILINEAR, NULL, NULL, NULL);

	if(!sws_ctx)
		return -1;

	return 1;
}

/************************************************************************/
/* 当有视频帧过来时  当前的视频帧是为经过旋转和裁剪过的视频帧
不论是前置摄像头还是后置摄像头都需要对视频帧进行裁剪和旋转
裁剪主要根据 摄像头的预览比例进行裁剪
数据格式为nv21

ratio是视频在android手机上预览的 宽度与高度的比例 原始图片需要按照这个比例进行裁剪
*/
/************************************************************************/
void Recorder::on_receive_video_frame(uint8_t* data, int len, int width, int height, float ratio, int camera_facing)
{
	if (NULL == data || 0 >= len || 0 >= width || 0 >= height || 0 >= ratio ||
		(CAMERA_FACING_FRONT != camera_facing && CAMERA_FACING_BACK != camera_facing))
		return;

	//得到当前的时间
	long cur_time = get_cur_time();

	//间隔时间 过短 这个视频帧直接丢掉
	if (-1 != pre_video_frame_time && (cur_time - pre_video_frame_time) < VIDEO_FRAME_INTER)
		return;

	/**
	 * 得到原始的camera预览之后需要进行裁剪旋转 缩放等 操作
	 */
	if(0 >= src_width || 0 >= src_height || NULL == src_frame_buffer || NULL == src_frame ||
	   0 >= dst_width || 0 >= dst_height || NULL == dst_frame_buffer || NULL == dst_frame ||
	   NULL == sws_ctx)
		return;

	//-------------------------------------------------------------
	//LOGD("camera width:%d", width);
	//LOGD("camera height:%d", height);
	//LOGD("camera facing:%d", camera_facing);
	//---------------------------------------------------------

	/**
	 * 将图片裁剪缩放后 放到src_frame_buffer中
	 */
	if(CAMERA_FACING_FRONT == camera_facing)
	{
		if (0 > rotate_acw90_cut_nv21(src_frame_buffer, src_width, src_height, data, width, height))
		{
			return;
		}
	}
	else if(CAMERA_FACING_BACK == camera_facing)
	{
		//LOGD("if(CAMERA_FACING_BACK == camera_facing)");

		/**
		 * 将图片进行顺时针旋转90度 然后裁剪到src_frame_buffer中
		 */
		if(0 > rotate_cw90_cut_nv21(src_frame_buffer, src_width, src_height, data, width, height))
		{
			return;
		}
	}

	/**
	 * 对裁剪好的图片进行格式转换 不执行缩放
	 * 缩放好给的时间过长
	 */
	sws_scale(sws_ctx,
			src_frame->data, src_frame->linesize,
			0, src_frame->height,
			dst_frame->data, dst_frame->linesize);

	//此时dst_frame中存储这说放过的yuv420p的视频帧数据 放入到队列中即可

	//生成一帧数据
	VideoFrame* frame = new VideoFrame(dst_frame_buffer, dst_width, dst_height);

	/************************************************************************/
	/*如果存储的视频帧已经超过上限 删除一个                                                                      */
	/************************************************************************/
	pthread_mutex_lock(&video_queue_mutex);	//对视频队列进行加锁
	if (video_que.size() >= VIDEO_FRAME_NUM)
	{
		VideoFrame* front_frame = video_que.front();
		video_que.pop();

		delete front_frame;
	}

	video_que.push(frame);	//将视频帧放入到队列中
	pthread_mutex_unlock(&video_queue_mutex); //释放锁，供其他线程使用  

	//更新时间
	pre_video_frame_time = cur_time;

	//测试
	//LOGD("video size:%d", video_que.size());
}

/************************************************************************/
/* 当收到 音频数据时                                                                     */
/************************************************************************/
void Recorder::on_receive_audio_frame(uint8_t* data, int len)
{
	if (NULL == data || 0 >= len)
		return;

	pthread_mutex_lock(&audio_queue_mutex);	//对音频队列进行加锁
	for (int i = 0; i < len; ++i)
	{
		//如果队列是满的 弹出一个数据
		if (audio_que.full())
			audio_que.pop();

		audio_que.push(data[i]);
	}
	pthread_mutex_unlock(&audio_queue_mutex); //释放锁，供其他线程使用  
}

/************************************************************************/
/* 初始化一个编码器 当前编码器 保存当前的数据
并返回编码器的id
小于0表示编码器初始化失败*/
/************************************************************************/
int Recorder::init_encoder(char* file_path)
{
	//更新id
	encoder_id++;

	//初始化一个编码器
	Encoder* encoder = new Encoder(encoder_id, file_path);

	//将当前的视频和音频数据 加入到 编码器中去
	pthread_mutex_lock(&video_queue_mutex);
	encoder->add_frame_to_video_que(video_que);
	pthread_mutex_unlock(&video_queue_mutex);

	pthread_mutex_lock(&audio_queue_mutex);	//对音频队列进行加锁
	encoder->add_frame_to_audio_que(audio_que);
	pthread_mutex_unlock(&audio_queue_mutex); //释放锁，供其他线程使用 

	//将对应的编码器索引 和 编码器 添加到hash_map中
	pthread_mutex_lock(&encoders_mutex);
	encoders.insert(make_pair(encoder->get_id(), encoder));
	pthread_mutex_unlock(&encoders_mutex);

	//返回编码器的id
	return encoder->get_id();
}

/*
将id对应的encdoer开始进行编码  如果失败返回小于0
这是一个耗时操作 需要将 此操作放在线程中进行  同时 需要对数据进行加锁
因为 有可能会得到一个延迟消息 添加第二部分的数据
*/
int Recorder::start_encoding(int id)
{
	//-----------------------------------------------
	//LOGD("Recorder::start_encoding");
	//-----------------------------------------------

	//当前id不存在 直接返回
	if (encoders.end() == encoders.find(id))
		return -1;

	//-----------------------------------------------
	//LOGD("Encoder* encoder = encoders[id];");
	//-----------------------------------------------

	Encoder* encoder = encoders[id];	//得到当前解码器
	//encoders.erase(id);	//不能删除 有可能需要调用add_second_part_to_encoder 进行添加数据

	if (NULL == encoder)
	{
		encoders.erase(id);
		return -1;
	}

	//-----------------------------------------------
	//LOGD("int ret = encoder->start_encoding();");
	//-----------------------------------------------

	/*
	开始编码 是一个耗时操作 
	在编码的过程中 有可能调用add_second_part_to_encoder 进行添加数据
	*/
	int ret = encoder->start_encoding();

	//-----------------------------------------------
	//LOGD("ret:%d", ret);
	//-----------------------------------------------

	//编码结束后 需要将 编码器删除 并清理内存 
	encoders.erase(id);
	delete encoder;	//清理内存

	return ret;
}

/************************************************************************/
/* 将第二部分的数据添加到 encoder中
因为视频的录制是 前1.5s和后1.5s 第一次复制的数据为前1.5s 需要发送一个延迟消息 将第二部分的数据 添加到对应的编码器中*/
/************************************************************************/
int Recorder::add_second_part_to_encoder(int id)
{
	//当前id不存在 直接返回
	if (encoders.end() == encoders.find(id))
		return -1;
	
	Encoder* encoder = encoders[id];	//得到当前解码器
	//encoders.erase(id);	//不能删除 否则出现内存泄漏  编码结束后才可以删除

	if (NULL == encoder)
	{
		encoders.erase(id);
		return -1;
	}

	//如果标志位已经被设置为false则不添加数据
	if (!encoder->get_is_encoding())
		return -1;

	//将当前的视频和音频数据 加入到 编码器中去
	pthread_mutex_lock(&video_queue_mutex);
	encoder->add_frame_to_video_que(video_que);
	pthread_mutex_unlock(&video_queue_mutex);

	pthread_mutex_lock(&audio_queue_mutex);	//对音频队列进行加锁
	encoder->add_frame_to_audio_que(audio_que);
	pthread_mutex_unlock(&audio_queue_mutex); //释放锁，供其他线程使用 

	//设置标志为
	encoder->set_is_encoding(false);

	return encoder->get_id();
}

/************************************************************************/
/* 结束所有的编码器操作 将所有的编码器 的标志位均设置为false
切换摄像头的时候需要调用 这时前后摄像头不能混用*/
/************************************************************************/
void Recorder::end_all_encoder()
{
    //加锁
    pthread_mutex_lock(&encoders_mutex);
	hash_map<int, Encoder*>::iterator it = encoders.begin();
	for (; it != encoders.end(); ++it)
	{
		Encoder* encoder = it->second;

		if (NULL != encoder)
			encoder->set_is_encoding(false);
	}
    pthread_mutex_unlock(&encoders_mutex);
}

/**
 * 清理视频 和 音频 队列的数据
 */
void Recorder::clear_data()
{
	pthread_mutex_lock(&video_queue_mutex);
	while (!video_que.empty())
	{
		VideoFrame* frame = video_que.front();
		video_que.pop();

		if(NULL != frame)
			delete frame;
	}
	pthread_mutex_unlock(&video_queue_mutex);

	pthread_mutex_lock(&audio_queue_mutex);	//对音频队列进行加锁
	audio_que.clear();
	pthread_mutex_unlock(&audio_queue_mutex); //释放锁，供其他线程使用
}


//顺时针旋转90度 然后裁剪 数据格式为nv21
int Recorder::rotate_cw90_cut_nv21(uint8_t* dst, int dst_w, int dst_h, uint8_t* src, int src_w, int src_h)
{
	if (dst_w > src_h || dst_h > src_w)
		return -1;

	int n = 0;
	int src_s = src_w * src_h;	//原始数据图片大小
	int n_pos;

	//int src_hw = src_w >> 1;	//除以2
	int src_hh = src_h >> 1;
	int src_hs = src_w * src_hh;	//原始UV数据占用的byte数

	int dst_hw = dst_w >> 1;
	//int dst_hh = dst_h >> 1;

	//拷贝Y
	for (int i = 0; i < dst_h; ++i)
	{
		n_pos = src_s;
		for (int j = 0; j < dst_w; ++j)
		{
			n_pos -= src_w;

			dst[n++] = src[n_pos + i];
		}
	}

	//拷贝UV
	uint8_t* temp = src + src_s;
	for (int i = 0; i < dst_h; i += 2)
	{
		n_pos = src_hs;
		for (int j = 0; j < dst_hw; ++j)
		{
			n_pos -= src_w;

			dst[n++] = temp[n_pos + i];
			dst[n++] = temp[n_pos + i + 1];
		}
	}

	return 1;
}

//逆时针旋转90度 然后进行裁剪
//处理前置摄像头的图片
int Recorder::rotate_acw90_cut_nv21(uint8_t* dst, int dst_w, int dst_h, uint8_t* src, int src_w, int src_h)
{
	if (dst_w > src_h || dst_h > src_w)
		return -1;

	int n = 0;
	int n_pos;

	int src_hh = src_h >> 1;
	int dst_hw = dst_w >> 1;

	//拷贝Y
	for (int i = 0; i < dst_h; ++i)
	{
		n_pos = (src_h - dst_w) * src_w;
		for (int j = 0; j < dst_w; ++j)
		{
			dst[n++] = src[n_pos + (src_w - 1 - i)];

			n_pos += src_w;
		}
	}

	//拷贝UV
	uint8_t* temp = src + src_w * src_h;
	int index;
	for (int i = 0; i < dst_h; i += 2)
	{
		n_pos = (src_hh - dst_hw) * src_w;
		index = src_w - i - 2;

		for (int j = 0; j < dst_hw; ++j)
		{
			dst[n++] = temp[n_pos + index];
			dst[n++] = temp[n_pos + index + 1];

			n_pos += src_w;
		}
	}

	return 1;
}

/************************************************************************/
/* 得到当前的系统时间 毫秒                                                                     */
/************************************************************************/
long Recorder::get_cur_time()
{
	struct timeval tv;
	gettimeofday(&tv, NULL);

	return tv.tv_sec * 1000 + tv.tv_usec / 1000;
}
