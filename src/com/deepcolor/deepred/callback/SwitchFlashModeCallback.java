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

/**
 * 切换闪光灯回调
 * @author WSH
 *
 */
public interface SwitchFlashModeCallback 
{
	/**
	 * 开始切换
	 */
	public void startSwitch();
	
	/**
	 * 完成切换 传入 切换后的模式
	 * @param mode
	 */
	public void finishSwitch(String mode);
	
	/**
	 * 切换失败
	 */
	public void failSwitch();
}
