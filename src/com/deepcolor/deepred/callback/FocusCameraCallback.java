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

package com.deepcolor.deepred.callback;

import android.graphics.PointF;

/**
 * 对焦回调函数
 * @author WSH
 */
public interface FocusCameraCallback 
{
	/**
	 * 开始对焦 传入对焦的坐标值
	 * @param x 对焦的触摸点
	 * @param y
	 */
	public void startFocus(PointF touchPoint);
	
	/**
	 * 完成对焦
	 */
	public void finishFocus();
	
	/**
	 * 取消对焦操作 或者对焦失败的时候操作
	 */
	public void cancelFocus();
}
