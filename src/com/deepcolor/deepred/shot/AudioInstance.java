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

package com.deepcolor.deepred.shot;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;

/**
 * 录制音频需要的函数 和 类
 * @author WSH
 *
 */
public class AudioInstance implements Runnable
{
	/**
	 * 初始化成单例类
	 */
	private static AudioInstance audioInstance = null;
	
	public static synchronized AudioInstance getInstance()
	{
		if(null == audioInstance)
		{
			audioInstance = new AudioInstance();
		}
		
		return audioInstance;
	}
	
	private AudioInstance(){}
	
	/**
	 * -----------------------------------------------------------------------
	 */
	private static final int SAMPLE_RATE    = 44100; //  采样率
	private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;	//单声道
	private static final int AUDIO_FORMAT   = AudioFormat.ENCODING_PCM_16BIT;	//数据为16bit
	private static final int AUDIO_SOURCE   = AudioSource.MIC;	//音频来源
	
	
	private AudioRecord audioRecord = null;	//录音类
	
	private byte[] sampleBuffers = null;	//存储每次采样的数据
	private int minBufferSize    = -1;		//最小的采样缓存大小
	
	private boolean isRecording = false;	//记录是否正在录制音频
	
	private RecordCallback recordCallback = null;	//有数据时回调
	
	/**
	 * 设置音频回调
	 * @param r
	 */
	public void setRecorderCallback(RecordCallback r)
	{
		recordCallback = r;
	}
	
	/**
	 * 开始录制音频
	 */
	public void startRecording()
	{
		isRecording = true;
		
		/**
		 * 开启线程进行音频录制
		 */
		new Thread(this).start();
	}
	
	
	/**
	 * 结束录制音频
	 */
	public void stopRecording()
	{
		isRecording = false;
	}
	
	@Override
	public void run() 
	{
		/**
		 * 得到最小的缓冲区大小
		 */
		minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
		
		if(0 >= minBufferSize)	return;
		
		/**
		 * 初始化音频录制类
		 */
		audioRecord = new AudioRecord(AUDIO_SOURCE, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSize);
		
		if(null == audioRecord)	return;
		
		/**
		 * 开始录制视频
		 */
		try 
		{
			audioRecord.startRecording();
		} 
		catch (IllegalStateException e) 
		{
			return;
		}
		
		/**
		 * 初始化缓冲区
		 */
		sampleBuffers = new byte[minBufferSize];
		
		while(isRecording)
		{
			int result = audioRecord.read(sampleBuffers, 0, minBufferSize);
			
			if(0 < result && null != recordCallback)
			{
				recordCallback.onRecordFrame(sampleBuffers, Math.min(result, minBufferSize));
			}
		}
		
		/**
		 * 结束录制
		 */
		audioRecord.release();
		audioRecord = null;
		sampleBuffers = null;
		minBufferSize = -1;
	}
	





	/**
	 * -----------------------------------------------------------------------
	 * 自定义一个回调 当有数据时调用
	 * -------------------------------------------------------------------------
	 */
	interface RecordCallback
	{
		/**
		 * 当有数据时调用
		 * @param data
		 */
		public void onRecordFrame(byte[] data, int len);
	}
}




















