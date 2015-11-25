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

import com.deepcolor.deepred.util.ColorUtil;

import android.R.integer;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;

/**
 * 是否打开 live photo 功能开关
 * @author WSH
 *
 */
public class LiveCheckBox extends CheckBox
{
	private static final float START_ROTATE = 0f;	//旋转的开始和结束角度
	private static final float END_ROTATE   = 360f;
	private static final int ROTATE_DURATION = 1200;	//旋转动画时间
	
	
	//初始化一个旋转动画 当后台进行编码时 就执行旋转动画
	ObjectAnimator rorateAnimator = null;
	
	public LiveCheckBox(Context context) 
	{
		super(context);
		
		init();
	}

	public LiveCheckBox(Context context, AttributeSet attrs) 
	{
		super(context, attrs);
		
		init();
	}

	public LiveCheckBox(Context context, AttributeSet attrs, int defStyle) 
	{
		super(context, attrs, defStyle);
		
		init();
	}
	
	private void init()
	{
		rorateAnimator = ObjectAnimator.ofFloat(this, "rotation", START_ROTATE, END_ROTATE); 
		rorateAnimator.setDuration(ROTATE_DURATION);
		rorateAnimator.setRepeatMode(ValueAnimator.INFINITE);	//设为无限重复的旋转
		rorateAnimator.setRepeatCount(-1);
	}
	
	/**
	 * ------------------------------------------
	 * 用一个标志为表示当前正在编码的的livephoto的个数
	 */
	private int encodingNum = 0;
	
	/**
	 * 开始编码
	 */
	public void startEncode()
	{
		handler.sendEmptyMessage(START_ENCODE_MSG);
	}
	
	/**
	 * 完成编码
	 */
	public void finishEncode()
	{
		handler.sendEmptyMessage(FINISH_ENCODE_MSG);
	}
	
	/**
	 * 编码失败
	 */
	public void failEncode()
	{
		handler.sendEmptyMessage(FAIL_ENCODE_MSG);
	}
	
	/**
	 * ----------------------------------------------------------------------------------
	 * 编码的回调不是在ui线程中 需要使用handle处理
	 * --------------------------------------------------------------------------------------
	 */
	private static final int START_ENCODE_MSG     = 0;
	private static final int FINISH_ENCODE_MSG    = 1;
	private static final int FAIL_ENCODE_MSG 	  = 2;
	
	
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
			case START_ENCODE_MSG:
				//有一个livephoto开始编码
				encodingNum++;
				
				/**
				 * 如果当前没有开始 或者 暂停 直接开始动画
				 */
				if(!rorateAnimator.isStarted() || !rorateAnimator.isRunning())
					rorateAnimator.start();
				
				break;
			case FINISH_ENCODE_MSG:	//一个已经完成编码
			case FAIL_ENCODE_MSG:	//编码失败
				
				encodingNum--;
				if(0 > encodingNum)
					encodingNum = 0;
		
				if(0 == encodingNum)
					rorateAnimator.end();
				
				break;
			}
		}
	};
}













