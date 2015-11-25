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

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;

/**
 * 焦点控件 主要用于在相机对焦的时候 显示焦点
 * @author WSH
 */
public class FocusView extends ImageView
{
	private static final float START_SCALE  = 1.0f;	//缩放参数
	private static final float END_SCALE    = 0.8f;	//缩放参数
	private static final int SCALE_DURATION = 150;	//缩放动画时间
	private static final int COLOR_DURATION = 200;	//改变颜色的时间
	
	//组合动画 用于控件的缩放
	AnimatorSet animSet = new AnimatorSet(); 	

	public FocusView(Context context, AttributeSet attrs, int defStyle) 
	{
		super(context, attrs, defStyle);
		
		init();
	}

	public FocusView(Context context, AttributeSet attrs) 
	{
		super(context, attrs);
		
		init();
	}

	public FocusView(Context context) 
	{
		super(context);
		
		init();
	}
	
	/**
	 * 初始化属性
	 */
	private void init()
	{
		//x y方向上的缩放
		ObjectAnimator xScaleAnimator = ObjectAnimator.ofFloat(this, "scaleX", START_SCALE, END_SCALE);
		ObjectAnimator yScaleAnimator = ObjectAnimator.ofFloat(this, "scaleY", START_SCALE, END_SCALE);
		
		//组合动画
		animSet = new AnimatorSet();
		animSet.play(xScaleAnimator).with(yScaleAnimator);
		animSet.setDuration(SCALE_DURATION);
	}

	/**
	 * 开始对焦函数 开始对焦后将 控件置于相应的位置 并且开始缩放动画
	 * 1:设置控件可见
	 * 2：设置相应的位置 
	 * 3：开始动画
	 * 
	 * --------------------------------------------
	 * focusview和cameraview在同一个布局下 因此 传入的坐标点是相对于cameraview父控件的坐标 
	 * 也是相当于focusview父控件的坐标
	 */
	public void startFocus(PointF touchPoint)
	{
		//封装一个消息
		Message msg = new Message();
		msg.what = START_FOCUS_MSG;
		msg.obj  = touchPoint;
		
		//删除消失消息
		handler.removeMessages(FINISH_FOCUS_MSG);
		handler.removeMessages(DISAPPEAR_MSG);
		
		//发送消息
		handler.sendMessage(msg);
	}
	
	/**
	 * 完成对焦之后的调用
	 * 完成对焦之后 将控件变为设定的颜色 然后一段时间后消失
	 */
	public void finishFocus()
	{
		//发送完成对焦的消息 改变图标的颜色
		handler.sendEmptyMessage(FINISH_FOCUS_MSG);

		//发送一个延迟消息 将控件不可见
		handler.sendEmptyMessageDelayed(DISAPPEAR_MSG, COLOR_DURATION);
	}
	
	/**
	 * 结束对焦操作 当切换摄像头的时候需要直接结束当前的对焦操作
	 */
	public void cancelFocus()
	{
		/**
		 * 删除消息队列中的所有消息
		 */
		handler.removeMessages(START_FOCUS_MSG);
		handler.removeMessages(FINISH_FOCUS_MSG);
		handler.removeMessages(DISAPPEAR_MSG);
		
		/**
		 * 发送取消对焦消息
		 */
		handler.sendEmptyMessage(CANCEL_FOCUS_MSG);
	}

	/**
	 * 私有的处理开始对焦的回调函数 在ui线程中执行
	 * @param touchPoint
	 */
	private void _startFocus(PointF touchPoint)
	{
		if(null == touchPoint)
		{
			//如果传入的触摸点为null 直接设为屏幕的中间位置
			Point screenSize = new Point();
			WindowManager wm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
			wm.getDefaultDisplay().getSize(screenSize);
			
			touchPoint = new PointF(screenSize.x / 2, screenSize.y / 2);
		}
		
		//设置当前控件的位置
		RelativeLayout.LayoutParams layout = (RelativeLayout.LayoutParams)this.getLayoutParams();
		layout.leftMargin = (int) (touchPoint.x - this.getWidth()  / 2);
		layout.topMargin  = (int) (touchPoint.y - this.getHeight() / 2);
		this.setLayoutParams(layout);
		
		this.setVisibility(View.VISIBLE);	//设置当前控件可见
		this.clearColorFilter();	//清除颜色过滤
		
		//开始缩放动画
		animSet.start();
	}
	
	
	/**
	 * ---------------------------------------------------------------------------
	 * 负责相应 对焦的各种操作 由于对焦操作可能不在ui线程中 因此需要 使用handler进行操作
	 * ----------------------------------------------------------------------------
	 */
	private static final int START_FOCUS_MSG     = 0;
	private static final int FINISH_FOCUS_MSG    = 1;
	private static final int CANCEL_FOCUS_MSG 	 = 2;
	private static final int DISAPPEAR_MSG       = 3;
	
	
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
			case START_FOCUS_MSG:
				//开始对焦操作
				_startFocus((PointF)msg.obj);
				
				break;
				
			case FINISH_FOCUS_MSG:
				
				animSet.end();	//结束动画
				FocusView.this.setColorFilter(ColorUtil.FOCUS_COLOR);	//改变图标的颜色
				
				break;
				
			case DISAPPEAR_MSG:
				//将控件消失
				FocusView.this.clearColorFilter();	//清除颜色过滤
				FocusView.this.setVisibility(View.GONE);	//控件不可见
				
				break;
			case CANCEL_FOCUS_MSG:
				/**
				 * 停止对焦操作
				 */
				
				animSet.cancel();	//取消动画
			
				FocusView.this.clearColorFilter();	//清除颜色过滤
				FocusView.this.setVisibility(View.GONE);	//控件不可见
				
				break;
			}
		}
	};
}
















