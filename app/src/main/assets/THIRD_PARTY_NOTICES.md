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
