# 轻跃跳绳 / Qingyue Rope

中文、开源、无广告的安卓跳绳精简 MVP：自动计数、自由/定时/分组训练、离线中文报数、本地历史。无账号、社交、会员或分析追踪。

**当前仅为开发测试预发布，不是正式分发或生产版本。未在手机验证相机、离线TTS、实际界面或真人计数准确率。**

## 下载与测试风险

[开发测试预发布与APK](https://github.com/HuiGaaGit/qingyue-rope/releases/tag/v0.1.0-test) · [测试报告](TEST_REPORT.md) · [真机验收清单](docs/DEVICE_ACCEPTANCE.md)

- 最低Android 8.0 / API26，目标API34；包含arm64-v8a、armeabi-v7a、x86和x86_64。
- APK采用**公开AOSP测试私钥对应的测试证书**。任何人都能用该公开私钥签名同包名的应用；此签名不能证明发布者身份，也不能建立安全的生产升级信任。请仅在受控测试环境验收。正式版本需要维护者另行提供独立私密签名。
- 这里只发布测试APK和源码，仓库和发布资产不包含签名私钥、凭证或工具缓存。没有生成新的签名凭证。
- 下载后先用Release中的`SHA256SUMS.txt`核对文件；SHA-256用于确认字节一致，不替代可信的正式签名。

APK SHA-256：`bc30dd9ed217b96e2fe9eb895c8194adefc7b80caa8ff1c02e961d4d068d132c`。

## 功能与边界

固定手机、单人正面全身入镜，站稳校准，倒数后开始。支持暂停、继续、结束；休息不计数，切到后台自动暂停并关闭相机，恢复需手动操作。训练记录保存在应用私有目录，最终保存按会话ID去重。

仅申请相机权限；无网络、录音、位置和存储权限。画面只在设备内存处理，不上传、不默认录制或保存。语音只选系统已安装且标记不需网络的中文声音；缺少音色可静音训练。

MoveNet SinglePose只处理一人，不能可靠检测旁人或跟踪多人。算法统计双脚同步起落，**不能判断绳圈是否实际过脚**；空跳可能计数，双摇算一次起落，交替脚不在本版范围。计数精度、低光和不同设备性能均待实测。

## 已执行的验证

- 正式Java核心类JVM测试：30场景、104断言通过。
- 合成关键点8段，每段20次，覆盖60/120/180/220次每分钟与约15/30fps采样；不代表图像推理或真人准确率。
- Java编译、AAPT2资源/清单、D8、APK v2与独立JAR v1签名、DEX无框架类、模型、四ABI及16KB对齐检查通过。
- 未进行Android模拟器、手机安装、相机/TTS、真人准确率或Android存储故障恢复测试。完整范围见测试报告。

## 构建

已测试工具链：Windows x64、Python3.13、Eclipse Temurin17.0.20.1+1、Google Maven AAPT2/D8/apksig及Robolectric AOSP API。下载工具前请审阅各自许可。

```powershell
python scripts/bootstrap.py
$env:QINGYUE_JDK = '本机JDK17目录'
python scripts/build.py
python scripts/verify_apk.py
```

`bootstrap.py`校验固定官方依赖并下载官方MoveNet模型，也会获取**公开AOSP测试签名材料**到被忽略的`.tools/`中，用于开发测试构建。它不会创建生产签名或把私钥打入APK。不要把`.tools/`或任何签名材料上传。

`android-all`只作为编译API/资源输入，不进入APK；这是AOSP/Maven构建路径，非标准Android SDK stub，可能与标准SDK的隐藏API/资源/lint诊断不同。标准SDK/Gradle配置也在源码中，但该路径尚未执行验证。资源与签名结构正确不能替代设备测试。

工具只留在项目目录，不要求修改系统PATH。可选`fetch_temurin.py`从官方Adoptium下载JDK17，执行前需明确同意其下载与运行。发行包不包含JDK或Android SDK。

## 代码与许可

核心算法/状态机/档案格式位于`app/src/main/java/org/qingyue/rope/core/`；相机、TTS、AtomicFile与UI分开实现，回归测试位于`tests/CoreTests.java`。

原创代码及Canvas/矢量图标采用Apache-2.0。Google LiteRT和MoveNet保留完整许可、官方模型卡和来源声明，见[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。模型原版文件SHA-256已锁定，未复用无许可证跳绳项目。

独立品牌“轻跃跳绳”与天天跳绳无隶属关系；仅参考官方商店中可见的布局习惯，未复制Logo、原图标或宣传素材。仓库不包含商店参考截图。
