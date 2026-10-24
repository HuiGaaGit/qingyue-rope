# 轻跃跳绳视觉说明

在界面设计前查看了 [天天跳绳小米官方商店页](https://r.app.xiaomi.com/details?id=com.gkid.crazyrope) 中的实际截图像素。商店图库是225×400宣传合成图，以下尺寸与色值是原创实现选择，不声称为原版精确测量值。

- [计数页参考](https://file.market.xiaomi.com/thumbnail/jpeg/l395/AppStore/06167b0fed29045d599443f38311e0666abf2f101)：黑色训练工具区、全屏相机、左上橙色计时、右上黑底白字计数；本版保留该构成，去掉AR、分享、录制、社交按钮。
- [首页参考](https://file.market.xiaomi.com/thumbnail/jpeg/l395/AppStore/061a1e3298c7846a68b38c25a77589e95c536fea7)：浅色背景、鲜橙主动作、蓝青紫运动卡与彩色圆角徽章。本版简化为品牌标题、今日统计、训练入口、最近记录。
- [计划页参考](https://file.market.xiaomi.com/thumbnail/jpeg/l395/AppStore/09838e69d64fd4909999e837a63345d99a2c4835a)：横向日期切换及白色卡片；本版记录页使用全部/今天/昨天/近7天筛选。

独立品牌：轻跃跳绳，包名 `org.qingyue.rope`。主橙 `#FF6B35`，蓝 `#4C9BFF`，青 `#31BFD3`，紫 `#9970F5`，页面背景 `#F7F8FA`。页面16dp边距，卡片16dp圆角，主按钮52–56dp。黑粗标题和灰色次级文字。

`Icons.java` 以Canvas几何图形原创绘制跳绳、秒表、休息、喇叭、日历等圆头运动符号；启动图标是独立原创矢量。未提取或复用天天跳绳Logo、原图标或宣传素材。研究用商店截图不打入APK、不放入源码交付包。

界面实际设备渲染尚未验证，不能把设计说明或预览当作手机截图。
