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

package com.deepcolor.deepred;

import java.util.HashMap;

import com.deepcolor.deepred.callback.CameraViewChangeCallback;
import com.deepcolor.deepred.callback.EncodeCallback;
import com.deepcolor.deepred.callback.FocusCameraCallback;
import com.deepcolor.deepred.callback.SwitchCameraCallback;
import com.deepcolor.deepred.callback.SwitchFlashModeCallback;
import com.deepcolor.deepred.sensor.LinearSensor;
import com.deepcolor.deepred.shot.CameraInstance;
import com.deepcolor.deepred.shot.ShotInstance;
import com.deepcolor.deepred.view.CameraView;
import com.deepcolor.deepred.view.FlashView;
import com.deepcolor.deepred.view.FocusView;
import com.deepcolor.deepred.view.LiveCheckBox;

import android.app.Activity;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;


public class MainActivity extends Activity 
{
	//摄像实例 包括录制视频 拍摄图片等操作
	private ShotInstance shotInstance = null;
	
	//加速计 判断是否自动对焦
	private LinearSensor linearSensor = null;
	
	private CameraView cameraView = null;	//摄像头预览控件
	private ImageView  baffleView = null;
	private ImageView  baffleBgView = null;
	
	private FocusView  focusView  = null;	//对焦的控件
	
	private LiveCheckBox liveCheckBox = null;	//live photo的开关
	private FlashView    flashView    = null;	//闪光灯切换按钮
	
	private ImageButton shotButton = null;	//拍摄按钮

    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        
        initAttributes();
        initAttributesParams();	//初始属性 和 控件的参数
        initViewsListener();	//初始化控件的事件监听
        initEventCallback();	//初始化各种回调和事件处理
    }
    
    /**
     * 初始化各种属性 包括各种控件的初始化
     */
    private void initAttributes()
    {
    	shotInstance = new ShotInstance();
    	linearSensor = new LinearSensor(this);
    	
    	cameraView   = (CameraView)  findViewById(R.id.camera_view);
    	baffleView   = (ImageView)   findViewById(R.id.baffle_view);
    	baffleBgView = (ImageView)   findViewById(R.id.baffle_bg_view);
    	focusView    = (FocusView)   findViewById(R.id.focus_view);
    	liveCheckBox = (LiveCheckBox)findViewById(R.id.live_photo_check_box);
    	flashView    = (FlashView)   findViewById(R.id.flash_view);
    	
    	shotButton = (ImageButton)findViewById(R.id.shot_button);
    }
    
    /**
     * 初始化控件的属性
     */
    private void initAttributesParams()
    {    	
    	/**
    	 * 初始化 cameraview需要的挡板
    	 */
    	cameraView.setBaffleView(baffleView, baffleBgView);
    	
    	/**
    	 * flash图标是否显示
    	 */
    	flashView.setVisibleByFlashMode();
    }
    
    /**
     * 初始化控件的监听操作
     */
    private void initViewsListener()
    {
    	/**
    	 * 是否打开livephoto的回调
    	 */
    	liveCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() 
    	{	
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) 
			{
				//是否打开livephoto
				shotInstance.setLivePhotoChecked(isChecked);
			}
		});

    	/**
    	 * 拍摄按钮点击
    	 */
    	shotButton.setOnClickListener(new View.OnClickListener() 
    	{	
			@Override
			public void onClick(View v) 
			{
				//进行图片的拍摄
				shotInstance.takePicture();
			}
		});
    }
    
    /**
     * 初始化各种事件的回调
     */
    private void initEventCallback()
    {
    	/**
    	 * 得到相机实例
    	 */
    	CameraInstance cameraInstance = CameraInstance.getInstance();
    	
    	/**
    	 * -----------------------------------------------------------------
    	 * 设置对焦的回调
    	 * -----------------------------------------------------------------
    	 */
    	cameraInstance.setFocusCallback(new FocusCameraCallback() 
    	{
			@Override
			public void startFocus(PointF touchPoint) 
			{
				/**
				 * 开始对焦 传入对焦的位置
				 */
				focusView.startFocus(touchPoint);
			}
			
			@Override
			public void finishFocus() 
			{
				/**
				 * 完成对焦
				 */
				focusView.finishFocus();
			}
			
			@Override
			public void cancelFocus() 
			{
				/**
				 * 取消对焦
				 */
				focusView.cancelFocus();
			}
		});
    	
    	/**
    	 * -----------------------------------------------------
    	 * 设置闪光灯切换模式
    	 * -----------------------------------------------------
    	 */
    	cameraInstance.setSwitchFlashModeCallback(new SwitchFlashModeCallback() 
    	{
			@Override
			public void startSwitch() 
			{
			}
			
			@Override
			public void finishSwitch(String mode) 
			{
				/**
				 * 切换完成
				 */
				flashView.finishSwitchFlashMode(mode);
			}
			
			@Override
			public void failSwitch() {				
			}
		});
    	
    	/**
    	 * ---------------------------------------------------------------
    	 * 切换摄像头回调
    	 * ------------------------------------------------------------
    	 */
    	cameraInstance.setSwitchCameraCallback(new SwitchCameraCallback() 
    	{
			@Override
			public void startSwitch() 
			{
				
			}
			
			@Override
			public void finishSwitch() 
			{
				/**
				 * 完成摄像头切换
				 */
				flashView.finishSwitchCamera();
				
				/**
				 * 重新设置 预览回调 得到预览大小等
				 */
				shotInstance.initCameraParams();
			}
			
			@Override
			public void failSwitch() 
			{

			}
		});
    	
    	/**
    	 * ------------------------------------------------------------
    	 * 窗口变化的时候回调
    	 * ------------------------------------------------------------
    	 */
    	cameraView.setCameraViewChangeCallback(new CameraViewChangeCallback() 
    	{	
			@Override
			public void onViewChanged(int width, int height) 
			{
				shotInstance.setViewRatio(1.0f * width / height);
			}
		});
    	
    	/**
    	 * ------------------------------------------------------------------------------------------------------------------------
    	 * 开始编码回调 当进行编码操作时 按钮进行旋转
    	 * ------------------------------------------------------------------------------------------------------------------------
    	 */
    	shotInstance.setEncodeCallback(new EncodeCallback() 
    	{
			@Override
			public void startEncode() 
			{
				liveCheckBox.startEncode();
			}
			
			@Override
			public void finishEncode() 
			{
				liveCheckBox.finishEncode();
			}
			
			@Override
			public void failEncode() 
			{
				liveCheckBox.failEncode();
			}
		});
    }
}

















