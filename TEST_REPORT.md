# 轻跃跳绳0.1.2测试报告

2026-10-24，versionCode3，最低Android 8.0/API26，target34，四ABI。此报告记录测试候选的发布前验证。包名、公开测试签名与历史格式延续旧版，实际覆盖安装与保留数据尚需手机验证。

| 验证层次 | 实际结果与范围 |
|---|---|
| 修复前差距复现 | 新回归在未改动的0.1.1计数器上复现2项失败：可靠结果返回后复用过期校准、宽高比变化导致相同比例位移判定不一致 |
| 最终JVM回归 | **49项/390断言通过**；覆盖原训练/休息/暂停/持久化、正常与低幅度合成跳、帧率变化、关节缺失、遮挡、站立/走动/深蹲负例、YUV/RGB/旋转/裁剪，以及新增有效期、宽高比与诊断字段检查 |
| 实际模型静态图像推理 | 一张真实照片的原图/竖屏/远景/下移4种摆放均获取人物和双脚；空白拒绝。75帧静态动态裁剪0次计数。桌面共享Java预处理、坐标映射、准入和计数器，不是4名真人或运动视频 |
| 构建与APK检查 | javac/AAPT2/D8/签名；v2及独立JAR-v1验证、DEX无Android/JDK框架类、模型/许可证、四ABI及16KB ELF/ZIP对齐，实际结果保存在test-evidence |
| 参考应用研究 | 现已取得用户提供的本地安装包并完成Rea/JADX私有静态研究。0.1.1报告中的“未取得”是当时状态。未运行第三方APK/native，未复用其专有源码、模型或阈值 |
| 模拟器/手机/真人准确率 | **未执行**。无硬件相机方向/性能/TTS或真人误报漏报结论，不将合成及静态图片测试换算为准确率 |

模型固定SHA256 `0fac2226112d0371903ca86e3853cec24ef603a0b2f96f589b180f0ebdd135ab`；输入UINT8 `[1,192,192,3]`，输出FLOAT32 `[1,1,17,3]`。桌面LiteRT2.3.0与APK1.4.0有版本差异。

照片来自 [TensorFlow官方测试目录](https://github.com/tensorflow/tfjs-models/tree/master/pose-detection/test_data)，固定SHA256 `c8a830ed683c0276d713dd5aeda28f415f10cd6291972084a40d0d8b934ed62b`。许可依据是仓库Apache-2.0，未找到照片单独授权说明；只在本地测试，不随交付再分发。

复现命令（已获准JDK与固定依赖的Windows项目内）：

```powershell
python scripts/build.py
python scripts/test_image_pipeline.py
python scripts/verify_static.py
python scripts/verify_apk.py
python scripts/package_source.py
```

图像测试另需在 `.tools/inference-libs` 局部安装 `ai-edge-litert==2.3.0 numpy==2.5.4 Pillow==11.3.0`，将官方照片放在 `.tools/image-fixtures/tf-pose.jpg`，程序校验其哈希。JVM回归不依赖照片或桌面推理库。

构建使用获准项目局部Temurin17及固定Maven工具；android-all为非标准AOSP编译API输入，未作标准SDK/Gradle等效验证。签名采用公开AOSP测试材料，证书SHA256 `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`，仅开发验收，不能证明正式发布者身份。未创建新持久凭证。

权限仅CAMERA，无网络/录音/定位/存储权限；视频不写文件、不上传。数值诊断默认关闭，仅内存最多240行，包含肩髋踝分数、人物获取/证据/就绪状态；主动复制才写剪贴板。

交付采用正向白名单并逐文件比对；排除第三方APK、模型、反编译材料、私有研究日志、用户路径/分享链接、签名私钥、工具和其他项目。详情见 [修复与差距检查](docs/RECOGNITION_FIX.md)，设备步骤见 [验收清单](docs/DEVICE_ACCEPTANCE.md)。
