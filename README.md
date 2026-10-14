# 墨待 InkTodo

墨待是一个面向 Android 14+ / 小米 HyperOS 的原生个人待办 App。产品保持四个核心入口：待办、日历、番茄钟、已完成；数据、偏好、计时与提醒全部在本地运行，不依赖账号、云服务或电脑连接。

- 当前源码与 GitHub 候选版：`0.6.7-rc1`（versionCode 15）
- 包名：`com.inktodo.app`
- 最低 Android：8.0（API 26）
- 主要适配：Android 14+、Redmi K70 至尊版 HyperOS，以及 600dp+ Android 平板横竖屏
- Release APK：`InkTodo-0.6.7-rc1.apk`（4,993,144 bytes；SHA-256 `E5B998BD48022ED80DF45E3B27511A76CACE109115E53B9E8D395BF9FFC77B85`）。使用既有 Release 证书的 APK Signature Scheme v2 + v3 签名。

## 下载与构建

正式安装包可从 [GitHub Releases](https://github.com/jyc793/InkTodo/releases) 下载：

- 版本：`v0.6.7-rc1`（预发布版）
- 文件：`InkTodo-0.6.7-rc1.apk`
- 大小：4,993,144 bytes（约 4.76 MiB）
- SHA-256：`E5B998BD48022ED80DF45E3B27511A76CACE109115E53B9E8D395BF9FFC77B85`
- 签名：APK Signature Scheme v2 + v3

正式 APK 使用 InkTodo 专用 Release 证书签名，安装后不依赖电脑。首次从旧的 Debug 签名版切换到正式版时签名不同，Android 不允许直接覆盖；请先自行处理本地数据，再卸载旧版。

在 Android Studio 打开仓库根目录，使用 JDK 17 执行 Debug 构建：

```powershell
.\gradlew.bat assembleDebug
```

输出 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

正式签名构建通过环境变量指定项目外的属性文件：

```properties
storeFile=D:/secure/inktodo-release.jks
storePassword=请填写密钥库密码
keyAlias=inktodo-release
keyPassword=请填写密钥密码
```

```powershell
$env:INKTODO_SIGNING_PROPERTIES = "D:\secure\inktodo-signing.properties"
.\gradlew.bat assembleRelease
```

`*.jks`、`*.keystore`、`key.properties`、APK/AAB 和本地构建目录均已被 Git 忽略。请妥善保管签名证书与密码；丢失后将无法为 GitHub 侧载用户提供可覆盖安装的更新。

## 安装方法

### 手机直接安装

1. 从 Releases 下载正式 APK，或将自行构建的 APK 复制到手机。
2. 在文件管理器中打开 APK。
3. 按 HyperOS 提示允许该文件来源安装应用。
4. 安装后打开“墨待”，按实际使用的提醒功能逐项配置权限，不会在首次启动连续弹出全部权限。

### ADB 安装

```powershell
adb install -r InkTodo-0.6.7-rc1.apk
```

`-r` 会在签名一致时保留已有 Room 数据、DataStore 偏好和 Widget 配置。

## 已实现功能

- 待办：真实 Room 数据，新建、查看、编辑、完成、恢复、删除；标题、备注、日期、提醒方式、提醒时间与系统闹钟选项。
- 已完成：独立列表、查看、恢复和删除；使用“雾”的弱化视觉，不依赖大量绿色对号。
- 时刻提醒：固定提前 15 分钟预提醒；正式时间由高优先级通知渠道播放一次系统默认通知音。解锁状态在用户授予“页面上层显示”后直接覆盖当前页面弹出山水确认框，锁屏状态使用 Full Screen Intent；两者都会显示任务标题与可选备注。确认前正式通知保持常驻；不足 15 分钟时只安排正式提醒；一次性任务过去时间禁止无效安排。
- 重复提醒：新建时可选每天，或每周指定一个/多个星期；某个时刻与短于 24 小时的持续时间段均支持。列表只显示当天实例；后台预存下一次提醒，跨天后自动清理已过期的重复实例（无论完成与否），非重复任务的已完成历史保留。重复的持续时间段还可设 10–720 分钟的确认提醒间隔；若上一条未确认，不叠加新弹窗。删除待办时会提示删除该系列尚未完成的后续任务。已生成的后续任务不会因编辑某一次而自动改变，重复星期需删除未完成系列后重新创建。
- 持续提醒：开始与结束分别选择独立日期和时间；不重复时可跨数天、一周或一个学期。开始时由高优先级通道声振一次，随后使用独立无声常驻通知，每 6 小时由系统 Alarm 静默续期，结束时自动撤销。续期保留现有通知，不再先恢复后取消。
- 提醒同步：保存修改前取消旧 Alarm；删除、完成时取消关联提醒；恢复时按当前时间重新判断。
- 重启恢复：`BOOT_COMPLETED`、手动改时间/时区、应用更新和精确闹钟权限状态改变后，从本地快照/数据库恢复未来提醒与进行中的持续通知；另设每天 00:05 的维护 Alarm，跨天后兜底清理过期重复实例并更新 Widget；若已有精确闹钟能力则精确调度，否则使用非精确 Alarm，受系统省电策略影响可能延迟。App 回到前台也会校准。
- 系统闹钟：通过官方 `AlarmClock.ACTION_SET_ALARM` 交给系统时钟应用；重复星期经 `EXTRA_DAYS` 传递，App 保留 15 分钟预提醒但不重复正式声振。进入时先显示完整目标日期，要求在 HyperOS 时钟中人工核对。
- 提醒可靠性：分别检查预提醒、正式提醒、持续开始与持续常驻四个必要渠道，支持直达单通道设置；升级时自动清理旧版重复渠道，同时展示持久化的最近注册/触发/投递异常，从设置返回后自动刷新。
- 番茄钟：15/25/30/45/60 分钟专注预设、5/10/15/20 分钟休息预设、自定义时间、开始/暂停/继续/重置/结束、完成通知；普通开始自动进入沉浸转场，锦鲤严格按真实计时进度绕玉青圆环一圈，暂停即冻结、继续续走、重置/完成归位，尾随水波仅在运行时流动并逐渐消散。可启用跨 App 悬浮锦鲤环，保留鱼体、水纹与倒计时特效，支持自由拖动和双击结束、暂停/继续或隐藏。
- 完全专注：进入前设置并确认六位数字 PIN；全屏仅保留主题背景、居中锦鲤圆环和倒计时，控制折叠在右上角；退出前两次劝返，第三次校验 PIN；PIN 仅保存随机盐与 PBKDF2 校验值，不保存明文。
- 完全专注勿扰：进入时可选择官方勿扰模式；需要用户授予 Notification Policy Access，退出或专注/休息倒计时结束后撤销墨待启用的规则。
- 习惯记忆：专注/休息时间、自定义值、上次提醒类型、系统闹钟偏好、主题、排序和 Widget 行数等保存到 DataStore；不复制标题、备注或具体日期等一次性内容。
- 日历：紧凑两行月视图、上月/下月/今天、公历与离线农历、传统节日、二十四节气、已收录年份的国务院正式休班安排及日期任务列表；日期格不显示任务数量。
- 特殊日期：生日/纪念日支持一次、公历年度、农历年度与闰月回退；可选提前五天自动生成当天可见的小时确认待办。
- Widget：原生可缩放桌面组件，放大标题/时间/备注字号后按高度安全显示 1/2/4/6 行，宽高足够时展示备注；每行提供“完/完成”快捷按钮，首次点击进入二次确认，确认后才完成任务并同步取消提醒。跨日任务按真实覆盖日期纳入今日列表；主题变化、跨天后每日维护、手动改时间和时区变化时刷新。
- 视觉：统一“水墨山水”背景与连续云雾层；待办为山、日历为月、番茄钟为水、已完成为雾、新建为纸、提醒为朱砂、设置为石；锁屏/解锁确认页复用桌面 Widget 的冷蓝山水图；底栏使用山脊、月牙、水纹、雾团微型意象；桌面 adaptive icon 使用同源青黛群山与云海，并提供 monochrome 主题图标层。
- 平板：600dp+ 确认卡片居中限宽；主内容与底栏在大屏上居中控制阅读宽度；日历和平板横屏番茄钟使用左右双栏，竖屏保持单栏，表单在低高度横屏下仍可滚动到操作区。
- 深色模式：主页、系统栏、锁屏/解锁正式提醒、Widget 与完成确认页同步适配；Widget 使用加深山水雾幕和高对比文字，不以纯黑遮掉意境。

## HyperOS 提醒配置

App 内进入“提醒可靠性”页面可查看实时状态和“去开启”入口。推荐按下列顺序处理：

1. 通知权限：允许通知，并在 App 的逐渠道检查中确认预提醒、正式强提醒、持续开始提醒和持续常驻通知。
2. 精确闹钟：允许“闹钟和提醒”特殊权限；未开启时 App 会使用官方非精确降级 API，但到点精度不受保证。
3. 全屏强提醒：允许全屏提醒能力；系统不允许时仍保留高优先级 heads-up、声音、振动和锁屏通知。
4. 锁屏与悬浮通知：在 HyperOS 通知设置中允许锁屏通知和悬浮通知。
5. 自启动：安全中心 → 应用管理/授权管理 → 自启动管理 → 墨待。
6. 电池策略：应用详情 → 省电策略/电量与性能 → 墨待 → 无限制。
7. 后台运行：允许后台活动，不要在系统清理工具中冻结墨待。
8. 系统闹钟：确认 HyperOS 时钟能够响应创建闹钟；系统要求时由用户在时钟界面确认。

不同 HyperOS 版本的设置组件名称会变化。App 会优先打开官方或已知稳定页面；无法解析时退回墨待的系统应用详情页，并显示需要手动确认的项目。完整说明见 [HyperOS 提醒可靠性配置](docs/HYPEROS_SETUP.md)。

## 架构

- Single Activity + Jetpack Compose + Compose Navigation
- MVVM + Repository；跨数据库/提醒/Widget 的命令由 `TaskCommandService` 协调
- 手动 `AppContainer` 依赖注入，避免大型 DI 框架与全局散乱实例
- Room 保存任务与提醒；DataStore 保存偏好和番茄钟真实状态
- Coroutines + StateFlow 驱动异步数据与 UI 状态
- AlarmManager → BroadcastReceiver → NotificationController，完全独立于 MainActivity、Compose 和 ViewModel 生命周期
- 原生 RemoteViews Widget，避免高耗电动态背景

### 主要 Package

```text
com.inktodo.app/
├─ core/
│  ├─ alarm/          Alarm 规划、调度、接收、重启恢复、番茄钟 Alarm
│  ├─ database/       Room Entity、DAO、Database、Migration
│  ├─ datastore/      用户偏好与番茄钟持久状态
│  ├─ notification/   预提醒、正式提醒、持续提醒、番茄钟通知
│  ├─ ui/             InkTheme、Design System、背景、公共组件与动效
│  └─ widget/         Widget Provider、尺寸策略、内容与刷新
├─ feature/
│  ├─ todo/           待办数据、命令、ViewModel、页面
│  ├─ calendar/       月视图与日期任务
│  ├─ pomodoro/       真实时间状态机、持久化协调与页面
│  ├─ completed/      已完成列表
│  └─ settings/       权限检测、系统设置导航与页面
└─ navigation/        四个底栏入口与编辑/设置导航
```

## 数据库

- 数据库：`ink_todo.db`
- Room version：6
- 核心表：`tasks`、`reminders`、`special_dates`
- Schema 导出已开启。
- `MIGRATION_1_2` 无损增加持续提醒手动结束状态字段。
- `MIGRATION_2_3` 无损保留完整起止 epoch 时间，并增加结束时间索引；原有持续任务无需复制或破坏数据即可表示跨日期区间。
- `MIGRATION_3_4` 无损增加重复规则与系列标识；旧任务默认不重复。
- `MIGRATION_4_5` 无损增加可选的持续时段间隔分钟数；旧任务默认不启用间隔弹窗。
- `MIGRATION_5_6` 无损增加特殊日期来源字段、幂等索引和 `special_dates` 表；旧任务与偏好保留。
- 不使用 `fallbackToDestructiveMigration`，升级不能以删除用户任务作为默认方案。

## 提醒实现

1. Task 保存为数据库实体与可恢复提醒快照。
2. `ReminderPlanner` 根据真实 `Instant` 生成预提醒、正式提醒、区间开始/结束计划。
3. `AndroidReminderScheduler` 先取消该任务全部旧 PendingIntent，再注册新 Alarm。
4. 具备精确权限时使用 `setExactAndAllowWhileIdle(RTC_WAKEUP)`；否则使用 `setAndAllowWhileIdle` 并在 UI 明确提示可靠性下降。
5. `ReminderAlarmReceiver` 在 App 页面未打开、进程原先不存在时仍可接收系统 Alarm。
6. `NotificationController` 分离预提醒、正式强提醒、待确认静默恢复、持续开始声振和无声常驻渠道；正式音效直接由高优先级通知渠道播放，避免服务内独立播放失败或影响蓝牙音频。解锁状态由 `FormalReminderOverlayService` 主动展示确认框，锁屏状态由 `FormalReminderActivity` 确认。
7. 长持续通知每 6 小时静默续期；重启、每日维护 Alarm、手动改时间/时区、应用更新、精确权限重新授予或 App 回前台后由恢复协调器校准未来提醒。
8. Device Protected 健康记录持久保存计划、触发、提交系统、失败和丢弃原因；诊断记录失败不会阻断真实提醒。

## Widget 实现

- `InkTodoWidgetProvider` 使用 `goAsync()` + IO Coroutine 读取今天未完成任务。
- `WidgetSizePolicy` 根据桌面实际报告的最小宽高重排内容，不只是放大字体。
- 小尺寸优先显示任务时间、标题和“完”按钮；足够宽高后增加备注并将按钮显示为“完成”。
- 数据 CRUD 和主题偏好变化通过 `WidgetUpdater` 触发刷新；跨天后由每日维护 Alarm 刷新（精确能力不可用时可能延迟），手动改时间/时区广播也会刷新；明确浅色/深色时 RemoteViews 显式套用对应资源，“跟随系统”保留 night qualifier。任务正文用不可变 PendingIntent 打开详情，完成按钮打开非导出的透明二次确认 Activity，确认后复用 `TaskCommandService` 完成任务。

## 已知系统限制

1. Android 官方 API 不允许 App 定义“只有长按通知后才出现某个自定义按钮”。Android 14 也允许用户划掉部分 `ongoing` 通知；墨待收到删除回调会重发，持续期间每 6 小时校准，点击通知可进入任务详情安全结束。系统仍可能在其间暂时移除通知，无法保证绝对不可划掉。
2. Android 13+ 在手机解锁使用中会把 Full Screen Intent 改成扩展 heads-up，这是系统固定行为；直接覆盖当前页面需要用户额外授予官方“显示在其他应用上层”特殊权限。锁屏仍使用 Full Screen Intent；两种权限都未获准时退化为不可普通滑动删除的高优先级通知。
3. HyperOS 自启动、锁屏悬浮通知和部分电池策略没有统一、可完整查询的公开 API；App 能检测官方能力并提供入口，但部分 HyperOS 项只能由用户人工确认。
4. `AlarmClock.ACTION_SET_ALARM` 是否直接创建、是否弹出确认，由 HyperOS 时钟决定；该官方协议没有具体年月日参数，因此 App 会显示完整目标日期并要求用户在时钟页核对。系统闹钟启用后墨待不重复正式声振。
5. 用户若强行停止 App、禁用通知分类、冻结应用或撤销精确闹钟权限，Android 会限制后台接收与精度；重新打开 App 后恢复协调器会再次校准可恢复状态。
6. ongoing 通知在 Android 官方语义下用于防误滑，并不意味着系统管理员/用户绝对无法停止应用或关闭通知。
7. targetSdk 35+ 下，Android 不允许普通 App 任意改写全局勿扰状态；官方 `setInterruptionFilter` 会启停由墨待关联的专注模式规则。用户必须在系统页面授权；若撤销权限或强行停止 App，系统可能阻止墨待及时撤销该规则，需要用户在系统勿扰设置中手动关闭。
8. 六位 PIN 用于降低冲动退出，不是设备级锁定：Android 用户始终可以离开 App、强行停止或清除应用数据。墨待不使用无障碍、设备管理员或锁任务 Hack 阻止系统级操作。

## 构建与验证

环境：JDK 17、Android SDK 36、Gradle Wrapper 8.14.3、AGP 8.13.2、Kotlin 2.3.21。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
.\gradlew.bat :app:assembleRelease :app:lintRelease
```

最终验证：

- Debug/JVM/Lint 联合门禁通过，AndroidTest APK 编译通过。
- Release R8 + Resource Shrinking + Lint 门禁通过，无 Kotlin 元数据兼容警告。
- 上一稳定基线曾在 Redmi K70 至尊版完成 23/23 instrumentation；本次跨日期、锦鲤、图标与完全专注增量的最新设备运行验收需在手机重新连接后执行，不将“APK 已编译”冒充“真机已通过”。
- Widget Provider 在 HyperOS AppWidgetService 中注册。

## 后续扩展建议

- 在不改变数据库兼容性的前提下增加显式导出/导入备份。
- 使用用户自有 Release keystore 建立可长期归档的正式签名流程。
- 对不同 HyperOS 大版本建立小规模实机兼容矩阵，记录通知渠道、全屏权限和时钟确认差异。
- 继续以静态山水 + 低频云雾为主，只有番茄钟使用较明显水波；避免为视觉效果牺牲提醒可靠性和耗电。

