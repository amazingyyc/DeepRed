#include "encoder.h"

//音频队列 必须是主队列的长度的二倍 因为需要最多3s的音频数据
Encoder::Encoder(int i, char* path) :id(i), audio_que(2 * AUDIO_FRAME_NUM), is_encoding(true)
{
    //视频的存放路径
    file_path = string(path);

    //初始化bit率 最小高度和最大高度对应的bitrate
    //ßvideo_bit_rates[2][2] = {{300, 800000}, {1080, 7000000}};

    video_bit_rates[0][0] = 300;
    video_bit_rates[0][1] = 800000;
    video_bit_rates[1][0] = 1080;
    video_bit_rates[1][1] = 7000000;


	//初始化 线程锁
	pthread_mutex_init(&video_queue_mutex, NULL);
	pthread_mutex_init(&audio_queue_mutex, NULL);
}

//析构函数
Encoder::~Encoder()
{
	while (!video_que.empty())
	{
		VideoFrame* frame = video_que.front();
		video_que.pop();

		delete frame;
	}

	//销毁 互斥锁
	pthread_mutex_destroy(&video_queue_mutex);
	pthread_mutex_destroy(&audio_queue_mutex);
}

//得到 id
int Encoder::get_id()
{
	return id;
}

/************************************************************************/
/* 向视频队列中添加帧数据                                                                     */
/************************************************************************/
void Encoder::add_frame_to_video_que(queue<VideoFrame*>& que)
{
	//对视频队列进行加锁
	pthread_mutex_lock(&video_queue_mutex);

	int que_size = que.size();
	while (que_size--)
	{
		//弹出一个视频帧
		VideoFrame* frame = que.front();
		que.pop();

		video_que.push(new VideoFrame(frame));

		que.push(frame);
	}

	//释放锁
	pthread_mutex_unlock(&video_queue_mutex);  
}


/************************************************************************/
/* 相队列中添加音频帧数据                                                                     */
/************************************************************************/
void Encoder::add_frame_to_audio_que(LoopQueue& que)
{
	int que_size = que.size();
	int start    = que.get_start();
	int end      = que.get_end();
	int len      = que.get_len();

	pthread_mutex_lock(&audio_queue_mutex);

	//遍历que 将数据添加到视频队列中去
	for (int i = start; i != end; i = (i + 1) % len)
	{
		if (audio_que.full())
			break;

		audio_que.push(que.get_byte(i));
	}

	pthread_mutex_unlock(&audio_queue_mutex);
}

//设置编码标志位
void Encoder::set_is_encoding(bool b)
{
	is_encoding = b;
}

/************************************************************************/
/* 得到编码标志位                                                                     */
/************************************************************************/
bool Encoder::get_is_encoding()
{
	return is_encoding;
}

/************************************************************************/
/* 开始编码 将队列中的数据编码成视频存储在相应的位置                                                                      */
/************************************************************************/

/**
 * 这个时候 视频和音频都放在队列中 且有一个标志为表示 当前是否可以结束 编码
 */
int Encoder::start_encoding()
{
	//-----------------------------------------------
	//LOGD("Encoder::start_encoding()");
	//-----------------------------------------------

	/*
	LOGD("file_path:%s", file_path.c_str());
	LOGD("video size:%d", video_que.size());

	LOGD("audio start:%d", audio_que.get_start());
	LOGD("audio end:%d", audio_que.get_end());
	LOGD("audio len:%d", audio_que.get_end());
	LOGD("audio size:%d", audio_que.size());
	*/

    /**
     * 路径不存在 直接返回-1
     视频队列为空 直接返回 －1
     */
	if(file_path.empty() || video_que.empty() || audio_que.empty())
        return -1;
    
    /**
     *  视频的宽度与高度 需要根据第一帧的视频确定 编码过程中
     *  出现宽度与高度 不符合的视频帧 直接丢弃
     */
    int video_width;
    int video_height;
    int video_bit_rate;
    
	//-----------------------------------------------
	//LOGD("VideoFrame* head_frame = video_que.front();");
	//-----------------------------------------------

    /**
     *  得到视频的宽度与高度
     */
    VideoFrame* head_frame = video_que.front();
    if(NULL == head_frame)
        return -1;  //为空 直接返回
    
    /**
     *  得到视频的宽与高 和 对应的bit率
     */
    video_width  = head_frame->width;
    video_height = head_frame->height;
    video_bit_rate = get_bit_rate_by_height(video_height);
    
    
    /**
     * ---------------------------------------------------------
     */
    //LOGD("video_width:%d", video_width);
    //LOGD("video_height:%d", video_height);
    //LOGD("video_bit_rate:%d", video_bit_rate);
    /**
     *
     */

    /**
     *  video_frame_size一张图片所占据的字节数  即video_width * video_height * 3 /2
        video_frame_buffer 存储一张图片的数组
     */
    //int video_frame_size;
    //uint8_t* video_frame_buffer = NULL;
    
    /**
     *  音频采样个数 每次需要向ffmpeg传入的音频的个数
     */
    int audio_nb_samples;
    
    /**
     *  原始的16位 即 从java层传入的音频对应的缓冲区大小
     *  和对应的缓冲区数组
     */
    int audio_temp_frame_size;
    uint8_t* audio_temp_frame_buffer = NULL;

    /**
     * 编码过后的音频缓存
     */
    int audio_frame_size;
    uint8_t* audio_frame_buffer = NULL;
    
    //输出文件属性上下文 和 输出“格式”
    AVFormatContext* pFormatCtx;
    AVOutputFormat* fmt;
    
    //对应的视频流 属性
    OutputStream video_stream;
    video_stream.next_pts = 0;
    
    /**
     *  音频流 和 对应的设置属性
     */
    OutputStream audio_stream;
    audio_stream.next_pts = 0;
    audio_stream.samples_count = 0;
    
    //注册
    av_register_all();
    
    //-----------------------------------------------
    //LOGD("avformat_alloc_output_context2");
    //-----------------------------------------------

    //初始化AVFormatContext  AVOutputFormat
    if (0 > avformat_alloc_output_context2(&pFormatCtx, NULL, NULL, file_path.c_str()))
    {
        //初始化AVFormatContext失败
        return -1;
    }
    
    //得到输出格式
    fmt = pFormatCtx->oformat;
    
    //-----------------------------------------------
	//LOGD("avio_open");
	//-----------------------------------------------

    //打开输出文件 用于写入数据
    if (0 > avio_open(&pFormatCtx->pb, file_path.c_str(), AVIO_FLAG_READ_WRITE))
    {
        return -1;
    }
    
    //-----------------------------------------------
	//LOGD("add_video_stream");
	//-----------------------------------------------

    //添加视频流
    if(0 > add_video_stream(&video_stream, pFormatCtx, fmt->video_codec, video_width, video_height, video_bit_rate))
    {
        return -1;
    }
    
    //-----------------------------------------------
	//LOGD("open_video");
	//-----------------------------------------------

    //打开视频缓存
    if(0 > open_video(&video_stream))
    {
        return -1;
    }
    
    //-----------------------------------------------
   	//LOGD("add_audio_stream");
   	//-----------------------------------------------

    //添加音频流
    if(0 > add_audio_stream(&audio_stream, pFormatCtx, fmt->audio_codec))
    {
        return -1;
    }

    //-----------------------------------------------
    //LOGD("open_audio");
    //-----------------------------------------------

    //打开音频缓存
    if(0 > open_audio(&audio_stream, &audio_temp_frame_size, &audio_temp_frame_buffer, &audio_frame_size, &audio_frame_buffer))
    {
        return -1;
    }

    //-----------------------------------------------
    //LOGD("avformat_write_header");
    //-----------------------------------------------

    //写入头文件
    if(0 > avformat_write_header(pFormatCtx, NULL))
    {
        return -1;
    }

    //-----------------------------------------------
	//LOGD("while((!video_que.empty() && !audio_que.empty()) || is_encoding)");
	//-----------------------------------------------

	bool enable_video = true;
	bool enable_audio = true;

    /**
     * 自由当视频和音频队列都有数据 或者 标志位为true的时候 进行编码
     */
    //while((!video_que.empty() && !audio_que.empty()) || is_encoding)
	//while((!video_que.empty() && !audio_que.empty()))
	while((enable_video && enable_audio) || is_encoding)
    {
    	//-----------------------------------------------
    	//LOGD("while!!!");
    	//-----------------------------------------------

        if (0 >= av_compare_ts(video_stream.next_pts, video_stream.pCodecCtx->time_base,
                               audio_stream.next_pts, audio_stream.pCodecCtx->time_base))
        {
        	//-----------------------------------------------
			//LOGD("video");
			//-----------------------------------------------

            //编码一帧视频
			enable_video = write_video_frame(&video_stream, pFormatCtx, video_width, video_height);

            //-----------------------------------------------
			//LOGD("video done!!");
			//-----------------------------------------------
        }
        else
        {
        	//-----------------------------------------------
			//LOGD("audio");
			//-----------------------------------------------


            //编码一帧音频
			enable_audio = write_audio_frame(&audio_stream, pFormatCtx, audio_temp_frame_buffer, audio_temp_frame_size);

            //-----------------------------------------------
			//LOGD("audio done!!");
			//-----------------------------------------------
        }
    }

    //-----------------------------------------------
	//LOGD("flush_video_encoder");
	//-----------------------------------------------

    //读取残留数据
    flush_video_encoder(&video_stream, pFormatCtx);
    flush_audio_encoder(&audio_stream, pFormatCtx);

    //写入尾部文件
    av_write_trailer(pFormatCtx);

    //关闭视频流相关的 参数
    avcodec_close(video_stream.pCodecCtx);
    av_frame_free(&video_stream.frame);
    //av_free(video_frame_buffer);

    //关闭音频相关
    avcodec_close(audio_stream.pCodecCtx);
    av_frame_free(&audio_stream.frame);
    av_frame_free(&audio_stream.temp_frame);
    av_free(audio_frame_buffer);
    av_free(audio_temp_frame_buffer);
    swr_free(&audio_stream.swr_ctx);

    //关闭文件
    avio_close(pFormatCtx->pb);
    avformat_free_context(pFormatCtx);


    //-----------------------------------------------
	//LOGD("return");
	//-----------------------------------------------

	return 1;
}

//根据视频高度得到对应的bitrate
int Encoder::get_bit_rate_by_height(int height)
{
    if(height <= video_bit_rates[0][0])
        return video_bit_rates[0][1];
    
    if(height >= video_bit_rates[1][0])
        return video_bit_rates[1][1];
    
    return (int)((video_bit_rates[1][1] - video_bit_rates[0][1]) / (video_bit_rates[1][0] - video_bit_rates[0][0]) * (height - video_bit_rates[0][0]) + video_bit_rates[0][1]);
}

/**
 添加视频流
 */
int Encoder::add_video_stream(OutputStream* oStream, AVFormatContext *pFormatCtx, enum AVCodecID codec_id, int video_width, int video_height, float bit_rate)
{
	//-----------------------------------------------
    //LOGD("avcodec_find_encoder");
    //-----------------------------------------------

    //查找对应的编码器
    oStream->pCodec = avcodec_find_encoder(codec_id);
    if(!oStream->pCodec)
    {
    	return -1;
	}
    
    //-----------------------------------------------
    //LOGD("avformat_new_stream");
    //-----------------------------------------------

    //创建视频流
    oStream->stream = avformat_new_stream(pFormatCtx, oStream->pCodec);
    if (!oStream->stream)
    {
        return -1;
    }
    
    //设置帧率和对应的ID
    oStream->stream->id = pFormatCtx->nb_streams - 1;
    oStream->stream->time_base.num = 1;
    oStream->stream->time_base.den = VIDEO_FPS;
    
    //设置编码器上下文
    oStream->pCodecCtx = oStream->stream->codec;
    oStream->pCodecCtx->codec_id   = codec_id;
    oStream->pCodecCtx->codec_type = AVMEDIA_TYPE_VIDEO;
    oStream->pCodecCtx->pix_fmt    = VIDEO_PIX_FMT;
    oStream->pCodecCtx->width      = video_width;
    oStream->pCodecCtx->height     = video_height;
    oStream->pCodecCtx->time_base.num = 1;
    oStream->pCodecCtx->time_base.den = VIDEO_FPS;
    oStream->pCodecCtx->bit_rate = bit_rate;
    //oStream->pCodecCtx->bit_rate = 400000;
    oStream->pCodecCtx->gop_size = 12;
    
    //-------------------------------------------------------
    /* Some formats want stream headers to be separate. */
    if (pFormatCtx->oformat->flags & AVFMT_GLOBALHEADER)
        oStream->pCodecCtx->flags |= CODEC_FLAG_GLOBAL_HEADER;
    
    //-----------------------------------------------
    //LOGD("avcodec_open2");
    //-----------------------------------------------

     int ret = avcodec_open2(oStream->pCodecCtx, oStream->pCodec, NULL);

     //LOGD("ret:%d", ret);
     //LOGD("video_width:%d" , video_width );
     //LOGD("video_height:%d", video_height);

    //打开编码器
    if (0 > ret)
    {
        return -1;
    }
    
    return 1;
}

//添加音频流
int Encoder::add_audio_stream(OutputStream* oStream, AVFormatContext *pFormatCtx, enum AVCodecID codec_id)
{
    //查找对应的编码器
    oStream->pCodec = avcodec_find_encoder(codec_id);
    if (!oStream->pCodec)
    {
        return -1;
    }
    
    //创建音频流
    oStream->stream = avformat_new_stream(pFormatCtx, oStream->pCodec);
    if (!oStream->stream)
    {
        return -1;
    }
    
    //设置音频流信息
    oStream->stream->id = pFormatCtx->nb_streams - 1;
    
    //设置编辑器上下文
    oStream->pCodecCtx = oStream->stream->codec;
    oStream->pCodecCtx->codec_id   = codec_id;
    oStream->pCodecCtx->codec_type = AVMEDIA_TYPE_AUDIO;
    oStream->pCodecCtx->bit_rate   = AUDIO_BIT_RATE;
    //我tmd的也不知道这句什么意思， 但是不加就会出现错误?????????????????????????
    oStream->pCodecCtx->strict_std_compliance = FF_COMPLIANCE_EXPERIMENTAL;
    
    //要根据 具体的情况您行 判断设置参数 不然可能出现问题
    oStream->pCodecCtx->sample_fmt = oStream->pCodec->sample_fmts ? oStream->pCodec->sample_fmts[0] : AV_SAMPLE_FMT_FLTP;
    
    //采样率
    oStream->pCodecCtx->sample_rate = AUDIO_SAMPLE_RATE;
    
    if (oStream->pCodec->supported_samplerates)
    {
        oStream->pCodecCtx->sample_rate = oStream->pCodec->supported_samplerates[0];
        
        for (int i = 0; oStream->pCodec->supported_samplerates[i]; i++)
        {
            if (oStream->pCodec->supported_samplerates[i] == AUDIO_SAMPLE_RATE)
                oStream->pCodecCtx->sample_rate = AUDIO_SAMPLE_RATE;
        }
    }
    
    oStream->pCodecCtx->channel_layout = AUDIO_CHANNEL_LAYOUT;	//根据channel_layout得到channels
    if (oStream->pCodec->channel_layouts)
    {
        oStream->pCodecCtx->channel_layout = oStream->pCodec->channel_layouts[0];
        for (int i = 0; oStream->pCodec->channel_layouts[i]; i++)
        {
            if (oStream->pCodec->channel_layouts[i] == AUDIO_CHANNEL_LAYOUT)
                oStream->pCodecCtx->channel_layout = AUDIO_CHANNEL_LAYOUT;
        }
    }
    //根据channel_layout得到channels
    oStream->pCodecCtx->channels = av_get_channel_layout_nb_channels(oStream->pCodecCtx->channel_layout);
    
    //采样率有可能变化 需要重新设置stream的time_base
    oStream->stream->time_base.num = 1;
    oStream->stream->time_base.den = oStream->pCodecCtx->sample_rate;

    /* Some formats want stream headers to be separate. */
    if (pFormatCtx->oformat->flags & AVFMT_GLOBALHEADER)
        oStream->pCodecCtx->flags |= CODEC_FLAG_GLOBAL_HEADER;
    
    
    //打开编码器
    if (0 > avcodec_open2(oStream->pCodecCtx, oStream->pCodec, NULL))
    {
        return -1;
    }
    
    return 1;
}


//打开视频缓存
//int Encoder::open_video(OutputStream* oStream, int* frame_size, uint8_t** frame_buffer)
int Encoder::open_video(OutputStream* oStream)
{
    //oStream->frame = av_frame_alloc();
    //(*frame_size) = avpicture_get_size(VIDEO_PIX_FMT, oStream->pCodecCtx->width, oStream->pCodecCtx->height);
    //(*frame_buffer) = (uint8_t *)av_malloc(*frame_size);
    //avpicture_fill((AVPicture *)oStream->frame, (*frame_buffer), VIDEO_PIX_FMT, oStream->pCodecCtx->width, oStream->pCodecCtx->height);
    
	oStream->frame = av_frame_alloc();
	if(!oStream->frame)
		return -1;

    oStream->frame->format = VIDEO_PIX_FMT;
    oStream->frame->width  = oStream->pCodecCtx->width;
    oStream->frame->height = oStream->pCodecCtx->height;
    
    return 1;
}

//打开音频缓存
int Encoder::open_audio(OutputStream* oStream, int* temp_frame_size, uint8_t** temp_frame_buffer,
                        int* frame_size, uint8_t** frame_buffer)
{
    int audio_nb_samples = oStream->pCodecCtx->frame_size;

    //初始化temp_frame
    oStream->temp_frame = av_frame_alloc();
    if(!oStream->temp_frame)
    	return -1;

    (*temp_frame_size) = av_samples_get_buffer_size(NULL, AUDIO_CHANNEL_NUM, audio_nb_samples, AUDIO_PIX_FMT, 1);
    (*temp_frame_buffer) = (uint8_t*)av_malloc(*temp_frame_size);
    avcodec_fill_audio_frame(oStream->temp_frame, AUDIO_CHANNEL_NUM, AUDIO_PIX_FMT, (const uint8_t*)(*temp_frame_buffer), (*temp_frame_size), 1);

    oStream->temp_frame->data[0] = (*temp_frame_buffer);
    oStream->temp_frame->format = AUDIO_PIX_FMT;
    oStream->temp_frame->channel_layout = AUDIO_CHANNEL_LAYOUT;
    oStream->temp_frame->sample_rate = AUDIO_SAMPLE_RATE;
    oStream->temp_frame->nb_samples = audio_nb_samples;

    //初始化frame 存储转格式后的音频
    oStream->frame = av_frame_alloc();
    if(!oStream->frame)
        	return -1;
    (*frame_size) = av_samples_get_buffer_size(NULL, oStream->pCodecCtx->channels, audio_nb_samples, oStream->pCodecCtx->sample_fmt, 1);
    (*frame_buffer) = (uint8_t*)av_malloc(*frame_size);
    avcodec_fill_audio_frame(oStream->frame, oStream->pCodecCtx->channels, oStream->pCodecCtx->sample_fmt, (const uint8_t*)(*frame_buffer), (*frame_size), 1);

    oStream->frame->data[0] = (*frame_buffer);
    oStream->frame->format = oStream->pCodecCtx->sample_fmt;
    oStream->frame->channel_layout = oStream->pCodecCtx->channel_layout;
    oStream->frame->sample_rate = oStream->pCodecCtx->sample_rate;
    oStream->frame->nb_samples = audio_nb_samples;

    //用于转格式
    oStream->swr_ctx = swr_alloc();
    if (!oStream->swr_ctx)
    {
        return -1;
    }

    av_opt_set_int(oStream->swr_ctx, "in_channel_count", AUDIO_CHANNEL_NUM, 0);	//输入数据的声道个数
    av_opt_set_int(oStream->swr_ctx, "in_sample_rate", AUDIO_SAMPLE_RATE, 0);	//输入数据的采样率
    av_opt_set_sample_fmt(oStream->swr_ctx, "in_sample_fmt", AUDIO_PIX_FMT, 0);	//原始音频数据，可能根据情况转成别的格式数据

    av_opt_set_int(oStream->swr_ctx, "out_channel_count", oStream->pCodecCtx->channels, 0);	//输出数据的声道个数
    av_opt_set_int(oStream->swr_ctx, "out_sample_rate", oStream->pCodecCtx->sample_rate, 0);	//输出音频数据的采样率
    av_opt_set_sample_fmt(oStream->swr_ctx, "out_sample_fmt", oStream->pCodecCtx->sample_fmt, 0);	//输出音频数据的格式

    if (0 > swr_init(oStream->swr_ctx))
    {
        return -1;
    }

    return 1;
}


/**
 * 对一帧视频进行编码 首先从队列中读取一帧数据 然后进行编码
 */
//bool Encoder::write_video_frame(OutputStream* oStream, AVFormatContext *pFormatCtx, uint8_t* buffer, int buffer_size, int width, int height)
bool Encoder::write_video_frame(OutputStream* oStream, AVFormatContext *pFormatCtx, int width, int height)
{
//    int ret = -1;
//
//    //------------------------------
//    //LOGD("while(!video_que.empty())");
//    //LOGD("buffer_size:%d", buffer_size);
//    //LOGD("width:%d", width);
//    //LOGD("height:%d", height);
//    //----------------------------
//
//    /*
//     * 从队列中读取一帧数据
//     */
//    while(!video_que.empty())
//    {
//        //读取一帧视频数据
//        VideoFrame* frame = NULL;
//
//        /**
//         * is_encoding为true表示 还没有拷贝第二段数据
//         * 这个时候需要对 队列进行加锁
//         * 如果is_encoding是false 表示第二段数据已经拷贝 就不需要加锁了
//         */
//        if(is_encoding)
//        {
//            pthread_mutex_lock(&video_queue_mutex); //对视频队列进行加锁
//        }
//
//        frame = video_que.front();
//        video_que.pop();
//
//        if(is_encoding)
//        {
//            pthread_mutex_unlock(&video_queue_mutex);   //释放锁
//        }
//
//        //为null继续下一个
//        if(NULL == frame)   continue;
//
//        //宽度与高度不对应直接返回
//        if(width != frame->width || height != frame->height)
//        {
//            delete frame;
//            continue;
//        }
//
//        //将数据拷贝到 缓存数组中
//        for(int i = 0; i < buffer_size; ++i)
//            buffer[i] = frame->data[i];
//
//        delete frame;
//
//        ret = 1;
//
//        break;
//    }
//
//    //------------------------------
//    //LOGD("if(0 > ret)");
//    //----------------------------
//
//    if(0 > ret)	return false;

	int ret = -1;
	VideoFrame* frame = NULL;

	/**
	 * 读取一帧数据 和ostream->frame进行连接
	 */
	while(!video_que.empty())
	{
		/**
		 * is_encoding为true表示 还没有拷贝第二段数据
		 * 这个时候需要对 队列进行加锁
		 * 如果is_encoding是false 表示第二段数据已经拷贝 就不需要加锁了
		 */
		if(is_encoding)
		{
			pthread_mutex_lock(&video_queue_mutex); //对视频队列进行加锁
		}

		frame = video_que.front();
		video_que.pop();

		if(is_encoding)
		{
			pthread_mutex_unlock(&video_queue_mutex);   //释放锁
		}

		if(NULL == frame)
			continue;

		if(width != frame->width || height != frame->height)
		{
			delete frame;
			frame = NULL;

			continue;
		}

		//将数据和ostream进行连接 避免拷贝
		avpicture_fill((AVPicture *)oStream->frame, frame->data, VIDEO_PIX_FMT, width, height);

		ret = 1;
		break;
	}

	//没有数据直接返回
	if(0 > ret)
	{
		if(NULL != frame)
		{
			delete frame;
			frame = NULL;
		}

		return false;
	}

    //读取数据成功
    //数据已经读到buffer 及OutputStream对应的frame
    //进行转码加写入文件

    //设置pts
    oStream->frame->pts = oStream->next_pts++;

    //定义一个packet
    AVPacket pkt = { 0 };
    av_init_packet(&pkt);

    int got_packet;

    ret = avcodec_encode_video2(oStream->pCodecCtx, &pkt, oStream->frame, &got_packet);

    /**
     * 编码结束直接删除frame
     */
    if(NULL != frame)
    {
    	delete frame;
    	frame = NULL;
    }

    if(0 > ret)
    	return false;

    if(got_packet)
    {
        pkt.stream_index = oStream->stream->index;
        av_packet_rescale_ts(&pkt, oStream->pCodecCtx->time_base, oStream->stream->time_base);

        //写入数据
        av_write_frame(pFormatCtx, &pkt);

        //释放pkt
        av_free_packet(&pkt);
    }

    return true;
}


/**
 * 读取音频 数据并编码
 */
bool Encoder::write_audio_frame(OutputStream* oStream, AVFormatContext *pFormatCtx, uint8_t* buffer, int buffer_size)
{
    //读取队列将音频写入到缓存中

    /*
     * 数据不够直接返回
     */
    if(audio_que.size() < buffer_size)
        return false;

    //---------------------------------------------------------------------------------------------------
    //加锁 将队列中的数据 读取到缓存中
    if(is_encoding)
    {
        pthread_mutex_lock(&audio_queue_mutex);
    }

    for(int i = 0; i < buffer_size; ++i)
    {
        buffer[i] = audio_que.front();
        audio_que.pop();
    }

    if(is_encoding)
    {
        pthread_mutex_unlock(&audio_queue_mutex);
    }
    /**
     * ----------------------------------------------------------------------------
     */

    //继续编码
    //数据已经写入temp_frame 将数据转码到 frame
    oStream->temp_frame->pts = oStream->next_pts;
    oStream->next_pts += oStream->temp_frame->nb_samples;

    int dst_nb_samples = av_rescale_rnd(swr_get_delay(oStream->swr_ctx, oStream->pCodecCtx->sample_rate) + oStream->temp_frame->nb_samples,
                                        oStream->pCodecCtx->sample_rate, oStream->pCodecCtx->sample_rate, AV_ROUND_UP);

    //音频格式的转码
    swr_convert(oStream->swr_ctx, oStream->frame->data, dst_nb_samples,
                (const uint8_t **)oStream->temp_frame->data, oStream->temp_frame->nb_samples);

    AVRational avR;
    avR.num = 1;
    avR.den = oStream->pCodecCtx->sample_rate;
    oStream->frame->pts = av_rescale_q(oStream->samples_count, avR, oStream->pCodecCtx->time_base);
    oStream->samples_count += dst_nb_samples;

    //初始化一个AVPacket
    AVPacket pkt = { 0 };
    av_init_packet(&pkt);

    int ret;
    int got_packet;

    //转码
    ret = avcodec_encode_audio2(oStream->pCodecCtx, &pkt, oStream->frame, &got_packet);

    if(0 > ret)
        return false;

    if(got_packet)
    {
        pkt.stream_index = oStream->stream->index;
        av_packet_rescale_ts(&pkt, oStream->pCodecCtx->time_base, oStream->stream->time_base);

        av_write_frame(pFormatCtx, &pkt);

        //释放pkt
        av_free_packet(&pkt);
    }

    return true;
}


//清理残余的数据
void Encoder::flush_video_encoder(OutputStream* oStream, AVFormatContext *pFormatCtx)
{
    int ret;
    int got_packet;

    while(true)
    {
        AVPacket pkt = { 0 };
        av_init_packet(&pkt);

        ret = avcodec_encode_video2(oStream->pCodecCtx, &pkt, NULL, &got_packet);

        if (0 > ret || !got_packet)
        {
            break;
        }

        //写入到文件
        pkt.stream_index = oStream->stream->index;
        av_packet_rescale_ts(&pkt, oStream->pCodecCtx->time_base, oStream->stream->time_base);

        av_write_frame(pFormatCtx, &pkt);

        //释放pkt
        av_free_packet(&pkt);
    }
}
void Encoder::flush_audio_encoder(OutputStream* oStream, AVFormatContext *pFormatCtx)
{
    int ret;
    int got_packet;

    while (true)
    {
        AVPacket pkt = { 0 };
        av_init_packet(&pkt);

        ret = avcodec_encode_audio2(oStream->pCodecCtx, &pkt, NULL, &got_packet);

        if (0 > ret || !got_packet)
        {
            break;
        }

        //写入到文件
        pkt.stream_index = oStream->stream->index;
        av_packet_rescale_ts(&pkt, oStream->pCodecCtx->time_base, oStream->stream->time_base);

        av_write_frame(pFormatCtx, &pkt);

        //释放pkt
        av_free_packet(&pkt);
    }
}
