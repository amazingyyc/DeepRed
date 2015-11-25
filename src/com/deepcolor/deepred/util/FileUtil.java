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

import java.io.File;

import android.os.Environment;

public class FileUtil 
{
	/** 获取sdcard路径 */
	public static String getExternalStorageDirectory() 
	{
		String path = Environment.getExternalStorageDirectory().getPath();
		
		if (DeviceUtil.isZte()) 
		{
			path = path.replace("/sdcard", "/sdcard-ext");
		}
		return path;
	}
	
	private static final String APP_PATH = "DeepRed";
	
	/**
	 * 得到 应用的根目录
	 * @return
	 */
	public static String getAppPath()
	{
		String appPath =  getExternalStorageDirectory() + File.separator + APP_PATH;
		
		isDirExitAndCreate(appPath);
		
		return appPath;
	}
	
	/**
	 * 判断当前目录是否存在 不存在就创建一个 存在就删除再创建一个目录
	 * @param path
	 */
	public static void isDirExitDeleteCreate(String path)
	{
		File file = new File(path);
		
		//如果文件存在且为目录 就直接删除
		if(file.exists() && file.isDirectory())
		{
			//删除目录 及 一下 所有的文件
			deleteDir(path);
		}
		
		//创建一个目录
		file.mkdir();
	}
	
	/*
	 * 判断目录是否存在 如果存在直接返回 如果不存在创建一个目录
	 */
	public static void isDirExitAndCreate(String path)
	{
		File file = new File(path);
		
		//如果不存在 或者 不是一个目录 就创建一个
		if(!file.exists() || (file.exists() && !file.isDirectory()))
		{
			file.mkdir();
		}
	}
	
	/**
	 * 删除对应的文件或者目录 如果是目录将目录内的文件同样删除
	 * @param path
	 */
	public static void deleteDir(String path)
	{
		File file = new File(path);
		
		//不是目录直接删除
		if(!file.isDirectory())
		{
			file.delete();
		}
		else if(file.isDirectory())
		{
			//是目录递归删除
			String[] filelist = file.list();
			for(int i = 0; i < filelist.length; ++i)
			{
				//对应的文件
				File delfile = new File(path + File.separator + filelist[i]);
				
				if(!delfile.isDirectory())
					delfile.delete();
				else if(delfile.isDirectory())
					deleteDir(path + File.separator + filelist[i]);
			}
			
			file.delete();
		}
	}
}
