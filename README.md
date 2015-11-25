# DeepRed
livephoto on android

在android实现类似于LivePhoto的功能

## 原理
见博客：
<br>
##demo
当点击“拍摄按钮”的同时点击网页的“启动”按钮，保留了前后1.5s左右的视频
![image](https://github.com/amazingyyc/DeepRed/blob/master/pic/2015-11-25 16_58_42.gif)
<br>
##代码说明
####视频默认存储在sdcard/DeepRed目录中
1改变视频的分辨率：修改com.deepcolor.deepred.shot.CameraInstance中的MIN_PREVIEW_WIDTH的值，MIN_PREVIEW_WIDTH越大视频分辨率越大。<br><br>
2改变视频bit率：修改jni/encoder.cpp下的int Encoder::get_bit_rate_by_height(int height)函数<br><br>
3改变视频帧率：修改jni/content.h中的VIDEO_FPS值<br><br>
3改变音频bit率：修改jni/content.h下的AUDIO_BIT_RATE值<br><br>
4改变视频长度：修改jni/content.h下的TIME_DURATION和com.deepcolor.deepred.shot.ShotInstance中的ADD_SECOND_PART_MSG_DELAY_TIME的值<br><br>
5改变视频存储位置：修改com.deepcolor.deepred.util.FileUtil下的getAppPath()函数
## 截图
1：中间“黄色摄像机”图标：是否开启livephoto功能<br>
2：“闪电图标”：对焦模式<br>
####3：左右滑动切换前后摄像头<br>
![image](https://github.com/amazingyyc/DeepRed/blob/master/pic/S51125-165600.jpg)
<br>


