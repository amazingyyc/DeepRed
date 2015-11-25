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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.deepcolor.deepred.callback.FocusCameraCallback;
import com.deepcolor.deepred.callback.SwitchCameraCallback;
import com.deepcolor.deepred.callback.SwitchFlashModeCallback;
import com.deepcolor.deepred.util.MathUtil;

import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;

/**
 * camera的单例类 负责摄像头的切换 预览 对焦等操作
 * @author WSH
 *
 */
public class CameraInstance 
{
	//一个实例 单例模式
	private static CameraInstance cameraInstance = null;
	
	public static synchronized CameraInstance getInstance()
	{
		if(null == cameraInstance)
		{
			cameraInstance = new CameraInstance();
		}
		
		return cameraInstance;
	}
	
	private CameraInstance()
	{
		/**
		 * 打开一个相机
		 */
		openCamera();
	}
	
	//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	
	private Camera camera = null;	//摄像头
	private int cameraId  = -1;		//摄像头id
	private Camera.Size previewSize = null;	//预览大小 当前摄像头对应的图片的预览大小
	
	private boolean isPreviewing = false;	//标志当前是否正在预览摄像头	
	
	public Camera getCamera() 
	{
		return camera;
	}

	public int getCameraId() 
	{
		return cameraId;
	}

	public Camera.Size getPreviewSize() 
	{
		return previewSize;
	}

	public boolean isPreviewing()
	{
		return isPreviewing;
	}

	/**
	 * 打开一个默认的相机
	 */
	public boolean openCamera()
	{
		return openCamera(0);
	}
	
	/**
	 * 打开一个对应的id的camera
	 * 如果没有摄像头返回false
	 * @param id
	 */
	public boolean openCamera(int id)
	{
		//如果当前相机已经打开
		if(null != camera)
		{
			//已经打开了对应id的相机 直接返回
			if(id == cameraId)	
				return true;	
			
			//释放当前相机
			releaseCamera();
		}
		
		//如果没有摄像头直接返回false
		int numberOfCameras = Camera.getNumberOfCameras();
		if(0 == numberOfCameras)
			return false;
		
		if(0 > id || id >= numberOfCameras)
			id = 0;
		
		camera   = Camera.open(id);
		cameraId = id;
		
		//初始化摄像头的相关参数
		initCameraParams(camera, cameraId);
		
		return true;
	}
	
	/**
	 * 释放当前相机
	 */
	public void releaseCamera()
	{
		if(null != camera)
		{
			stopPreview();	//首先停止预览
			
			camera.release();	//释放摄像头
						
			camera      = null;
			cameraId    = -1;
			previewSize = null;
		}
	}
	
	/**
	 * 停止预览摄像头
	 */
	private void stopPreview()
	{
		if(isPreviewing)
		{
			if(null != camera)
			{
				//camera.cancelAutoFocus();	//停止对焦
				camera.stopPreview();
				camera.addCallbackBuffer(null);
				camera.setPreviewCallbackWithBuffer(null);
			}
			
			isPreviewing = false;
		}
	}
	
	/**
	 * 开始预览相机
	 */
	public void startPreview(SurfaceTexture surfaceTexture)
	{
		//停止当前的预览
		if(isPreviewing)
		{
			stopPreview();
		}
		
		if(null == camera)	return;
		
		//开启预览
		try 
		{
			camera.setPreviewTexture(surfaceTexture);
			camera.startPreview();
			
			isPreviewing = true;
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * ---------------------------------------------------------------
	 * 最小宽度 预览的大小是大与最小宽度的最小值 减少缩放和肤质的时间
	 * ---------------------------------------------------------------
	 */
	private static final int MIN_PREVIEW_WIDTH = 400;	//像素值
	
	/**
	 * 初始化摄像头的相关参数
	 * @param camera
	 * @param cameraId
	 */
	private void initCameraParams(Camera camera, int cameraId)
	{
		if(null == camera)	return;
		
		//得到摄像头的配置信息
		Parameters parameters = camera.getParameters();
		
		/**
		 * 预览的帧数
		 */
		int[] range = getPreviewFpsRange(parameters);
		parameters.setPreviewFpsRange(range[0], range[1]);
		
		/**
		 * 预览格式 测试！！！！！！！！！！！！！！！！！
		 */
		//parameters.setPreviewFormat(ImageFormat.YV12);	
		//parameters.getS
		
		//得到支持的对焦模式
		List<String> focusMode = parameters.getSupportedFocusModes();
		if(focusMode.contains(Parameters.FOCUS_MODE_AUTO))
		{
			//设置为 自动对焦模式
			parameters.setFocusMode(Parameters.FOCUS_MODE_AUTO);
		}
		
		/**
		 * 设置图片的预览大小
		 */
		previewSize = getPreviewSize(parameters, MIN_PREVIEW_WIDTH);
		parameters.setPreviewSize(previewSize.width, previewSize.height);
		
		//设置相机属性
		camera.setParameters(parameters);
	}
	
	/**
	 * 根据支持的预览帧率设置 帧率
	 */
	private int[] getPreviewFpsRange(Parameters parameters)
	{
		List<int[]> ranges = parameters.getSupportedPreviewFpsRange();
		int[] range = ranges.get(0);
		
		//进行遍历 
		for(int[] r : ranges)
		{
			if(r[1] > range[1] || (r[1] == range[1] && r[0] > range[0]))
			{
				range = r;
			}
		}
		
		return range;
	}
	
	/**
	 * 根据当前支持的预览大小 得到大于等于width的最小预览大小
	 * @param parameters
	 * @param width
	 * @return
	 */
	private Camera.Size getPreviewSize(Parameters parameters, int width)
	{
		/**
		 * 查找大于等于width的最小值 如果查找不到直接返回第一个预览大小
		 */
		List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
		Camera.Size curSize = previewSizes.get(0);
		
		boolean bigger = curSize.width >= width;
		int diff = Math.abs(curSize.width - width);
		
		int num = previewSizes.size();
		for(int i = 0; i < num; ++i)
		{
			Camera.Size size = previewSizes.get(i);
			boolean tBigger = size.width >= width;
			int tDiff = Math.abs(size.width - width);
			
			if((!bigger && tBigger) || 
			    (bigger && tBigger && (tDiff < diff)) ||
			    (!bigger && !tBigger && (tDiff < diff)))
			{
				bigger = tBigger;
				diff   = tDiff;
				curSize = size;
			}
		}
		
		return curSize;
	}
	
	/**
	 * ----------------------------------------------------------------------------------------
	 * 对焦相关的函数 
	 * ---------------------------------------------------------------------------------------
	 */
	private static final int FOCUS_AREA_SIZE = 300;	//对焦区域的大小
	
	private FocusCameraCallback focusCameraCallback  = null;	//自定义的对焦回调
	private AutoFocusCallback autoFacusCallback      = null;	//系统自带的对焦回调 完成对焦的时候调用
	
	/**
	 * 设置对焦回调
	 * @param callback
	 */
	public void setFocusCallback(FocusCameraCallback f)
	{
		if(null == f)	return;
		
		//自定义的对焦回调
		focusCameraCallback = f;
		
		autoFacusCallback = new AutoFocusCallback() 
		{
			@Override
			public void onAutoFocus(boolean success, Camera camera) 
			{
				if(null != focusCameraCallback)
				{
					/**
					 * 完成对焦
					 */
					if(success)
						focusCameraCallback.finishFocus();
					else
						focusCameraCallback.cancelFocus();
				}
			}
		};
	}
	
	/**
	 * 判断是否支持 自动对焦
	 * @return
	 */
	public boolean supportFocusCamera()
	{
		if(null == camera)	return false;
		if(!isPreviewing)   return false;
		
		Parameters parameters = camera.getParameters();
		if(null == parameters || 0 >= parameters.getMaxNumFocusAreas())	
			return false;	
		
		return true;
	}
	
	/**
	 * 自动对焦 自动对焦可能子啊ui线程执行 也可能在非ui线程执行  注意如果改变ui的显示 需要注意
	 * @param focusPoint 对焦点坐标
	 * @param touchPoint 触摸点的坐标
	 */
	public void autoFocus(Point focusPoint, PointF touchPoint)
	{
		/**
		 * 不支持对焦 直接返回
		 */
		if(!supportFocusCamera())	return;
		
		/**
		 * 首先取消当前的对焦 如果存在的话
		 */
		cancelFocus();
		
		//得到相机参数
		Parameters parameters = camera.getParameters();
				
		//如果对焦点为null 直接在中心位置进行对焦
		if(null == focusPoint)
		{
			focusPoint = new Point(0, 0);
		}
		
		int left = focusPoint.x - FOCUS_AREA_SIZE / 2;
		int top  = focusPoint.y - FOCUS_AREA_SIZE / 2;
		
		left = MathUtil.clamp(left, -1000, 1000 - FOCUS_AREA_SIZE);
		top  = MathUtil.clamp(top , -1000, 1000 - FOCUS_AREA_SIZE);
		
		Rect focusRect = new Rect(left, top, left + FOCUS_AREA_SIZE, top + FOCUS_AREA_SIZE);
		List<Camera.Area> focusAreas = new ArrayList<Camera.Area>();
		focusAreas.add(new Camera.Area(focusRect, 1000));
		
		//设置对焦区域
		parameters.setFocusAreas(focusAreas);
		
		//设置相机参数
		camera.setParameters(parameters);
		
		//开始相机的对焦回调
		if(null != focusCameraCallback)
		{
			focusCameraCallback.startFocus(touchPoint);
		}
		
		//进行自动对焦
		camera.autoFocus(autoFacusCallback);
	}
	
	/**
	 * 取消对焦操作
	 */
	public void cancelFocus()
	{
		if(null != camera)
			camera.cancelAutoFocus();
		
		if(null != focusCameraCallback)
			focusCameraCallback.cancelFocus();
	}
	
	
	
	
	/**
	 * -------------------------------------------------------------------
	 * 切换摄像头相关
	 * ---------------------------------------------------------------------
	 */
	private SwitchCameraCallback switchCameraCallback = null;
	
	/**
	 * 设置摄像头的切换回调函数
	 * @param s
	 */
	public void setSwitchCameraCallback(SwitchCameraCallback s)
	{
		switchCameraCallback = s;
	}

	/**
	 * 是否支持摄像头的切换
	 * @return
	 */
	public boolean supportSwitchCamera()
	{
		int numberOfCameras = Camera.getNumberOfCameras();
		
		return 1 < numberOfCameras;
	}
	
	/**
	 * 切换摄像头
	 */
	public void switchCamera()
	{
		/**
		 * 不支持直接返回
		 */
		if(!supportSwitchCamera())	
			return;
		
		/**
		 * 切换摄像头回调 开始切换摄像头
		 */
		if(null != switchCameraCallback)
		{
			switchCameraCallback.startSwitch();
		}
				
		/**
		 * 切换摄像头
		 */
		int changeId = -1;
		int numberOfCameras = Camera.getNumberOfCameras();
		for(int i = 0; i < numberOfCameras; ++i)
		{
			if(i != cameraId)
			{
				changeId = i;
				break;
			}
		}
		
		/**
		 * 摄像头切换失败
		 */
		if(-1 == changeId)
		{
			/**
			 * 摄像头切换失败
			 */
			if(null != switchCameraCallback)
			{
				switchCameraCallback.failSwitch();
			}
			
			return;
		}
		
		/**
		 * 打开另一个摄像头
		 */
		openCamera(changeId);
		
		/**
		 * 摄像头切换成功
		 */
		if(null != switchCameraCallback)
		{
			switchCameraCallback.finishSwitch();
		}
	}

	/**
	 * ----------------------------------------------------------------
	 * 闪光灯相关的操作
	 * ----------------------------------------------------------------
	 */
	private SwitchFlashModeCallback switchFlashModeCallback = null;
	
	/**
	 * 设置切换闪光灯模式 回调
	 * @param s
	 */
	public void setSwitchFlashModeCallback(SwitchFlashModeCallback s)
	{
		switchFlashModeCallback = s;
	}
	
	
	
	/**
	 * 存储三种闪光灯 模式 打开 关闭 和 自动 
	 * 必须同时支持三种模式才表明 闪光可以开启
	 */
	private static final String[] FLASH_MODES = 
		{
			Parameters.FLASH_MODE_OFF, 
			Parameters.FLASH_MODE_AUTO, 
			Parameters.FLASH_MODE_ON
		};
	
	/**
	 * 是否支持闪光灯
	 * @return
	 */
	public boolean supportFlashMode()
	{
		if(null == camera)	
			return false;
		
		Parameters parameters = camera.getParameters();
		if (parameters == null)	
			return false;
		
		/**
		 * 得到flashmodes的信息
		 */
		List<String> flashModes = parameters.getSupportedFlashModes();
		if(null == flashModes || 0 == flashModes.size())	
			return false;
		
		for(int i = 0; i < FLASH_MODES.length; ++i)
		{
			if(!flashModes.contains(FLASH_MODES[i]))
				return false;
		}
		
		return true;
	}
	
	/**
	 * 得到当前的闪光灯模式
	 * @return
	 */
	public String getCurFlashMode()
	{
		if(!supportFlashMode())
			return null;
		
		return camera.getParameters().getFlashMode();
	}
	
	/**
	 * 切换当前的闪光灯模式 
	 * 如果不支持闪光灯 或者 相机未打开 直接返回null
	 * @return 返回闪光灯的模式
	 */
	public void switchFlashMode()
	{
		if(null == camera || !supportFlashMode())	return;
		
		/**
		 * 执行回调
		 */
		if(null != switchFlashModeCallback)
		{
			switchFlashModeCallback.startSwitch();
		}
		
		Parameters parameters = camera.getParameters();
		
		/**
		 * 得到当前的闪光灯模式
		 */
		String curMode = parameters.getFlashMode();
		String flashMode = null;
		
		/**
		 * 切换到下一个模式
		 */
		for(int i = 0; i < FLASH_MODES.length; ++i)
		{
			if(curMode.equals(FLASH_MODES[i]))
			{
				flashMode = FLASH_MODES[(i + 1) % FLASH_MODES.length];
				
				break;
			}
		}
		
		/**
		 * 切换失败
		 */
		if(null == flashMode)
		{
			if(null != switchFlashModeCallback)
			{
				switchFlashModeCallback.failSwitch();
			}
			
			return;
		}
		
		//切换闪光灯模式
		parameters.setFlashMode(flashMode);
		camera.setParameters(parameters);
		
		/**
		 * 成功回调
		 */
		if(null != switchFlashModeCallback)
		{
			switchFlashModeCallback.finishSwitch(flashMode);
		}
	}
	
	
	/**
	 * ---------------------------------------------------------
	 * 与预览相关的函数 
	 * ---------------------------------------------------------------------
	 */
	
	/**
	 * 设置预览 回调函数 和 缓冲区
	 * @param callback
	 * @param data
	 */
	public void setPreviewCallback(Camera.PreviewCallback callback, byte[] buffer)
	{
		if(null == camera)	return;
		
		camera.addCallbackBuffer(buffer);
		camera.setPreviewCallbackWithBuffer(callback);
	}
	
	/**
	 * ----------------------------------------------------------
	 * 得到摄像头的前后 
	 * ----------------------------------------------------------
	 */
	private static final int CAMERA_FACING_FRONT = 0;
	private static final int CAMERA_FACING_BACK = 1;
	private static final int CAMERA_FACING_ERROR = -1;
	
	/**
	 * 得到前后摄像头信息
	 * @return
	 */
	public int getCameraFacing()
	{
		if(null == camera || 0 > cameraId)
			return CAMERA_FACING_ERROR;

		CameraInfo cameraInfo = new CameraInfo();
		Camera.getCameraInfo(cameraId, cameraInfo);
		
		if(CameraInfo.CAMERA_FACING_FRONT == cameraInfo.facing)
			return CAMERA_FACING_FRONT;
		else if(CameraInfo.CAMERA_FACING_BACK == cameraInfo.facing)
			return CAMERA_FACING_BACK;
		else
			return CAMERA_FACING_ERROR;
	}
}





















