# 构建与测试报告（2026-10-24）

**测试APK已构建，签名和结构验证通过；未在Android模拟器或手机安装运行。真人计数准确率未知。**

- 文件：`dist/qingyue-rope-0.1.0-test.apk`，25,027,745字节（约23.87 MiB）。
- APK SHA-256：`bc30dd9ed217b96e2fe9eb895c8194adefc7b80caa8ff1c02e961d4d068d132c`。
- 包名 `org.qingyue.rope`，版本0.1.0 / versionCode1，最低 **Android 8.0 / API26**，目标API34。
- 使用官方Eclipse Temurin jdk-17.0.20.1+1。下载ZIP同时匹配官方API与校验文件SHA-256：`e53a79c3c3d86865bd7e787903884331068e71321714ffd44f145785affc7cb0`。仅在本项目运行，未改系统PATH。

| 验证层级 | 实际结果 | 证据 |
|---|---|---|
| 正式Java核心类的JVM测试 | **30场景 / 104断言全部通过**。计时边界、休息不计数、暂停/恢复、过期帧、档案校验与最终记录去重 | `docs/test-evidence/core-tests.txt` |
| 合成计数序列 | 60/120/180/220次/分钟 × 约15/30fps，8段各20个合成双脚起落均计为20；髋单独移动、脚单独移动、小抖动为0；丢失/NaN/裁切及跨阶段落地门控通过 | 同上；不是图像推理或真人准确率 |
| 应用Java编译 | 正式源码以`javac --release 8`编译通过；有公开平台API弃用提示，无编译错误 | 编译通过；包含本机路径的原始构建日志仅本地保留 |
| AAPT2资源/编译清单 | 包名与中文名称正确，min26/target34，**仅CAMERA权限**；禁止备份、迁移及明文流量 | `apk-badging.txt`、`apk-permissions.txt`、`apk-manifest.txt` |
| DEX独立审计 | **105个类定义**；Dex校验和通过，没有打入Android/JDK框架类 | `apk-audit.json` |
| 模型打包 | 原版MoveNet Lightning Float16，4,758,512字节；真实模型哈希与官方下载匹配；输入UINT8 `[1,192,192,3]`，输出FLOAT32 `[1,1,17,3]` | `static-checks.json`、`apk-audit.json` |
| 原生库与ZIP | arm64-v8a、armeabi-v7a、x86、x86_64机器类型正确；ELF LOAD与ZIP数据均16KB对齐，只依赖系统libc/libm/libdl/liblog | `apk-audit.json`；未在16KB页设备运行 |
| 签名 | APK v2由apksig验证通过；独立JAR v1校验覆盖13个载荷条目。min26下apksig优先v2，因此其v1标记为false不代表v1失败 | `apk-signature.txt` |
| 许可 | APK含Apache文本、LiteRT完整LICENSE与依赖声明；源码含官方MoveNet模型卡及Maven POM许可证据 | `THIRD_PARTY_NOTICES.md`、`licenses/` |
| Android模拟器 | **未执行**；无可用模拟器/adb | 没有可用模拟器环境 |
| 手机安装、相机推理、TTS、实际界面与AtomicFile故障恢复 | **未实测**；未进行手机安装或启用相机测试 | `docs/DEVICE_ACCEPTANCE.md` |
| 真人跳绳准确率 | **未知，未测**；没有真人/视频测试样本 | 同上 |

## 本轮修正与限制

相机打开尚未回调时关闭页面，会保留工作线程以接收回调并关闭设备；这一设备时序路径已通过编译，仍需按真机清单验证。运动阶段起点保留稳定校准，但等待新落地，避免把休息或倒数中起跳的周期计入；新增核心测试通过。

JDK jar工具的临时文件跨Windows沙箱移动曾失败，改成在项目目录直接生成标准ZIP/JAR后完成构建，没有扩大目录权限或修改安全设置。

编译API来自正规Maven Central的Robolectric AOSP android-all，非标准SDK stub路径；只作为API/资源输入，没有框架类进入APK。可能与标准SDK的隐藏API/资源/lint诊断有差异，结构检查不能替代实际设备测试。没有接受额外SDK协议、安装SDK/模拟器或系统软件。

签名使用**已有公开AOSP测试证书/测试私钥**，未生成新凭证。证书SHA-256：`a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`。仅供验收，生产发布需维护者提供正式签名；当前为GitHub公开开发测试预发布，未上架商店。

MoveNet SinglePose不能可靠检测多人。算法统计双脚起落，不能证明绳圈过脚；空跳可能计数，双摇算一次，交替脚不属于本版范围。合成测试不能证明真人准确率达标。

公开AOSP测试签名不能证明发布者身份，任何人都能用相同公开私钥签名同包名应用；本包仅供开发测试，非生产版本。详见 docs/SIGNING_AND_LIMITATIONS.md。
