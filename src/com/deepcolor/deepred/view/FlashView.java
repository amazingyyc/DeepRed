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

package com.deepcolor.deepred.view;

import java.util.HashMap;

import com.deepcolor.deepred.R;
import com.deepcolor.deepred.shot.CameraInstance;

import android.content.Context;
import android.hardware.Camera.Parameters;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

/**
 * 显示闪光灯的状态 包括三个状态 打开 自动 和 关闭 但是有的摄像头不支持
 * @author WSH
 *
 */
public class FlashView extends ImageView
{
	/**
	 * 存储flash对应的模式 和 资源id
	 */
	private static HashMap<String, Integer> FLASHMODES = new HashMap<String, Integer>();
	static
	{
		FLASHMODES.put(Parameters.FLASH_MODE_OFF, R.drawable.flash_off);
		FLASHMODES.put(Parameters.FLASH_MODE_AUTO, R.drawable.flash_auto);
		FLASHMODES.put(Parameters.FLASH_MODE_ON, R.drawable.flash_on);
	}
	
	public FlashView(Context context) 
	{
		super(context);
	}

	public FlashView(Context context, AttributeSet attrs) 
	{
		super(context, attrs);
	}

	public FlashView(Context context, AttributeSet attrs, int defStyle) 
	{
		super(context, attrs, defStyle);
	}
	
	/**
	 * 根据闪光灯模式 设置是否可见
	 */
	public void setVisibleByFlashMode()
	{
		if(CameraInstance.getInstance().supportFlashMode())
		{
			this.setVisibility(View.VISIBLE);
			
			String flashMode = CameraInstance.getInstance().getCurFlashMode();
			
			if(null != flashMode && FLASHMODES.containsKey(flashMode))
			{
				this.setImageResource(FLASHMODES.get(flashMode));
			}
			else
			{
				this.setVisibility(View.INVISIBLE);
			}
		}
		else
			this.setVisibility(View.INVISIBLE);
	}
	
	/**
	 * 当闪光灯 完成切换时候调用 有可能不是在ui线程中切换的
	 */
	public void finishSwitchFlashMode(String flashMode)
	{
		Message msg = new Message();
		msg.what = FINISH_SWITCH_FLAH_MODE_MSG;
		msg.obj  = flashMode;
		
		handler.sendMessage(msg);
	}
	
	/**
	 * 当完成摄像头的时候调用 有可能不再ui线程中调用
	 */
	public void finishSwitchCamera()
	{
		handler.sendEmptyMessage(FINISH_SWITCH_CAMERA_MSG);
	}
	
	/**
	 * -----------------------------------------------------------------
	 */
	private static final int FINISH_SWITCH_FLAH_MODE_MSG = 0;	//完成闪光灯模式切换消息
	private static final int FINISH_SWITCH_CAMERA_MSG = 1;	//完成摄像头切换消息
	
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
			case FINISH_SWITCH_FLAH_MODE_MSG:
				/**
				 * 切换闪光灯
				 */
				String flashMode = (String)msg.obj;
				
				if(null != flashMode && FLASHMODES.containsKey(flashMode))
				{
					FlashView.this.setImageResource(FLASHMODES.get(flashMode));
					FlashView.this.setVisibility(View.VISIBLE);
				}
				else
				{
					FlashView.this.setVisibility(View.INVISIBLE);
				}
				
		        break;
		 
			case FINISH_SWITCH_CAMERA_MSG:
				/**
				 * 是否可见
				 */
				setVisibleByFlashMode();
				
				break;
			}
		}
	};

	
	/**
	 * ---------------------------------------------
	 * 相应触摸
	 */
	
	@Override
	public boolean onTouchEvent(MotionEvent event) 
	{
		if(MotionEvent.ACTION_DOWN == event.getAction())
		{
			/**
			 * 切换闪光灯模式
			 */
			CameraInstance.getInstance().switchFlashMode();
		}
		return super.onTouchEvent(event);
	}
	
	
	
	
}


















