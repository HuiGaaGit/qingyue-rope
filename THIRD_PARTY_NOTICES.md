# Third-party notices

轻跃跳绳原创代码、Canvas图标和启动矢量：Copyright 2026 Qingyue contributors，Apache License 2.0，完整文本见 LICENSE。未复用任何无许可证跳绳项目。

## APK 中包含的运行依赖

| 依赖 | 版本 / 来源 | 许可 |
|---|---|---|
| Google LiteRT 及 LiteRT API（原TensorFlow Lite） | `com.google.ai.edge.litert:litert:1.4.0` 和 `litert-api:1.4.0`，Google Maven | Apache-2.0；AAR原始 LICENSE 保留在 `licenses/LiteRT-LICENSE.txt` 和APK assets |
| Google MoveNet SinglePose Lightning Float16 TFLite | 官方 Kaggle `google/movenet/tflite/singlepose-lightning-tflite-float16/1`，包内 `4.tflite` | Apache-2.0；Google官方模型卡首页明确声明，见 `licenses/MoveNet.SinglePose-Model-Card.pdf` |

模型官方来源：<https://www.kaggle.com/models/google/movenet/tfLite/singlepose-lightning-tflite-float16>

模型卡：<https://storage.googleapis.com/movenet/MoveNet.SinglePose%20Model%20Card.pdf>

模型作者（官方模型卡）：Francois Beletti、Yu-Hui Chen、Ard Oerlemans、Ronny Votel，Google。模型文件未修改，仅在本机执行推理。模型SHA-256：`0fac2226112d0371903ca86e3853cec24ef603a0b2f96f589b180f0ebdd135ab`。

LiteRT AAR附带的其他许可声明也完整保留，应用的“隐私与开源”入口说明该路径。无需Google Play服务，不包含广告或分析SDK。

## 仅构建使用，不进入 APK

| 依赖 | 来源与版本 | 许可 |
|---|---|---|
| AAPT2 | Google Maven `com.android.tools.build:aapt2:8.6.1-11315950:windows` | Apache-2.0及工具包附带NOTICE |
| R8 / D8 | Google Maven `com.android.tools:r8:8.6.27` | BSD-3-Clause；包含其第三方依赖许可，JAR内LICENSE为准 |
| apksig | Google Maven `com.android.tools.build:apksig:8.6.1` | Apache-2.0 |
| Android API/framework resources | Maven Central `org.robolectric:android-all:14-robolectric-10818077`，来自AOSP源码构建 | Apache-2.0，POM说明；只做javac/AAPT2的编译输入，不打入APK |
| Android Gradle Plugin（可选标准SDK工程） | Google Maven `com.android.application:8.6.1` | Apache-2.0 |
| AOSP公开测试证书/测试私钥 | `aosp-mirror/platform_build` 的 `android-14.0.0_r1/target/product/security/testkey.*` | AOSP Apache-2.0；这是公开测试材料，不生成新凭证，不可用于生产分发信任 |

工具下载地址与SHA-256见 `dependencies.lock.json`。JDK是独立运行环境，按单独获准版本和其GPLv2＋Classpath Exception许可使用，不随源码包或APK再分发。实际JDK版本见构建日志。

阶段语法检查另用了PyPI正常包管理下载的 `javalang 0.13.0` 与 `six 1.17.0`（均MIT），仅在 `.tools/py-libs` 中执行，不进入APK或源码交付包，也不是构建必需依赖。

研究用天天跳绳商店截图归原权利人所有，仅用于观察布局；未包含在APK、源码交付包或原创图标中。独立品牌“轻跃跳绳”与该应用无隶属关系。

## 0.1.1 识别修复的参考与测试工具

裁剪获取及坐标映射参考 Google 的 [MoveNet Android 示例](https://github.com/tensorflow/examples/blob/master/lite/examples/pose_estimation/android/app/src/main/java/org/tensorflow/lite/examples/poseestimation/ml/MoveNet.kt) 与 [官方教程](https://www.tensorflow.org/hub/tutorials/movenet)；以进入/退出阈值计次的思路参考 [MediaPipe 重复动作分类说明](https://github.com/google-ai-edge/mediapipe/blob/master/docs/solutions/pose_classification.md)，均为 Apache-2.0 项目的设计参考。本项目的预处理、准入、状态机及测试为独立实现，没有复制天天跳绳代码、模型或素材。

以下工具仅在本机研究/测试，不进入APK或源码包：Google `ai-edge-litert 2.3.0`（Apache-2.0，桌面图像推理）；NumPy 2.5.4（BSD-3-Clause及其分发许可）、Pillow 11.3.0（MIT-CMU，测试图像画布）；ZXing 3.5.3（Apache-2.0，读取官网二维码）；Rea / `rea-agents 6.7.0`（MIT）及其官方文档指定的 `jadx-headless-mcp 0.7.1` 引擎（Apache-2.0）。没有运行全局setup、注册持久MCP或再分发这些工具。

真实静态照片来自 [TensorFlow tfjs-models 测试目录](https://github.com/tensorflow/tfjs-models/tree/master/pose-detection/test_data)，仅本地测试使用，未收入APK或源码ZIP；复现脚本固定其SHA-256并注明来源。测试结果不证明真人跳绳准确率。完整研究状态和边界见 `docs/RECOGNITION_FIX.md`。

后续已对用户提供的参考应用安装包完成私有静态研究，只保留高层架构结论与限制。该应用的安装包、模型、反编译代码和原始研究日志均不进入轻跃APK、公开文档或源码包，未运行该应用及其原生库；0.1.2的修正来自本项目自身可复现的时间边界与坐标单位问题，没有复用第三方实现或阈值。
