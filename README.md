# 轻跃跳绳 / Qingyue Rope

中文、开源、无广告的安卓跳绳 MVP。只保留自动计数、计时/分组休息、离线中文语音和本地历史。独立品牌与原创图标，布局参考天天跳绳的小米官方截图。无账号、社交、会员、广告或分析追踪。

当前测试候选 **0.1.2 / versionCode 3**。沿用0.1.1的人物获取/计次分离，修正校准过期和宽高比坐标单位两项已复现缺口，补齐肩部置信度与计数就绪数值诊断。49项/390断言及真实静态图像推理通过；未验证手机相机或真人跳绳准确率。已完成参考应用的私有静态研究，未运行或复用其源码/模型。详见 [修复与差距检查](docs/RECOGNITION_FIX.md) 和 [测试报告](TEST_REPORT.md)。下载状态以 [GitHub Releases](https://github.com/HuiGaaGit/qingyue-rope/releases) 为准。

## 运行要求与使用

- 最低 **Android 8.0（API26）**；目标API34。APK包含arm64-v8a、armeabi-v7a、x86、x86_64四种ABI。
- 需可用的Camera2 YUV相机。单人、正面、固定手机、全身入镜；站稳约1秒校准，再倒数3秒开始。
- 自由、定时、分组三种训练。分组间休息不计数。暂停/切后台/锁屏会释放相机、停止报数并冻结计时；继续需手动点击，重新校准后才恢复。
- 离开画面3秒或相机画面中断会自动暂停。模糊、遮挡、低置信度、跨阶段与过期画面不计数。
- 系统已装离线中文音色时可每1/10/20个报数，缺少音色时可静音训练，不自动下载音色。
- 训练只保存在应用私有目录。每次计数与阶段切换写入，运动时长每秒检查点；进程意外退出后恢复为暂停，最近时长检查点可能落后约1秒。结束结果按会话ID去重，原子保存历史与清除活动记录。

## 隐私与算法边界

仅申请相机权限；无网络、录音、位置或存储权限。帧在内存中转换、推理和显示，不上传、不写入视频。历史禁用云备份及设备迁移。卸载/清除应用数据会删除记录。

MoveNet SinglePose只输出一人的17个关节点，不能可靠发现第二人或锁定多人中的同一人。多人进入画面请手动暂停。检测器用髋和双脚的同步抬升/落地统计双脚跳跃，**不能判断绳是否实际过脚**；空跳也可能被统计，双摇算一次起落，交替脚不在本版支持范围。

真人准确率、不同手机性能、相机/TTS及界面实际设备呈现尚待验证。算法合成测试、APK静态验证、模拟器测试和真人准确率必须分别解读，见 `TEST_REPORT.md`。真机步骤见 [docs/DEVICE_ACCEPTANCE.md](docs/DEVICE_ACCEPTANCE.md)。

## 构建

项目所有工具与产物均留在此独立目录。没有安装Android SDK、修改系统PATH或接受SDK协议。官方Maven依赖与哈希见 `dependencies.lock.json`，完整许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

需要Python 3.10+与**已获得运行许可**的Windows x64 JDK17。源码脚本不会自动下载或安装JDK。

```powershell
python scripts/bootstrap.py
$env:QINGYUE_JDK = '本任务内已获准的JDK目录'
python scripts/build.py --test-only
python scripts/build.py
```

`bootstrap.py`只从Google Maven、Maven Central、Google官方Kaggle和AOSP公共源码下载固定版本数据/工具依赖，校验锁文件并准备许可证；需要正常网络访问。首次锁文件已随源码提供，后续下载必须匹配SHA-256。

构建过程：Java编译 → AAPT2资源/清单 → D8 Dex → ZIP对齐 → apksig签名/验证。`android-all`由Robolectric从AOSP构建，只作为编译API和framework资源输入，不将Android框架类打入APK。这条工具链不是官方Android SDK的标准stub路径，可能与标准SDK在隐藏API、资源验证或 lint 诊断上存在差异；仅使用公开API，并对DEX类、清单、权限、签名、ABI及ELF对齐进行检查，不能替代真机安装运行。

同时提供标准Gradle项目（AGP8.6.1、Gradle8.7、JDK17、compileSdk34），供已经合法安装SDK的维护者使用。没有附带或执行Gradle Wrapper；debug/release均设为不自动创建签名，常规Gradle输出需所有者自行配置已获准签名。

本MVP用AOSP已公开的测试签名材料，**只适合测试验收**，不生成新凭证；生产版本应由所有者提供正式签名。没有公开仓库、商店发布或公开下载服务，也没有安装到手机或开启手机相机。

## 代码结构

| 路径 | 职责 |
|---|---|
| `core/JumpDetector.java`、`Pose.java` | 可独立测试的姿态门控和落地计数 |
| `core/SessionEngine.java`、`Plan.java` | 单调时钟训练状态机、分组与恢复 |
| `core/ArchiveCodec.java`、`WorkoutRecord.java` | 版本化档案、校验和与历史去重 |
| `LocalStore.java` | Android AtomicFile原子落盘，坏文件保留并禁止覆盖 |
| `CameraController.java`、`MoveNet.java` | 单工作线程Camera2、YUV旋转、CPU端侧推理 |
| `MainActivity.java`、`PosePreview.java`、`Icons.java` | 中文界面、相同坐标系预览、原创图标 |
| `OfflineSpeaker.java` | 只选择不需要网络的中文系统声音 |
| `tests/CoreTests.java` | 正式核心Java类的无Android依赖测试 |

维护入口：添加新模式先扩展状态机测试；调整阈值必须新增有人工真值的回归样本，不能用合成测试声称真人准确率。历史格式升级必须保留旧版本迁移，不得静默清空数据。

许可证：Apache-2.0；完整依赖声明、模型卡及各依赖许可随源码和APK保留。

本次2026-10-24已获授权使用官方Temurin 17.0.20.1+1，完成测试APK构建与签名/结构检查；核心测试30场景、104断言通过。实际执行证据随源码放在 `docs/test-evidence/`。没有进行Android设备安装、相机/TTS或真人准确率测试，详见测试报告。
