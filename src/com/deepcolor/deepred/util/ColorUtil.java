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

package com.deepcolor.deepred.util;

import android.graphics.Color;

/**
 * 定义项目中需要的颜色
 * @author WSH
 *
 */
public class ColorUtil 
{
	public static final int FOCUS_COLOR  = 0xFFFFA000;	//对焦控件颜色
	//public static final int BAFFLE_COLOR = 0xAAFF4545;	//挡板的颜色
	
	/**
	 * 得到一个颜色的相反 颜色
	 * @param color
	 * @return
	 */
	public static int getOpposeColor(int color)
	{
		return Color.rgb(255 - Color.red(color), 255 - Color.green(color), 255 - Color.blue(color));
	}
}
