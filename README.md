# NatTypeTester

原生 Android NAT 类型检测工具，基于 STUN 协议（RFC 3489/5389/5780），可准确识别多种 NAT 类型。

## 功能特性

- 支持 **TCP** 和 **UDP** 双协议检测
- 识别 NAT 映射行为：Endpoint-Independent、Address-Dependent、Address and Port-Dependent
- 识别 NAT 过滤行为：Endpoint-Independent、Address-Dependent、Address and Port-Dependent
- 综合判定 NAT 类型：Full Cone、Restricted Cone、Port Restricted Cone、Symmetric 等
- 可折叠的检测详细信息和日志
- 一键复制检测日志

## 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose + Material Design 3
- **协议**：自研 STUN 客户端
- **最低 SDK**：Android 8.0（API 26）

## 构建

```bash
# 克隆仓库
git clone <repo-url>
cd NatTypeTester

# 构建 Release APK（自动签名 v1+v2）
bash build_android.sh
```

构建产物位于 `NatTypeTester-Android/app/build/outputs/apk/release/NatTypeTester-{version}.apk`，同时自动拷贝到项目根目录。

## 项目结构

```
NatTypeTester/
├── build_android.sh          # 构建脚本
├── NatTypeTester-Android/    # Android 工程
│   ├── app/
│   │   ├── src/main/java/com/nattype/tester/
│   │   │   ├── nat/           # NAT 检测核心
│   │   │   ├── stun/          # STUN 协议实现
│   │   │   ├── network/       # 网络工具
│   │   │   └── ui/            # Compose UI
│   │   └── build.gradle.kts
│   └── ...
└── README.md
```

## 许可证

MIT License
