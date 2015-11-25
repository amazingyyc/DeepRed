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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import com.deepcolor.deepred.R;
import com.deepcolor.deepred.callback.CameraViewChangeCallback;
import com.deepcolor.deepred.callback.FocusCameraCallback;
import com.deepcolor.deepred.shot.CameraInstance;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.Renderer;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

/**
 * 继承glsurfaceview实现自己的view用于摄像头的预览
 * @author WSH
 */

/**
 * 当图片预览的时候 图片预览的大小和屏幕大小的比例不相同 因此需要对预览图片进行裁剪
 * 
 * 裁剪的原则是 当图片经过旋转正常显示的左上角 开始裁剪
 * @author WSH
 *
 */
public class CameraView extends GLSurfaceView implements Renderer, OnFrameAvailableListener
{
	int textureId = -1;	//纹理id
	
	private SurfaceTexture surfaceTexture = null;	//纹理贴图
	private TextureDraw    textureDraw    = null;	//用于绘制纹理
	
	private float[] mvpMatrix     = new float[16];	//坐标变换 包括 视角坐标 投影坐标等
	private float[] textureMatrix = new float[16];	//存储纹理坐标 每次SurfaceTexture产生的纹理 都有一个坐标变换
	
	private CameraViewChangeCallback cameraViewChangeCallback = null;	//窗口变换时调用

	public CameraView(Context context, AttributeSet attrs) 
	{
		super(context, attrs);
		
		/**
		 * 初始化属性
		 */
		init();
	}
	
	/**
	 * 初始化属性
	 */
	private void init()
	{
		//opengles 2.0
		setEGLContextClientVersion(2);
		setRenderer(this);
		setRenderMode(RENDERMODE_WHEN_DIRTY);	//
		
		/**
		 * 和手势相关的属性
		 */
		detector = new GestureDetector(getContext(), new GestureListener());
		
		/**
         * 初始化renderscript
         */
        renderScript = RenderScript.create(getContext());
        blurScript   = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
	}

	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) 
	{
		//纹理id
		textureId = createTextureId();	
		
		//传入一个纹理id 生成一张纹理
		surfaceTexture = new SurfaceTexture(textureId);
		surfaceTexture.setOnFrameAvailableListener(this);
		
		//初始化绘制纹理类
		textureDraw = new TextureDraw(textureId);
	}

	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) 
	{
		GLES20.glViewport(0, 0, width, height);
		
		float ratio = 1.0f * width / height;	//控件大小的宽高比例 
		screenWidth  = width;
		screenHeight = height;
		
		//视角坐标的坐标值
		float cx = 0, cy = 0, cz = 3;
		float tx = 0, ty = 0, tz = 0;
		float ux = 0, uy = 1, uz = 0;
		
		//投影坐标的坐标值
		float left   = -1, right = 1;
		float bottom = -1.0f / ratio, top = 1.0f / ratio;
		float near   = 1, far = 10;
		
		float[] viewMatrix = new float[16];	//视角坐标
		float[] projMatrix = new float[16];	//投影坐标
		
		Matrix.setLookAtM(
				viewMatrix,
				0,
				cx, cy, cz,
				tx, ty, tz,
				ux, uy, uz);

		Matrix.orthoM(
				projMatrix,
				0,
				left, right,
				bottom, top,
				near, far);

		//视角坐标系和投影坐标系 组成的系统坐标系
		Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0);
		
		//========================================================================
		//初始化texturedraw需要的参数
		textureDraw.setRatio(ratio);	//设置控件大小比例
		startPreview();	//开始预览画面
		
		/**
		 * --------------------------------------------------
		 * 窗口变化回调
		 */
		if(null != cameraViewChangeCallback)
			cameraViewChangeCallback.onViewChanged(width, height);
	}

	@Override
	public void onDrawFrame(GL10 gl)
	{
		GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

		//更新预览图片 并 得到变换矩阵
		surfaceTexture.updateTexImage();
		surfaceTexture.getTransformMatrix(textureMatrix);
		
		//绘制预览图片
		textureDraw.onDraw(mvpMatrix, textureMatrix);
		
		/**
		 * 将glsurfaceive中需要绘制的资源生成一张bitmap的图片 用来进行高斯模糊
		 */
		blurringBimtap();
	}

	/**
	 * 当surfacetext有新的数据的时候调用
	 */
	@Override
	public void onFrameAvailable(SurfaceTexture surfaceTexture)
	{
		//当有新的摄像头数据时  刷新界面
		this.requestRender();
	}
	
	/**
	 * 设置窗口变化回调
	 * @param c
	 */
	public void setCameraViewChangeCallback(CameraViewChangeCallback c)
	{
		cameraViewChangeCallback = c;
	}
	/**
	 * 创建一张纹理
	 * @return
	 */
	private int createTextureId()
	{
		int[] texture = new int[1];

		GLES20.glGenTextures(1, texture, 0);

		GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]);

		GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
		GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

		GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

		return texture[0];
	}
	
	/**
	 * 开始预览
	 */
	public void startPreview()
	{
		//得到摄像头实例
		CameraInstance cameraInstance = CameraInstance.getInstance();
		
		//得到摄像头的预览大小
		Camera.Size previewSize = cameraInstance.getPreviewSize();
		
		//设置预览大小
		textureDraw.setPreviewSize(previewSize.width, previewSize.height);
		
		//开始预览
		cameraInstance.startPreview(surfaceTexture);
		
		//进行对焦
		cameraInstance.autoFocus(null, null);
	}

	/**
	 * --------------------------------------------------------------------------------
	 * 触摸相关的函数 相应 相关的触摸函数 包括 单击 双击 左划右划 等操作 
	 * -------------------------------------------------------------------------------
	 */
	
	private static final int FLING_MIN_DISTANCE   = 15;	//手指滑动的最大间距 
	private static final float FLING_MIN_VELOCITY = 300;	//手指滑动的最大速度
	
	/**
	 * 手势识别类 用于识别各种手势
	 */
	private GestureDetector detector = null;
	
	/**
	 * 触摸回调
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event) 
	{
		//手势识别
		return detector.onTouchEvent(event);
	}
	
	class GestureListener extends SimpleOnGestureListener
	{
		/**
		 * e1第一次触摸点击时的位置
		 * e2在当前事件中触摸的位置
		 * velocityX velocityY 滑动的速度 每秒的像素个数
		 */
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) 
		{
			//滑动的方向必须是左右滑动
			if(Math.abs(e1.getX() - e2.getX()) > Math.abs(e1.getY() - e2.getY()))
			{
				//滑动的距离和速度符合要求时 产生滑动事件
				if(Math.abs(e1.getX() - e2.getX()) >= FLING_MIN_DISTANCE && Math.abs(velocityX) >= FLING_MIN_VELOCITY)
				{
					/**
					 * 左右滑动 切换摄像头
					 */
					switchCamera();
					
					return true;
				}
			}
			
			return false;
		}
				
		@Override
		public boolean onSingleTapUp(MotionEvent e) 
		{
			/**
			 * 单击进行 自动对焦
			 */
			autoFocus(e.getX(), e.getY());
			
			return true;
		}

		@Override
		public boolean onDown(MotionEvent e) 
		{
			return true;
		}
	}
	
	/**
	 * 触摸的时候进行相机的对焦操作
	 * @param x	触摸点
	 * @param y
	 */
	private void autoFocus(float x, float y)
	{
		//预览图片的大小
		CameraInstance cameraInstance = CameraInstance.getInstance();
		
		/**
		 * 不支持对焦 直接返回
		 */
		if(!cameraInstance.supportFocusCamera())
			return;
		
		
		Camera.Size previewSize = cameraInstance.getPreviewSize();
		int previewWidth  = previewSize.width;	//预览图片的大小
		int previewHeight = previewSize.height;
		
		int viewWidth  = this.getWidth();
		int viewHeight = this.getHeight();
		
		int focusX;	//对焦的坐标
		int focusY;
		
		/**
		 * -------------------------------------------------------
		 * 当前的预览画面是旋转过90度的
		 * -------------------------------------------------------
		 */
		//根据比例 判断触摸点的位置
		if(1.0f * previewWidth / previewHeight > 1.0f * viewHeight / viewWidth)
		{
			/*float l = (1.0f * previewWidth / previewHeight * viewWidth - 1.0f * viewHeight) / 2.0f;
			
			focusX = Float.valueOf((l + y) / (2.0f * l + viewHeight)*2000 - 1000).intValue();
			focusY = Float.valueOf(1000 - 1.0f * x / viewWidth*2000).intValue();*/
			
			float l = 1.0f * previewWidth / previewHeight * viewWidth - 1.0f * viewHeight;
			
			focusX = Float.valueOf(y / (l + viewHeight) * 2000 - 1000).intValue();
			focusY = Float.valueOf(1000 - 1.0f * x / viewWidth * 2000).intValue();
		}
		else
		{
			/*float l = (1.0f * previewHeight / previewWidth * viewHeight - 1.0f * viewWidth) / 2.0f;
			
			focusX = Float.valueOf(1.0f * y / viewHeight * 2000 - 1000).intValue();
			focusY = Float.valueOf(1000 - (l + x) / (2.0f * l + viewWidth)*2000).intValue();*/
			float l = 1.0f * previewHeight / previewWidth * viewHeight - 1.0f * viewWidth;
			
			focusX = Float.valueOf(1.0f * y / viewHeight * 2000 - 1000).intValue();
			focusY = Float.valueOf(1000 - x / (l + viewWidth)*2000).intValue();
		}
		
		float touchX = this.getX() + x;	//根据当前控件的坐标和触摸位置 得到触摸点相对于父控件的坐标
		float touchY = this.getY() + y;
		
		//进行对焦操作
		cameraInstance.autoFocus(new Point(focusX, focusY), new PointF(touchX, touchY));
	}

	
	
	/**
	 * ------------------------------------------------------------------------------
	 * 左右滑动切换前后摄像头的时候进行处理
	 * --------------------------------------------------------------------------------
	 */
	
	/**
	 * 透明度动画的透明度
	 */
	private static final float START_ALPHA  = 0.4f;
	private static final float END_ALPHA    = 1.0f;
	private static final int ALPHA_DURATION = 450;	//动画时间
	
	private static final int DOWNSAMPLE_FACTOR = 10;	//图片的缩放倍数
	
	/**
	 * opengl屏幕的大小
	 */
	private int screenWidth;
	private int screenHeight;
	
	/**
	 * 挡板view 默认不显示 当切换摄像头的时候显示一张模糊的照片然后 动画处理
	 */
	ImageView baffleView = null;
	ImageView baffleBgView = null;
	ObjectAnimator baffleAnimator = null;	//透明动画 当切换摄像头的时进行透明度的变化
	
	/**
	 * 两张图片 mBitmapToBlur需要模糊的图片 
	 * mBlurredBitmap 模糊过的图片
	 * 
	 * originBitmap是原始的未压缩的图片
	 */
	private Bitmap originBitmap;
	private Bitmap bitmapToBlur;
	private Bitmap blurredBitmap;
	
	/**
	 * 是否将屏幕保存为一张模糊的图片
	 */
	private boolean blurSurfaceToBitmap = false;
	
	private boolean isBlurring  = false;	//是否正在进行图片的模糊
	private boolean isSwitching = false;	//是否正在进行摄像头的切换
    
    /**
     * 高斯模糊需要的属性
     */
    private RenderScript renderScript;	//使用renderscript用来高斯模糊
    private ScriptIntrinsicBlur blurScript;
    private Allocation blurInput, blurOutput;	//renderscript的输入和输出
    
    /**
     * 初始化挡板控件 包括前景和背景
     * 初始化动画
     * @param context
     */
    public void setBaffleView(ImageView bView, ImageView bBgView)
    {
    	baffleView   = bView;
    	baffleBgView = bBgView;
    	
    	/**
		 * 初始化动画
		 */
		baffleAnimator = ObjectAnimator.ofFloat(baffleView, "alpha", START_ALPHA, END_ALPHA);
		baffleAnimator.setDuration(ALPHA_DURATION);
		
		baffleAnimator.addListener(new AnimatorListener() {
			
			@Override
			public void onAnimationStart(Animator animation) {
			}
			
			@Override
			public void onAnimationRepeat(Animator animation) {
			}
			
			@Override
			public void onAnimationEnd(Animator animation) 
			{
				isBlurring = false;
				
				/**
				 * 如果摄像头切换 和 动画都已经完成直接将挡板 设为不可见
				 */
				if(!isSwitching)
				{
					baffleView.setVisibility(View.GONE);
					baffleBgView.setVisibility(View.GONE);
				}
			}
			
			@Override
			public void onAnimationCancel(Animator animation) {
			}
		});
    }
	
	
	/**
	 * 从glsurfaceview中得到一张图片然后进行高斯模糊
	 * 将模糊好的图片存储 用于显示
	 */
	private void blurringBimtap()
	{
		if(!blurSurfaceToBitmap)	
			return;
	    
		blurSurfaceToBitmap = false;
		
		/**
		 * 存储原始的像素数据
		 */
		int bitmapBuffer[] = new int[screenWidth * screenHeight];
	    int bitmapSource[] = new int[screenWidth * screenHeight];
		
	    IntBuffer intBuffer = IntBuffer.wrap(bitmapBuffer);
	    intBuffer.position(0);

	    /**
	     * 读取opengl的像素数据
	     */
	    GLES20.glReadPixels(0, 0, screenWidth, screenHeight, GL10.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, intBuffer);
	    
	    /**
	     * 将图片进行缩放处理 存储到数组中
	     */
		int offset1, offset2;
	    for(int i = 0; i < screenHeight; ++i)
	    {
	    	offset1 = i  * screenWidth;
	    	offset2 = (screenHeight - i - 1) * screenWidth;
	    	
	    	for(int j = 0; j < screenWidth; ++j)
	    	{
	    		int texturePixel = bitmapBuffer[offset1 + j];
                int blue = (texturePixel >> 16) & 0xff;
                int red  = (texturePixel << 16) & 0x00ff0000;
                int pixel = (texturePixel & 0xff00ff00) | red | blue;
                
                bitmapSource[offset2 + j] = pixel;
	    	}
	    }
	    
	    /**
	     * 将数组存储成一张原始的图片
	     */
	    if(null != originBitmap)
	    {
	    	originBitmap.recycle();
	    	originBitmap = null;
	    }
	    originBitmap = Bitmap.createBitmap(bitmapSource, screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
	    
	    /**
	     * 将一张图片进行缩放
	     */
	    android.graphics.Matrix matrix = new android.graphics.Matrix(); 
	    matrix.postScale(1f / DOWNSAMPLE_FACTOR, 1f / DOWNSAMPLE_FACTOR); //长和宽放大缩小的比例
	    
	    if(null != bitmapToBlur)
        {
        	bitmapToBlur.recycle();
        	bitmapToBlur = null;
        }
        bitmapToBlur = Bitmap.createBitmap(originBitmap, 0, 0, originBitmap.getWidth(), originBitmap.getHeight(), matrix, false);
        
        /**
         * 创建输出的高斯模糊后的图片
         */
        if(null != blurredBitmap)
        {
        	blurredBitmap.recycle();
        	blurredBitmap = null;
        }
        blurredBitmap = Bitmap.createBitmap(bitmapToBlur.getWidth(), bitmapToBlur.getHeight(), Bitmap.Config.ARGB_8888);
		
        /**
         * render的输入和输出
         */
        blurInput  = Allocation.createFromBitmap(renderScript, bitmapToBlur, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        blurOutput = Allocation.createTyped(renderScript, blurInput.getType());
        
        /**
		 * 将图片进行模糊 然后 复制到blurredBitmap中
		 */
		blurInput.copyFrom(bitmapToBlur);
        blurScript.setInput(blurInput);
        blurScript.forEach(blurOutput);
        blurOutput.copyTo(blurredBitmap);

		/**
		 * 发送完成消息
		 */
		handler.sendEmptyMessage(FINISH_BLUR_MSG);
	}
	
	/**
	 * 切换前后摄像头
	 */
	private void switchCamera()
	{
		/**
		 * 如果当前正在切换摄像头 直接返回
		 */
		if(isBlurring || isSwitching)
			return;
		
		CameraInstance cameraInstance = CameraInstance.getInstance();
		
		//不支持切换直接返回
		if(!cameraInstance.supportSwitchCamera())	
			return;
		
		/**
		 * 取消对焦
		 */
		cameraInstance.cancelFocus();
		
		/**
		 * 得到一张模糊的图片
		 */
		blurSurfaceToBitmap = true;
		isBlurring  = true;	//标志位 表示当前正在模糊屏幕并存储到一张图片 然后进行动画操作
		isSwitching = true;	//标志位 表示正在切换摄像头
		
		/**
		 * 请求刷新界面 通知 生成一张模糊和未模糊的壁纸
		 */
		this.requestRender();
		
        /**
         * 异步的切换摄像头
         */
		new SwitchAsyncTask().execute(null, null);
	}
	
	/**
	 * --------------------------------------------------------------------
	 * opengl的线程和ui不是同一个线程 需要异步处理
	 * ----------------------------------------------------------------------
	 */
	private static final int FINISH_BLUR_MSG = 0;	//完成高斯模糊
	
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
			case FINISH_BLUR_MSG:
				baffleBgView.setImageBitmap(bitmapToBlur);	//未模糊的图片
		        baffleBgView.setVisibility(View.VISIBLE);
				
				baffleView.setImageBitmap(blurredBitmap);	//设置图片
		        baffleView.setVisibility(View.VISIBLE);	//可见

		        baffleAnimator.start();	//开始透明度的变化

		        break;
			}
		}
	};
	
	/**
	 * 异步的开启摄像头
	 */
	class SwitchAsyncTask extends AsyncTask<Integer, Integer, String>
	{
		/**  
	     * 这里的String参数对应AsyncTask中的第三个参数（也就是接收doInBackground的返回值）  
	     * 在doInBackground方法执行结束之后在运行，并且运行在UI线程当中 可以对UI空间进行设置  
	     */  
	    @Override  
	    protected void onPostExecute(String result) 
	    {
			/**
			 * 重置标志位
			 */
	    	isSwitching = false;
	    	
	    	/**
	    	 * 切换摄像头完成后 将挡板置于不可见
	    	 */
	    	if(!isBlurring)
	    	{
	    		baffleView.setVisibility(View.GONE);
	    		baffleBgView.setVisibility(View.GONE);
	    	}
	    }

		@Override
		protected String doInBackground(Integer... params) 
		{			
			/**
			 * 切换摄像头
			 */
			CameraInstance cameraInstance = CameraInstance.getInstance();
			cameraInstance.switchCamera();	//切换摄像头
			
			/**
			 * 切换完成后开始预览
			 */
			startPreview();
			
			return null;
		}
	}
	
}


/**
 * -----------------------------------------------------------------------------------------------
 * -------------------------------------------------------------------------------------------------------
 */
/**
 * 绘制函数 根据传入的textureid 和 坐标等信息 绘制 摄像头的预览画面
 * @author WSH
 *
 */
class TextureDraw
{
	//顶点着色器
	private final String vertexShaderCode =
			//定义两个变量 vPosition inputTextureCoordinate
			"uniform mat4 uMVPMatrix;" + 	//总的变换矩阵
			"uniform mat4 uTexMatrix;" +	//纹理变换矩阵
			"uniform float uAlpha;" + 
			
            "attribute vec4 aPosition;" +			//位置坐标
            "attribute vec4 aTextureCoordinate;" +	//纹理坐标
            
            "varying vec2 vTextureCoordinate;" +	//传入到片源着色器的纹理坐标
            "varying float vAlpha;" + 
            
            "void main()" +
            "{"+
                "gl_Position = uMVPMatrix * aPosition;"+	//位置坐标
                "vTextureCoordinate = (uTexMatrix * aTextureCoordinate).xy;" +	//纹理坐标
                //"vTextureCoordinate = aTextureCoordinate.xy;" + 
                "vAlpha = uAlpha;" +	//透明度
            "}";
	
	//片源着色器代码
	private final String fragmentShaderCode =
            "#extension GL_OES_EGL_image_external : require\n"+
            "precision mediump float;" +
            		
            //接收从顶点着色器过来的纹理坐标
            "varying vec2 vTextureCoordinate;" +
            "varying float vAlpha;" + 
            
            "uniform samplerExternalOES s_texture;" +
            
            "void main() {" +
            	"gl_FragColor = texture2D(s_texture, vTextureCoordinate);" +
            	"gl_FragColor.a = vAlpha;" + 
            "}";
	
	//=====================================================================
	private FloatBuffer vertexVerticesBuffer;	//顶点坐标
	private FloatBuffer textureVerticesBuffer;	//纹理坐标
    private ShortBuffer drawListBuffer;	//绘制顺序
    
    private int program;				//shader程序id
    
    private int mvpMatrixHandle;			//变换矩阵句柄
    private int textureMatrixHandle;		//纹理变换矩阵
    private int alphaHandle;			//透明度句柄
    
    private int positionHandle;		//顶点位置 句柄
    private int textureCoordHandle;	//纹理句柄
    
    
    //顶点绘制顺序
    private short drawOrder[] = {0, 1, 2, 0, 2, 3};
    
    //纹理id
  	private int textureId = -1;
  	
  	//每个点 的一组的数的个数(x, y)两个点
    private static final int COORDS_PER_VERTEX = 2;
    private final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex
    
    private static final float alpha = 1.0f;	//透明度 暂时无作用
    
    //opengl窗口的宽和高比例
    private float ratio = -1;
    private int previewWidth  = -1;	//摄像头的预览图片大小
    private int previewHeight = -1;
    
    /*
     * 传入纹理坐标
     */
    public TextureDraw(int textureId)
    {
    	this.textureId = textureId;
    	
    	//初始化属性
    	init();
    }
    
    //初始化参数等信息 包括 shader的初始化
    private void init()
    {
    	//绘制顺序
        ByteBuffer orderBB = ByteBuffer.allocateDirect(drawOrder.length * 2);
        orderBB.order(ByteOrder.nativeOrder());
        drawListBuffer = orderBB.asShortBuffer();
        drawListBuffer.put(drawOrder);
        drawListBuffer.position(0);
        
        //+++++++++++++++++++++++++++++++++++++++++++++++++++++
        //加载shader
        int vertexShader    = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader  = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);
        
        //glCreaterProgram()创建一个（着色）程序对象；
        program = GLES20.glCreateProgram();
        
        //glAttachShader()分别将顶点着色器对象和片段着色器对象附加到（着色）程序对象上；
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        
        //glLinkProgram()对（着色）程序对象执行链接操作
        GLES20.glLinkProgram(program);
        
        //得到各种参数的句柄
        mvpMatrixHandle        = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        textureMatrixHandle    = GLES20.glGetUniformLocation(program, "uTexMatrix");
        alphaHandle            = GLES20.glGetUniformLocation (program, "uAlpha");
        
        positionHandle     = GLES20.glGetAttribLocation (program, "aPosition");
        textureCoordHandle = GLES20.glGetAttribLocation (program, "aTextureCoordinate");
    }
    
    /**
     * 加载shader代码
     * @param type
     * @param shaderCode
     */
    private int loadShader(int type, String shaderCode)
    {
    	//glCreateshader()分别创建一个顶点着色器对象和一个片段着色器对象；
        int shader = GLES20.glCreateShader(type);

        //glShaderSource()分别将顶点着色程序的源代码字符数组绑定到顶点着色器对象，
        //将片段着色程序的源代码字符数组绑定到片段着色器对象；
        GLES20.glShaderSource(shader, shaderCode);
        
        //glCompileShader()分别编译顶点着色器对象和片段着色器对象；
        GLES20.glCompileShader(shader);

        return shader;
    }
    
    /**
     * 设置opengl控件大宽高比例 从而设置顶点坐标
     */
    public void setRatio(float r)
    {
    	//窗口控件的宽与高的比例
    	if(ratio != r)
    	{
    		ratio = r;
        	float inverseRatio = 1.0f / ratio;
        	
        	float vertexCoords[] = 
    	    	{
    	         
    	         -1.0f, -inverseRatio,
                  1.0f, -inverseRatio,
                 -1.0f,  inverseRatio,
                  1.0f,  inverseRatio,
    	    };
        	
        	//纹理顶点坐标 vertexVerticesBuffer
    		ByteBuffer vertexBB = ByteBuffer.allocateDirect(vertexCoords.length * 4);
    		vertexBB.order(ByteOrder.nativeOrder());
    		vertexVerticesBuffer = vertexBB.asFloatBuffer();
    		vertexVerticesBuffer.put(vertexCoords);
    		vertexVerticesBuffer.position(0);
    	}
    }
    
    /**
     * 设置相机预览图片的大小
     * @param width
     * @param height
     */
    public void setPreviewSize(int width, int height)
    {
    	if(previewWidth != width || previewHeight != height)
    	{
    		//图片预览的大小
    		previewWidth  = width;
    		previewHeight = height;
    		
    		/**
    		 * 摄像头的图片是有一个90的旋转 所以比例和控件的宽与高的比例 是倒数关系
    		 * 
    		 * 从左上角开始预览 便于裁剪
    		 */
    		float previewRatio = 1.0f * previewHeight / previewWidth;
    		
    		//纹理坐标的范围
    		float startU = 0, endU = 1;
    		float startV = 0, endV = 1;
    		
    		if(previewRatio > ratio)
    		{
    			startU = 0;
    			endU   = 1;
    			
    			startV = (previewHeight - ratio * previewWidth) / (1.0f * previewHeight);
    			endV   = 1.0f;
    			
    			//startV = (previewHeight - ratio * previewWidth) / (2.0f * previewHeight);
    			//endV   = 1.0f - startV;
    			
    		}
    		else if(previewRatio < ratio)
    		{
    			//startU = (previewWidth - previewHeight / ratio) / (2.0f * previewWidth);
    			//endU   = 1.0f - startU;
    			startU = 0;
    			endU   = previewHeight / ratio / previewWidth;
    			
    			startV = 0;
    			endV   = 1;
    		}
    		
    		//纹理坐标
    		float textureVertices[] = 
	        	{
    				endU, startV,
    				endU, endV,
    				startU, startV,
    				startU, endV,
	        	};
    		
    		//纹理坐标
	  		ByteBuffer textureBB = ByteBuffer.allocateDirect(textureVertices.length * 4);
	  		textureBB.order(ByteOrder.nativeOrder());
	  		textureVerticesBuffer = textureBB.asFloatBuffer();
	  	    textureVerticesBuffer.put(textureVertices);
	  	    textureVerticesBuffer.position(0);
    	}
    }
    
    /**
     * 绘制纹理
     * @param mvpMatrix
     * @param textureMatrix
     */
  	public void onDraw(float[] mvpMatrix, float[] textureMatrix)
  	{
  		//使用glUseProgram()将OpenGL渲染管道切换到着色器模式，并使用刚才做好的（着色）程序对象。
        GLES20.glUseProgram(program);
        
        //绑定纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        
        //打开 顶点 纹理数组
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glEnableVertexAttribArray(textureCoordHandle);
        
        /*
         * void glUniformMatrix4fv(GLint location,  GLsizei count,  GLboolean transpose,  const GLfloat *value); 
			location:指明要更改的uniform变量的位置
			count:指明要更改的矩阵个数
			transpose:指明是否要转置矩阵，并将它作为uniform变量的值。必须为GL_FALSE。
			value:指明一个指向count个元素的指针，用来更新指定的uniform变量。
         */
        //设置坐标变换矩阵 
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);
        
        //设置纹理变换矩阵
        GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, textureMatrix, 0);
        
        //设置纹理坐标
        GLES20.glVertexAttribPointer(textureCoordHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, textureVerticesBuffer);
        
        //设置顶点数据
        GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, vertexVerticesBuffer);
        
        //设置纹理的透明度
        GLES20.glUniform1f(alphaHandle, alpha);
        
        //绘制纹理
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        
        //关闭顶点和纹理数组
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(textureCoordHandle);
  	}
}






























