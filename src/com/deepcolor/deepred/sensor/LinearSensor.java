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

package com.deepcolor.deepred.sensor;

import com.deepcolor.deepred.shot.CameraInstance;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * 先行加速 判断是否进行自动对焦
 * @author WSH
 *
 */
public class LinearSensor implements SensorEventListener
{
	/**
	 * 加速的最小的阈值
	 */
	private static final float MAX_ACCELERATION_THRESHOLD = 4.0f;
	private static final float MIN_ACCELERATION_THRESHOLD = 1.0f;
	
	private SensorManager sensorManager;
	private Sensor sensor;
	private boolean autoFocus = false;

	public LinearSensor(Activity activity)
	{
		/**
		 * 得到传感器
		 */
		sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
		sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
		
		/**
		 * 注册传感器
		 */
		sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
	}

	/**
	 * 数值变化时
	 */
	@Override
	public void onSensorChanged(SensorEvent event) 
	{
		/**
		 * 不是线性加速计 直接返回
		 */
		if(Sensor.TYPE_LINEAR_ACCELERATION != event.sensor.getType())
			return;
		
		/**
		 * 得到三个方向的加速值
		 */
		float x = event.values[0];
		float y = event.values[1];
		float z = event.values[2];
		
		/**
		 * 得到加速度
		 */
		float acceleration = (float) Math.sqrt(x * x + y * y + z * z);
		
		//---------------------------------------------
		//System.out.println(acceleration);
		
		if(acceleration > MAX_ACCELERATION_THRESHOLD)
		{
			autoFocus = true;
		}
		else if(acceleration < MIN_ACCELERATION_THRESHOLD && autoFocus)
		{
			autoFocus = false;
			
			/**
			 * 进行自动对焦
			 */
			CameraInstance.getInstance().autoFocus(null, null);
		}
	}

	/**
	 * 精度变化时 调用
	 */
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) 
	{
		
	}
}




















