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

import java.io.File;

import com.deepcolor.deepred.callback.EncodeCallback;
import com.deepcolor.deepred.util.FileUtil;
import com.deepcolor.deepred.view.FlashView;

import android.R.integer;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.view.View;

/**
 * 摄影类 包括 录制视频 和 拍摄一张图片等所有的关于拍摄的操作
 * @author WSH
 *
 */
public class ShotInstance implements Camera.PreviewCallback, AudioInstance.RecordCallback
{
	/**
	 * 摄像机相关的属性
	 */
	private CameraInstance cameraInstance = null;	//相机实例 包括各种 相机的操作
	private byte[] cameraBuffer = null;	//每一帧视频数据的缓存
	private Size previewSize    = null;	//相机预览画面的大小
	private float viewRatio     = -1;	//控件 即 视觉比例 呈现给用户的宽度与高度的比例 因为手机屏幕和相机的预览的比例有可能不相同
	
	/**
	 * 和录制音频相关的属性
	 */
	private AudioInstance audioInstance = null;	//音频录制类
		
	/**
	 * 是否打开livephoto
	 */
	boolean livePhotoChecked = true;	//是否开启livephoto默认打开
	
	public ShotInstance()
	{
		init();
	}
	
	/**
	 * 初始化需要的各种参数
	 */
	private void init()
	{
		/**
		 * 初始化摄像头预览回调
		 */
		cameraInstance = CameraInstance.getInstance();
		initCameraParams();	//初始化摄像头相关的参数
		
		/**
		 * 初始化音频录制函数
		 */
		audioInstance  = AudioInstance.getInstance();
		audioInstance.setRecorderCallback(this);
		audioInstance.startRecording();	//开始录制音频
	}
	
	/**
	 * 重置关于摄像投的参数信息
	 * 第一次 设 摄像头切换的时候都需要调用
	 */
	public void initCameraParams()
	{
		/**
		 * 得到预览画面的大小
		 */
		previewSize = cameraInstance.getPreviewSize();
		
		if(null == previewSize) return;
		
		/**
		 * 设置缓冲区和回调
		 */
		cameraBuffer = new byte[previewSize.width * previewSize.height * 3 / 2];
		cameraInstance.setPreviewCallback(this, cameraBuffer);
		
		/**
		 * 预览大小改变需要重新设置c++层的各种缩放参数
		 */
		jniSetPreviewSizeAndRatio(previewSize.width, previewSize.height, viewRatio);
		
		/**
		 * 清理队列中的数据 和 结束当前所有的编码操作
		 */
		jniClearData();
		jniEndAllEncoder();
	}
	
	/**
	 * 设置控件比例
	 * @param ratio
	 */
	public void setViewRatio(float ratio)
	{
		viewRatio = ratio;
		
		/**
		 * 预览大小改变需要重新设置c++层的各种缩放参数
		 */
		jniSetPreviewSizeAndRatio(previewSize.width, previewSize.height, viewRatio);
	}
	
	/**
	 * 是否开启 livephoto
	 * @param checked
	 */
	public void setLivePhotoChecked(boolean isChecked)
	{
		livePhotoChecked = isChecked;
	}	
	
	/**
	 * 执行拍摄操作
	 */
	public void takePicture()
	{
		/**
		 * -----------------------------------------
		 * 首先拍摄一张图片 并根据比例进行裁剪
		 * -----------------------------------------
		 */
		
		//根据当前时间得到视频的路径和文件名
		long curTime = System.currentTimeMillis();
		String fileKey = String.valueOf(curTime);
		
		/**
		 * 拍摄一段视频
		 */
		if(livePhotoChecked)
		{
			//开启线程进行编码
			//new Thread(new LivePhotoRunnable(videoFilePath)).start();
			//视频路径
			String videoFilePath = FileUtil.getAppPath() + File.separator + fileKey + VIDEO_DIFF;
			
			/**
			 * 异步执行编码操作
			 */
			new LivePhotoAsyncTask(videoFilePath).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, 0);
		}
	}
	
	/**
	 * ----------------------------------------------------------------------------------------------------------------
	 * ----------------------------------------------------------------------------------------------------------------
	 * ----------------------------------------------------------------------------------------------------------------
	 */
	
	/**
	 * 编码回调 每次进行编码都进行回调操作
	 */
	private EncodeCallback encodeCallback = null;
	public void setEncodeCallback(EncodeCallback e)
	{
		encodeCallback = e;
	}
	
	//消息和对应的延迟时间
	private static final int ADD_SECOND_PART_MSG = 0;	//添加第二段数据的消息
	private static final int ADD_SECOND_PART_MSG_DELAY_TIME = 1500;	//1500毫秒 的延迟时间
	private static final String VIDEO_DIFF = ".mp4";	//文件的后缀名
	
	/**
	 * 处理事件消息
	 */
	private Handler handler = new Handler()
	{
		@Override
		public void dispatchMessage(Message msg)
		{
			switch(msg.what)
			{
			case ADD_SECOND_PART_MSG:
				/*
				 * 添加第二段数据
				 */
				Integer encoderId = (Integer)msg.obj;
				if(null != encoderId)
				{
					//jniAddSecondPartToEncoder(encoderId.intValue());
					//new Thread(new AddSecondPartRunnable(encoderId.intValue())).start();
					//异步添加第二段数据
					new AddSecondPartAsyncTask(encoderId.intValue()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, 0);
				}
				
		        break;
			}
		}
	};
	
//	private class AddSecondPartRunnable implements Runnable
//	{
//		int encoderId = -1;
//		
//		AddSecondPartRunnable(int id)
//		{
//			encoderId = id;
//		}
//		
//		@Override
//		public void run() 
//		{
//			if(0 < encoderId)
//				jniAddSecondPartToEncoder(encoderId);
//		}
//		
//	}
	
	/**
	 * 添加第二段数据
	 * @author apple
	 *
	 */
	private class AddSecondPartAsyncTask extends AsyncTask<Integer, Integer, String>
	{
		int encoderId = -1;
		
		public AddSecondPartAsyncTask(int id)
		{
			encoderId = id;
		}

		@Override
		protected String doInBackground(Integer... params) 
		{
			if(0 < encoderId)
				jniAddSecondPartToEncoder(encoderId);

			return null;
		}
	}
	
	
	/**
	 * 使用AsyncTask进行后台处理 是一个线程池 减少初始化线程的时间但是 最多只能5个 任务同时进行?
	 */
	private class LivePhotoAsyncTask extends AsyncTask<Integer, Integer, String>
	{
		/**
		 * 视频编码的路径 一定要以.mp4结尾
		 */
		String filePath;
		
		public LivePhotoAsyncTask(String path)
		{
			filePath = path;
		}

		@Override
		protected String doInBackground(Integer... params) 
		{
			/**
			 * ------------------------------------
			 * 记录时间
			 */
			long time = System.currentTimeMillis();
			//
			
			/**
			 * 开始编码操作
			 */
			if(null != encodeCallback)
				encodeCallback.startEncode();
			
			/**
			 * 初始化一个编码器 并返回编码器的id
			 */
			int encoderId = jniInitEncoder(filePath);
			
			/**
			 * 编码器初始化失败 直接返回
			 */
			if(0 > encoderId)
			{
				//编码失败
				if(null != encodeCallback)
					encodeCallback.failEncode();
				
				return null;
			}
			
			/**
			 * 发送一个延迟 消息 拷贝第二段数据
			 */
			Message msg = new Message();
			msg.what = ADD_SECOND_PART_MSG;
			msg.obj  = new Integer(encoderId);
			
			handler.sendMessageDelayed(msg, ADD_SECOND_PART_MSG_DELAY_TIME);
			
			/**
			 * 开始编码操作 耗时操作 编码的过程中 会收到第二部分的数据
			 */
			int ret = jniStartEncoding(encoderId);
			
			if(0 > ret)
			{
				//编码失败
				if(null != encodeCallback)
					encodeCallback.failEncode();
				
				return null;
			}
			
			//编码成功
			if(null != encodeCallback)
				encodeCallback.finishEncode();
			
			
			/**
			 * ----------------------------------------------------------------------
			 */
			System.out.println("时间：" + (System.currentTimeMillis() - time));
			
			return null;
		}
	}
	
	
	
//	/**
//	 * 多线程调用 进行livephioto的编码
//	 * @author apple
//	 *
//	 */
//	private class LivePhotoRunnable implements Runnable
//	{
//		/**
//		 * 视频编码的路径 一定要以.mp4结尾
//		 */
//		String filePath;
//		
//		public LivePhotoRunnable(String path) 
//		{
//			filePath = path;
//		}
//		
//		@Override
//		public void run() 
//		{
//			/**
//			 * ------------------------------------
//			 */
//			long time = System.currentTimeMillis();
//			
//			
//			/**
//			 * 开始编码操作
//			 */
//			if(null != encodeCallback)
//				encodeCallback.startEncode();
//			
//			/**
//			 * 初始化一个编码器 并返回编码器的id
//			 */
//			int encoderId = jniInitEncoder(filePath);
//			
//			/**
//			 * 编码器初始化失败 直接返回
//			 */
//			if(0 > encoderId)
//			{
//				//编码失败
//				if(null != encodeCallback)
//					encodeCallback.failEncode();
//				
//				return;
//			}
//			
//			/**
//			 * 发送一个延迟 消息 拷贝第二段数据
//			 */
//			Message msg = new Message();
//			msg.what = ADD_SECOND_PART_MSG;
//			msg.obj  = new Integer(encoderId);
//			
//			handler.sendMessageDelayed(msg, ADD_SECOND_PART_MSG_DELAY_TIME);
//			
//			/**
//			 * 开始编码操作 耗时操作 编码的过程中 会收到第二部分的数据
//			 */
//			int ret = jniStartEncoding(encoderId);
//			
//			if(0 > ret)
//			{
//				//编码失败
//				if(null != encodeCallback)
//					encodeCallback.failEncode();
//				
//				return;
//			}
//			
//			//编码成功
//			if(null != encodeCallback)
//				encodeCallback.finishEncode();
//			
//			
//			/**
//			 * ----------------------------------------------------------------------
//			 */
//			System.out.println("时间：" + (System.currentTimeMillis() - time));
//		}
//	}

	//long time;
	
	/**
	 * 有新的摄像头数据时 调用
	 */
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) 
	{	
		//long time = System.currentTimeMillis();
		
		
		/**
		 * 将数据传入到jni层
		 */
		jniOnReceiveVideoFrame(data, data.length, previewSize.width, previewSize.height, viewRatio, cameraInstance.getCameraFacing());
		
		/**
		 * 每次都要设置？
		 */
		cameraInstance.getCamera().addCallbackBuffer(cameraBuffer);
		
		/**
		 * ==================================================
		 * 时间测试
		 */
		//long curTime = System.currentTimeMillis();
		
		//System.out.println("onPreviewFrame间隔：" + (curTime - time));
		//time = curTime;
	}
	
	/**
	 * 当有新的音频数据时
	 */
	@Override
	public void onRecordFrame(byte[] data, int len) 
	{
		/**
		 * 将音频数据传入到jni层
		 */
		jniOnReceiveAudioFrame(data, len);
	}
	
	/**
	 * ---------------------------------------------------------------
	 * jni代码
	 * ------------------------------------------------------------------
	 */
	
	/**
	 * 设置摄象头的预览大小的用于的预览大小
	 * @param width
	 * @param height
	 * @param ratio
	 * @return
	 */
	private native int jniSetPreviewSizeAndRatio(int width, int height, float ratio);
	
	
	/**
	 * 将视频数据传入到 jni层
	 * @param data
	 * @param len
	 * @param width
	 * @param height
	 * @param ratio
	 * @param cameraFacing
	 */
	private native void jniOnReceiveVideoFrame(byte[] data, int len, int width, int height, float ratio, int cameraFacing);
	
	/**
	 * 将音频数据传输到jni层
	 * @param data
	 * @param len
	 */
	private native void jniOnReceiveAudioFrame(byte[] data, int len);
	
	/**
	 * 初始化一个编码器 返回 编码器的id
	 * @param path
	 * @return
	 */
	private native int jniInitEncoder(String path);
	
	/**
	 * 开始一个编码器 传入编码器的id
	 * @param id
	 * @return
	 */
	private native int jniStartEncoding(int id);
	
	/**
	 * 将第二部分的数据拷贝到 对应的编码器
	 * @param id
	 * @return
	 */
	private native int jniAddSecondPartToEncoder(int id);
	
	/**
	 * 结束所有的编码器 
	 */
	private native void jniEndAllEncoder();
	
	/**
	 * 清理所有数据
	 */
	private native void jniClearData();
	
	static
	{
		System.loadLibrary("livephoto");
	}
}








