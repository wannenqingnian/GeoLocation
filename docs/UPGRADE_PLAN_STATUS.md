# Step1–Step9 完成情况核对

核对日期：2026-10-31。

范围：`GeoLocation_Upgrade_Prompts_and_References/prompts_by_step` 中的 Step1–Step9，Step0 作为背景约定。核对对象包括工作区已有的未提交改动。

结论：**尚未全部完成，当前没有一项可以按原计划的全部要求验收通过。** 部分模块已有模型、服务或界面，但存在未连接入口、固定示例数据、无持久化及缺少验证的情况。

本次为静态完成度核对，没有修改现有功能代码，没有对本批改动运行编译或设备测试。此前已发布版本的测试结果不能作为这批新增实现的验证结果。

| 计划 | 状态 | 已有内容 | 未完成或无法验收的内容 |
| --- | --- | --- | --- |
| Step1 运行模式与 Root | 部分实现，存在架构问题 | 模式枚举、部分设置同步、挂载策略、Root 入口和服务 | Root 方案尝试从跨进程 Binder 代理读取服务端私有字段，不能按此方式访问另一进程中的服务对象；模式切换与不同系统的完整行为未验证 |
| Step2 GNSS 与位置抖动 | 部分实现 | 卫星模型、位置帧合成器、GNSS 回调代码、部分设置界面 | GNSS 派发仍写死卫星数量和星座等参数；设置与实际输出没有完整接通，缺少本批算法与回调测试 |
| Step3 三方 SDK | 部分实现 | 高德、百度、GMS/HMS 等适配文件及通道模型 | 分通道配置与运行逻辑未完整联动；存在固定地址示例，监听器处理保留空分支；无法视为完整的 SDK 兼容实现 |
| Step4 路线与摇杆 | 部分实现 | 路线模型、推进引擎、服务、悬浮摇杆、跑道 JS 文件 | 地图尚未提供计划要求的路线/跑道编辑入口；未找到跑道 JS 的加载调用和路线收藏闭环；驻留与运动状态功能未完整接通 |
| Step5 步频与传感器 | 部分实现 | 步频模型、计算方法和传感器适配文件 | 缺少完整的全局/分应用设置入口与配置读写闭环；部分派发参数仍使用固定默认值，缺少专项测试 |
| Step6 Wi-Fi 与基站环境 | 演示界面及部分基础代码 | 环境模型、构造工厂、页面、Root 环境适配文件 | 页面展示固定示例；采集按钮仅统计数量并提示，没有保存模板；构造工厂未接入完整调用链，页面没有导航入口 |
| Step7 BLE | 部分实现 | BLE 模型、扫描页面、广播与 GATT 基础服务 | 页面无导航入口；模板保存按钮仅显示提示；广播内容固定，未完整读取所选配置；扫描停止与权限流程仍需审查 |
| Step8 NFC | 未找到实现 | 未找到计划对应的 NFC 模型、读取器、派发器或管理页面 | Manifest 未声明计划中的 NFC 权限和硬件特性，导航也没有 NFC 入口 |
| Step9 额外增强 | 部分既有逻辑，新增要求未完成 | 已有部分定位状态适配和通用 Root 命令 | 未找到 AES-GCM 备份恢复、LauncherAlias/LsposedSettingsAlias 组合或目标应用重启入口的完整实现 |

## 代表性核对依据

- [导航定义](../app/src/main/java/io/github/ceigt/geolocation/manager/ui/navigation/Screen.kt)与[导航接入](../app/src/main/java/io/github/ceigt/geolocation/manager/ui/navigation/NavGraph.kt)：没有环境、BLE、NFC 的对应路由。
- [Root 位置模块](../app/src/main/java/io/github/ceigt/geolocation/inject/RootLocationHook.kt)：代理对象分支没有完成实际的跨进程服务访问处理。
- [GNSS 回调](../app/src/main/java/io/github/ceigt/geolocation/xposed/hooks/GnssStatusHooks.kt)：派发时仍使用固定的卫星数量、星座和信号预设。
- [路线引擎](../app/src/main/java/io/github/ceigt/geolocation/manager/route/RouteSimulationEngine.kt)、[地图页面](../app/src/main/java/io/github/ceigt/geolocation/manager/ui/map/MapScreen.kt)：已有推进代码，但没有形成计划所要求的完整交互与运行闭环。
- [环境页面](../app/src/main/java/io/github/ceigt/geolocation/manager/ui/environment/EnvironmentSimulationScreen.kt)：固定样例列表，采集动作未生成和持久化模板。
- [BLE 页面](../app/src/main/java/io/github/ceigt/geolocation/manager/ui/ble/BleScreen.kt)：保存模板动作仅弹出提示，未保存配置；页面未接入导航。
- [BLE 外设服务](../app/src/main/java/io/github/ceigt/geolocation/manager/ble/BlePeripheralService.kt)：使用固定广播和 GATT 数据，尚非配置驱动的完整服务。
- [Root 环境适配](../app/src/main/java/io/github/ceigt/geolocation/manager/root/RootEnvironmentAdapter.kt)：恢复时将扫描开关直接设置为开启，没有保存并恢复用户原值；不能宣称已实现无副作用恢复。
- [Manifest](../app/src/main/AndroidManifest.xml)：尚无 NFC 声明及计划中的两个 Activity 别名。
- [现有更新日志](../更新日志.md)已将多项内容描述为完成，但静态实现不足以支持这些完整性声明。

Android 的 Binder 用于跨进程通信，远程代理不等于服务端对象本身，不能通过本地反射直接读取另一进程的私有字段。参见 [Android IBinder 文档](https://developer.android.com/reference/android/os/IBinder)。

## 可继续实施的测试范围

可按自有应用及测试设备的需求，完善保留模拟标识的测试位置、路线预览、明确授权的 BLE/NFC 测试、配置校验及加密备份。

计划中面向运动/考勤校验规避、抹除模拟标识、克隆环境以伪造校验结果的内容，不作为本次补齐或发布范围。本次没有将这些未完成改动推送到 GitHub，也没有构建或发布新 APK。

## 2026-10-31 阶段修复记录

1. **全局系统级 Hook (`LocationMode.GLOBAL_HOOK`) 连通**：
   - 在设置界面 `LocationModeSelector` 补充了“全局系统级 Hook”选项，支持切换并保存 `enable_global_hooks=true` 与 `enable_system_hooks=true`。
   - 修改 `SystemServicesHooks.kt` 与 `SystemActiveLocationHooks.kt` 的目标选择逻辑（`selectSystemTargetPackage`、`selectVerifiedSystemTargetPackage`），当 `global=true` 时忽略白名单配置，直接稳定选取三方调用方包名；排除管理器自身与 Android 框架核心包。
   - 在 `SystemTargetSelectionTest.kt` 中补充了全局模式单元测试（4 个用例），验证多三方应用共享 UID、已配置目标优先归因及系统框架包排除。

2. **环境全要素真实采集（Wi-Fi、蓝牙、基站、卫星）落地**：
   - 新建 `EnvironmentCollector.kt`，并发从 Android 系统公开 API（LocationManager、WifiManager、TelephonyManager、BluetoothLeScanner、GnssStatus.Callback）异步现场采样。
   - 移除原先硬编码的固定假数据和 12 颗虚构卫星预设；支持 GSM、CDMA、WCDMA、LTE、NR (5G) 完整制式基站，支持 BLE 信标扫描去重与 GNSS 真实卫星星座及 CN0 数据记录。
   - 重构 `EnvironmentSimulationScreen.kt`：增加卫星 Tab 与已采集 BLE 信标列表；支持一键现场采集多权限申请与进度提示，实时保存至 `PreferencesRepository` 持久化环境快照。
   - 补齐 `AndroidManifest.xml` 中的 Wi-Fi 扫描相关权限（`ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`）。
   - 通过 Unit Test（35 个测试全部通过）与 Gradle `assembleDebug` 构建验证。

