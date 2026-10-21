# aetm 1.19.2 → 1.20.1 移植日志（第一阶段：能编译；第二阶段见文末）

- 目标：MC **1.20.1** / Forge **47.4.10** / AE2 **15.4.10**（`libs/appliedenergistics2-forge-15.4.10.jar`）
- 来源分支：`aetm-1.19.2`（MC 1.19.2 / Forge 43 / AE2 12.9.9）
- 第一阶段只做 **`gradlew compileJava` 通过**：不做 GUI、不实机、不部署、不推送。
- 权威参考：`reference/AE2-1.20.1-15.4.10/src/main/java`（AE2 15.x API）、
  `reference/AdvancedAE-1.20.1-1.3.4/src/main/java`（AAE，仅用于确认监控器 API 与物品 id）。

## 0. 验证结果（本机实测）

```
> Task :compileJava
BUILD SUCCESSFUL in 8s
2 actionable tasks: 2 executed
```

- `gradlew compileJava`（首次）：**BUILD SUCCESSFUL**
- `gradlew --offline clean compileJava`：**BUILD SUCCESSFUL in 8s**（依赖已全部落缓存，可离线构建）
- `gradlew --offline build`：**BUILD SUCCESSFUL in 11s** → `build/libs/aetm-1.0.12.jar`（含 `reobfJar`，
  `mods.toml` / `pack.mcmeta` 的 `${...}` 占位符均已正确展开）


---

## 1. 构建文件

| 文件 | 改动 |
|---|---|
| `gradle.properties` | `minecraft_version=1.20.1`、`minecraft_version_range=[1.20.1,1.21)`、`forge_version=47.4.10`、`forge_version_range=[47,)`、`loader_version_range=[47,)`、`mapping_channel=official`、`mapping_version=1.20.1`；新增 `ae2_version=15.4.10` |
| `build.gradle` | AE2 改为 `compileOnly fg.deobf("blank:appliedenergistics2-forge:${ae2_version}")`（`flatDir { dirs 'libs' }`）；删除 `appeng:...:12.9.9`、jade、jei、AE2WTLib、architectury、cloth-config、curios 全部依赖与 `Modmaven` 仓库 |
| `mods.toml` | `ae2` 依赖区间 `[12.9.9,13.0.0)` → **`[15.4.10,16)`**；删除 `ae2wtlib` 软依赖段 |
| `pack.mcmeta` | `pack_format` 9/10 → **15**（resource / data 都是 15） |

Forge 版本用 **47.4.10**（本机 `~/.gradle/caches/forge_gradle/maven_downloader` 里已有该版本，
用户允许"本机缓存里可用的 47.x"；47.2.0 需要额外联网下载）。

---

## 2. 实际遇到并修掉的 AE2 12.x → 15.x API 差异

每条都给"旧 → 新"，并注明证据文件（`reference/AE2-1.20.1-15.4.10/src/main/java` 下）。

1. **`AbstractMonitorPart#configureWatchers()` 从 private 变 protected**
   - 旧：`private void configureWatchers()`（`AE2-1.19.2-12.9.9/.../reporting/AbstractMonitorPart.java:201`），
     1.19.2 只能反射调用。
   - 新：`protected void configureWatchers()`（`appeng/parts/reporting/AbstractMonitorPart.java:270`），
     现在直接调用，删掉了 `getDeclaredMethod + setAccessible` 整段反射。
   - 文件：`Parts/AdvancedThroughputMonitorPart.java`

2. **`BlockEntityRenderHelper.rotateToFace` 参数变了**
   - 旧：`rotateToFace(PoseStack, Direction face, byte spin)`
     （`AE2-1.19.2-12.9.9/.../client/render/BlockEntityRenderHelper.java:63`）
   - 新：`rotateToFace(PoseStack, BlockOrientation orientation)`
     （`appeng/client/render/BlockEntityRenderHelper.java:54`）
     → 调用点先用 `BlockOrientation.get(getSide(), getSpin())` 合成。
   - 文件：`Parts/ThroughputMonitorPart.java`

3. **`BlockEntityRenderHelper.renderItem2dWithAmount` 从 7 个参数变 9 个**
   - 旧：`(PoseStack, MultiBufferSource, AEKey, long amount, float scale, float yOffset, int color)`
   - 新：`(PoseStack, MultiBufferSource, AEKey, long amount, boolean canCraft, float itemScale, float spacing,
     int textColor, Level level)`（`appeng/client/render/BlockEntityRenderHelper.java:81`）
     → 补 `canCraft()` 与 `getLevel()`。
   - 文件：`Parts/ThroughputMonitorPart.java`

4. **`Font#drawInBatch` 第 8 个参数类型变了**（原版 1.20 的改动，不是 AE2 的）
   - 旧：`..., boolean transparent, int packedLight, int packedOverlay)` —— 传 `false`
   - 新：`..., Font.DisplayMode displayMode, int packedLight, int packedOverlay)` —— 传
     `Font.DisplayMode.NORMAL`（AE2 自己的 `renderItem2dWithAmount` 就是这么写的）
   - 文件：`Parts/ThroughputMonitorPart.java`

5. **创造模式物品栏：1.20 起是注册表对象，`Item.Properties#tab()` 被删除**
   - 旧：`new CreativeModeTab(MODID) { makeIcon() }` + `new Item.Properties().tab(Aetm.TAB)`
   - 新：`DeferredRegister<CreativeModeTab>`（`Registries.CREATIVE_MODE_TAB`）注册，
     内容由 `CreativeModeTab.builder().displayItems((params, output) -> …)` 逐个 `output.accept(...)`。
   - 文件：`Aetm.java`、`Init/ModItems.java`
   - 顺带收益：1.19.2 里"装了 AdvancedAE 时基础监控器不进创造标签"是靠"不给物品挂 `.tab()`"实现的，
     现在变成 `displayItems` 里一句 `if (ModItems.providesBasicMonitor())`。

> 说明：`appeng.menu.*`（`AEBaseMenu(Object host)` 构造、`MenuTypeBuilder.build()`、
> `@GuiSync`、`registerClientAction`）、`TickingRequest`（4 参构造）、`PartItem` 构造、
> `AbstractDisplayPart#selectModel`、`ConfigInventory.configTypes/getKey/setStack/createMenuWrapper`、
> `WirelessTerminalItem(DoubleSupplier, Properties)`、`WirelessTerminalMenuHost(Player, Integer, ItemStack, BiConsumer)`、
> `GridLinkables`、`AEKey#formatAmount/toTagGeneric/fromTagGeneric/writeKey/readKey` 在 12.9.9 与 15.4.10
> **完全一致**，未改动（已逐条对照参考源码）。

6. **`Registry.MENU_REGISTRY` 不存在了（原版 1.20 的改动）** —— **这是第一阶段唯一一个真正的编译错误**
   - 旧：`net.minecraft.core.Registry.MENU_REGISTRY`
   - 新：`net.minecraft.core.registries.Registries.MENU`
   - 报错原文：`CommonEvents.java:31: 错误: 找不到符号  符号: 变量 MENU_REGISTRY  位置: 接口 Registry`
   - 文件：`CommonEvents.java`
   - 同一处顺手加了 `ForgeRegistries.MENU_TYPES.containsKey(id)` 守卫（见下）。

7. **`MenuTypeBuilder.build()` 的注册语义没变，但更值得小心**
   - `build(id)` 会把 type 排进 AE2 的 `InitMenuTypes` 队列（`MenuTypeBuilder.java:173`），
     而该队列只在 AE2 处理 **BLOCK** 注册表事件时被冲刷一次（`AppEngBase.java:149`）。
     我们的菜单类通常在那之后才被类加载，所以队列不会被 AE2 冲刷，必须自己注册一次
     （与 1.19.2 相同）。现在加了 `containsKey` 守卫，顺序真的翻过来时也不会 "Adding duplicate key"。

### 仍然存在的 3 条非阻断告警（`[removal]` 弃用，不影响编译）

| 位置 | 弃用 API |
|---|---|
| `Aetm.java:55` | `FMLJavaModLoadingContext.get()` |
| `Aetm.java:64` | `ModLoadingContext.get()` |
| `network/AetmNetwork.java:32` | `new ResourceLocation(String, String)` |

1.20.1 的替代写法（构造器注入 `FMLJavaModLoadingContext`、`ResourceLocation.tryBuild/fromNamespaceAndPath`）
留到第二阶段一起改，避免第一阶段引入没验证过的改动。


---

## 3. 删除 / 暂时排除的文件

### 3.1 删除（第一阶段砍掉，第二阶段重写）

`src/main/java/com/cheesexiaolv/aetm/` 下：

| 文件 | 行数 | 为什么 | 第二阶段要做什么 |
|---|---:|---|---|
| `client/gui/ThroughputTerminalScreen.java` | 790 | 屏幕：1.19.2 用 `PoseStack` 手绘，1.20.1 是 `GuiGraphics` | 重写（终端主界面：行列表 + 行内按钮 + 折线图 + 浮动子窗口） |
| `client/gui/TerminalWindow.java` | 596 | 浮动子窗口（拖拽 / 贴图化九宫格 / 定位按钮） | 重写（可复用其中的布局常量与交互逻辑） |
| `client/gui/ThroughputChart.java` | 309 | 折线图绘制（坐标轴 / 网格 / 曲线 / 分桶） | 重写（计算部分可直接搬，绘制改 `GuiGraphics`） |
| `client/gui/AdvancedThroughputMonitorScreen.java` | 137 | 高级监控器界面（配置槽 + 读数 + 单位 + 图表按钮） | 重写 |
| `client/gui/FlatButton.java` | 44 | 自绘扁平按钮 | 重写（或改用 AE2 15 的按钮组件） |
| `client/gui/WirelessThroughputTerminalScreen.java` | 22 | 无线终端屏幕（复用终端屏幕） | 重写（随 AE2WTLib 一起） |
| `client/ClientHighlights.java` | 141 | 「定位」的世界描边（`RenderLevelStageEvent` + `LevelRenderer#renderLineBox`） | 重写（1.20.1 事件仍在，签名要核） |
| `client/TerminalClientPackets.java` | 43 | 把自建通道的行数据塞进当前打开的菜单 | 重写，并在客户端初始化里调 `AetmNetwork.setClientHandler(...)` 接回来 |
| `xmod/ae2wtlib/AE2WtLibHook.java` | 222 | AE2WTLib（WUT / 通用终端）集成 | **待补**：1.20.1 的 AE2WTLib 集成 |
| `xmod/ae2wtlib/AE2WtLibPlugin.java` | 57 | 同上（提前占热键 id + 注册 ItemWT） | 同上 |
| `xmod/ae2wtlib/AE2WtLibClientHook.java` | 30 | 同上（客户端屏幕） | 同上 |
| `xmod/ae2wtlib/UniversalThroughputTerminalItem.java` | 55 | 通用终端物品（`IUniversalWirelessTerminalItem`） | 同上 |
| `xmod/ae2wtlib/ThroughputTerminalWirelessMenuHost.java` | 49 | 同上 | 同上 |
| `xmod/ae2wtlib/WutThroughputTerminalScreen.java` | 57 | 同上 | 同上 |

数据/资源：

| 文件 | 为什么 |
|---|---|
| `data/aetm/recipes/wireless_universal_terminal/{upgrade,combine_*}_throughput.*.json`（4 个） | AE2WTLib 的 `ae2wtlib:upgrade` 配方 |
| `data/aetm/advancements/recipes/wireless_universal_terminal/*`（4 个） | 上面那 4 个配方的解锁进度 |
| `tools/gen_wut_recipes.js` | 生成上面这些文件的脚本 |

**保留**（不依赖 GUI，1.20.1 下原样编译）：
`client/ClientTooltipFixer.java`、`client/MonitorTooltipLockFix.java`
—— 它们只用 `appeng.api.integrations.igtooltip.*` 与 `InGameTooltip`，
AE2 15.4.10 里这些类/字段（`PartTooltipProviders.bodyProviders`、`cache`、
`StorageMonitorDataProvider`）**名字都没变**，是零成本的保留项。

### 3.2 JEI / Jade

代码里从来没有直接的 JEI/Jade 引用（1.19.2 只把它们当 `runtimeOnly` 开发环境 QoL），
所以第一阶段就是删掉 `build.gradle` 里那两行 + 不再需要 `libs/` 里的 jar。没有要"桩掉"的 import。

### 3.3 第二阶段工作量粗估

* 屏幕层：约 **2 000 行**（上面 6 个 `client/gui` 文件）+ `ClientEvents` 的屏幕注册 + `TerminalClientPackets`。
  主要成本是 1.20.1 的 `GuiGraphics` 重写（1.19.2 是 `render(PoseStack, int, int, float)` 里自己开
  buffer，1.20.1 是 `render(GuiGraphics, ...)`），图表/窗口的**布局与数值计算可原样搬**。
* `ClientHighlights`：约 140 行，`RenderLevelStageEvent` 在 1.20.1 仍在，主要核 `LevelRenderer#renderLineBox` 签名。
* AE2WTLib 集成：约 450 行 + 4 个配方 + 4 个进度 + lang 键，**需要先拿到 1.20.1 的 AE2WTLib jar**
  才能确定 `IUniversalWirelessTerminalItem` / `ItemWT` / `WUTHandler` 的 1.20.1 形态。

---

## 4. AdvancedAE 反射软桥

### 4.1 连接对象

| 项 | 值 | 证据 |
|---|---|---|
| modid | `advanced_ae` | `advanced_ae:throughput_monitor` 配方 |
| 类名 | `net.pedroksl.advanced_ae.common.parts.ThroughputMonitorPart` | `reference/AdvancedAE-1.20.1-1.3.4/src/main/java/net/pedroksl/advanced_ae/common/parts/ThroughputMonitorPart.java:37` |
| 继承 | `extends AbstractMonitorPart implements IGridTickable` | 同上 |
| 物品 id | `advanced_ae:throughput_monitor`（配置器 `advanced_ae:throughput_monitor_configurator`） | 同 reference 的 `src/generated/resources/data/advanced_ae/recipes/throughput_monitor.json` |

### 4.2 走 AE2 基类（类型安全，不反射）

`getDisplayed()`（`AEKey`，@Nullable）、`getAmount()`、`getBlockEntity()`、`getLevel()`、
`isActive()`、`onPartActivate(Player, InteractionHand, Vec3)`（public，`getMethod` 沿父类链可取）——
前 4 个是 `AbstractMonitorPart` 的 public 方法，包装类先 cast 到 `AbstractMonitorPart` 再读。

### 4.3 必须反射的三处

| 反射目标 | 声明 | 说明 |
|---|---|---|
| 字段 `lastReportedValue` | `protected double`（AAE 源码第 61 行） | 按 `Number` 读，所以 1.19.2 时代 aebackports 的 `protected float` 也兼容；读不到返回 0 |
| 字段 `workRoutine` | `private WorkRoutine`（枚举，第 63 行） | 读 `ordinal()` 当档位号（枚举顺序 0=t 1=s 2=m 3=10m，与 aetm 一致）；读不到按 1（秒档） |
| 方法 `onPartActivate` | `public boolean onPartActivate(Player, InteractionHand, Vec3)` | 供 aetm 的配置器切档位；失败返回 false |

实现细节（`Parts/AaeMonitorRef.java`）：

- 字段/方法句柄**按"零件实际类"懒解析并缓存**（沿父类链 `getDeclaredField`），
  不再像 1.19.2 那样"解析到第一个能加载的类就用它" —— 这样整合包里同时出现 AAE 与旧 id 的 jar 时不会串。
- `MonitorRefs` 会尝试 3 个类名（按顺序）：
  `net.pedroksl.advanced_ae.common.parts.ThroughputMonitorPart`、
  `com.cheesexiaolv.aebackports.Parts.ThroughputMonitorPart`、
  `com.cheesexiaolv.Aaemonitor.Parts.ThroughputMonitorPart`；
  能加载的全部收进列表，`isMonitorClass` / `isInstance` 对**任意一个**命中即认。
- **降级**：`ModList.get().isLoaded(...)` 全为 false → 根本不做 `Class.forName`，桥为空；
  类加载失败 / 字段与方法是解析失败 → 该零件被跳过（`of()` 返回 null），
  终端照常工作，只在第一次记一条 `AELog.warn`。**本包没有任何 `net.pedroksl.*` 的 import**
  （`grep -r "net.pedroksl" src/` 为空）。

### 4.4 "只能有一种吞吐量监控器"

- `Init/ModItems.java`：`PROVIDER_MODIDS = { "advanced_ae", "aebackports", "aaemonitor" }`；
  任一在场 ⇒ `providesBasicMonitor() == false` ⇒ 基础监控器**仍注册**（老存档不炸）但**不进创造标签**。
- `data/aetm/recipes/throughput_monitor.json`：三条 `forge:not{value:{type:"forge:mod_loaded",…}}`
  反条件（`advanced_ae` / `aebackports` / `aaemonitor`）⇒ 对面在场时不生成配方。
- `data/aetm/recipes/advanced_throughput_monitor.json`：4 个条件分支
  （无对面 → `aetm:throughput_monitor + engineering_processor`；
  `advanced_ae` → **`advanced_ae:throughput_monitor + ae2:engineering_processor`**；
  `aebackports` / `aaemonitor` 两条历史分支保留）。
- 注：`advanced_throughput_monitor_direct.json`（`ae2:storage_monitor + engineering_processor`）是
  1.0.0 起就有的"直通配方"，与"基础监控器唯一性"无关，本阶段未动。

---

## 5. 构建 / 网络踩到的坑（本机特有的，换机器可能不出现）

1. **Forge 47.4.10 的 userdev 与 MC 1.20.1 的 client/server jar、mappings 本机已有缓存**，
   但 **ForgeGradle 的 `listLibraries` 步骤要下载 1.20.1 的全部 MC 库**（88 个 artifacts）。
   本机到 `libraries.minecraft.net` / `maven.minecraftforge.net`（Cloudflare）的链路会**长时间挂死**
   （不是失败，是 socket 一直不开腔）：`fastutil-8.5.9.jar`(23 MB)、`icu4j-71.1.jar`(13 MB)
   各自挂 6~10 分钟，CPU 不动、日志不动。
2. **解决手段**（三件）：
   - `gradle.properties` 里加 `org.gradle.internal.repository.max.tentatives=6` 与
     `systemProp.org.gradle.internal.http.socketTimeout=20000` / `...connectionTimeout=20000`
     （让僵死连接 20 秒后放弃并重试，而不是无限等）；
   - 把能在大缓存里找到的 artifact **预置**进 `~/.gradle/caches/forge_gradle/maven_downloader/<maven 路径>`
     （写 `jar` + `.sha1` + `.md5` 三个文件，sha1 必须等于 `version.json` 里 `downloads.artifact.sha1`，
     否则 FG 会删掉重下）。这次靠它补了 `icu4j-71.1` 等 6 个；预置脚本见 `/tmp` 记录
     （`D:\MC_mod\.tmp\seed_mc_libs.ps1`，一次性工具，不入库）；
   - 卡住时**杀掉重跑**：已下载的部分会被沿用，重跑 40 秒就到 `listLibraries`。
3. **`-D...=...` 在 PowerShell 里不能写在任务名之后**：`gradlew.bat compileJava -Dfoo=bar` 会被当成
   "任务 `.gradle...` 不存在"（PowerShell 把 `-Dorg` 拆了）。要么把 `-D` 放在任务名**之前**，
   要么（本工程的做法）直接写进 `gradle.properties` 的 `systemProp.*`。
4. AE2 15.4.10 的 jar 通过 `flatDir`（`libs/`）+ `fg.deobf("blank:appliedenergistics2-forge:15.4.10")`
   反混淆，**不需要联网**、也不需要往 `repositories` 里加 maven.modrinth 之类。
5. 其它坐标一律拉得到：`net.minecraftforge:forge:1.20.1-47.4.10`、`de.oceanlabs.mcp:mcp_config`、
   `official` mappings（1.20.1）全部走 maven.minecraftforge.net，**没有一个坐标是取不到的**，
   只是慢。

---

## 6. 本阶段必然无法验证的东西
- 任何 GUI / 屏幕渲染 / 图表 / 浮动窗口 / 世界描边（相关类已删除）。
- 任何实机行为：零件放置、tick 采样、终端行枚举、菜单打开、无线终端链接与耗电、
  创造标签内容、配方数据包加载、AAE 反射桥在真实 AAE 上的表现。
- 服务端 / 客户端启动（`runServer` / `runClient` 一次都没跑）。
- 数据包版本升级（`pack_format` 15）是否让已有存档里的资源正常加载。

---

# 第二阶段：客户端 GUI 重写 + JEI/Jade/AE2WTLib 集成

- 起点 `45514e1`（第一阶段产物），分支 `aetm-1.20.1`。
- 做法：`git checkout 11e73a0 -- client/ xmod/ data/…/wireless_universal_terminal tools/gen_wut_recipes.js`
  取回 1.19.2 的原文件，然后**只改绘制 API 与签名**；布局常量、行内列位置、子窗口尺寸、
  交互语义一律逐字保留（`TEX_*` / `ICON_X` / `AMOUNT_RIGHT` / `RATE_RIGHT` / `ROW_X/ROW_W` /
  `TITLE_H/TITLE_TEXT_Y/TITLE_BTN_Y` / `SLOT_ROW_Y/SLOT_ROW_H/LOCATE_BTN_Y` / `WIDTH/HEIGHT/CHART_W/H`）。

## 7. 验证结果（本机实测，全部 `--offline`）

```
> Task :build
BUILD SUCCESSFUL in 9s        -> build/libs/aetm-1.0.12.jar（175 KB，123 entries）
```

- jar 内含：`client/gui/*` 11 个类、`xmod/ae2wtlib/*` 6 个类、`data/aetm/recipes` 10 个、
  `data/aetm/advancements` 4 个。
- `runClient --offline`：到主菜单（`Sound engine started` + 全部 atlas + Realms 探测），日志里
  **0** 个 `Missing or unsupported mandatory dependencies` / `NoClassDefFoundError` / mixin 报错；
  进程在到达主菜单后由人工结束（所以 Gradle 报 `runClient FAILED … exit value -1`，是杀进程的痕迹）。
- `runServer --offline`：`Done (13.720s)!`，WUT 的 upgrade/combine 配方解析 0 错误。
- 关键日志（`run/logs/latest.log`，`runClient`）：
  ```
  [Render thread/DEBUG] [mixin/]: Remapping refMap ae2wtlib.mixins.refmap.json using …/output.srg
  [Worker-Main-25/INFO] [Jade/]: Start loading plugin at appeng.integration.modules.jade.JadeModule
  [AE2:C/]: aetm: item.aetm.wireless_throughput_terminal -> in universal terminal: true,
            linkable in security terminal: true (canLink: true), keybind: true
  [Render thread/INFO] [minecraft/SoundEngine]: Sound engine started
  ```

## 8. 客户端绘制 API 迁移表（1.19.2 → 1.20.1）

| 1.19.2 | 1.20.1 | 备注 |
|---|---|---|
| `drawFG(PoseStack, int, int, int, int)` | `drawFG(GuiGraphics, int, int, int, int)` | AE2 15.x 的签名，`renderLabels` 仍是 final 并负责 pose 平移 |
| `drawBG(PoseStack, …, float)` | `drawBG(GuiGraphics, …, float)` | 同上，未平移，自己加 `offsetX/offsetY` |
| `GuiComponent.fill(pose, …)` | `guiGraphics.fill(x1,y1,x2,y2,color)` | |
| `GuiComponent.blit(pose, x,y,u,v,w,h,256,256)` | `guiGraphics.blit(RL, x,y,u,v,w,h,256,256)` | 贴图必须显式给 |
| `AEBaseScreen#bindTexture("guis/x.png")` | **已删除** | 贴图提成 `ResourceLocation(ae2, "textures/guis/…")` 常量 |
| `font.draw(pose, String, x, y, color)` | `guiGraphics.drawString(font, s, x, y, color, false)` | 第 6 参 `false` = 无阴影，与 `Font#draw` 语义一致（默认的 `drawString` 会带阴影） |
| `font.draw(pose, Component, float x, y, color)` | `pose.translate(x, y, 0)` + `drawString(…, 0, 0, …)` | 只为保住浮点位置（居中提示文字），避免 ±1px 偏差 |
| `getBlitOffset()` | **已删除** | `AEKeyRendering.drawInGui` 不再要 z 偏移 |
| `AEStackRendering` | `AEKeyRendering` | `drawInGui(Minecraft, GuiGraphics, x, y, AEKey)` |
| `Button(x,y,w,h,msg,onPress)` | `Button(x,y,w,h,msg,onPress,Button.DEFAULT_NARRATION)` | 6 参构造器删除，只剩 protected 7 参 |
| `renderButton(PoseStack, int, int, float)` | `renderWidget(GuiGraphics, int, int, float)` | |
| `Tesselator` + `BufferUploader.drawWithShader` | **不变** | 曲线仍用 QUADS 细四边形带（vanilla `GuiGraphics#fill` 同一条路径） |

**没变的**：`Scrollbar`（`setHeight/setRange/setVisible/setCaptureMouseWheel/getCurrentScroll` 全同）、
`SettingToggleButton`、`ScreenStyle`、`Blitter`、`WidgetContainer#addScrollBar`、
`InitScreens.register(MenuType, StyledScreenFactory, String)`（仍在 `appeng.init.client.InitScreens`，
内部仍调 `MenuScreens.register`，仍在 `FMLClientSetupEvent#enqueueWork` 里注册）、
`LevelRenderer.renderLineBox(PoseStack, VertexConsumer, AABB, r,g,b,a)`、`RenderType.lines()`、
`RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS`、`AEBaseScreen#setTextContent/drawTooltip`。

**原版 1.20 的注册表重构**：`Registry.DIMENSION_REGISTRY` → `Registries.DIMENSION`，
`Registry.ITEM_REGISTRY` → `Registries.ITEM`（`ClientHighlights`、`AE2WtLibPlugin`）。
**AE2 15.x 的日志 API**：`AELog.log(Level, Throwable, fmt, args)` 删除，带异常只剩
`AELog.error(Throwable, String)`（不带格式参数）。

## 9. AE2WTLib 12.9.7 → 15.3.3（WUT）

- `WUTHandler.addTerminal(...)` 的 **6 参重载语义变了**（`javap -p -c` + 常量池核对）：
  - 旧：第 6 参 = `hotkeyName`；
  - 新：第 6 参 = 终端**显示名的 lang key**，热键名固定自动拼 `"wireless_" + name + "_terminal"`
    （拼接模板 `wireless_\u0001_terminal`），显示名走 `TextConstants.formatTerminalName(key)`。
  - 所以调用改成传 `item.aetm.wireless_throughput_terminal`，热键 id 仍等于 `HOTKEY_ID`
    （提前占位的那个 id 依旧命中，`keybind: true` 是运行期证据）。
- `WTMenuHostFactory.create(Player, Integer, ItemStack, BiConsumer<Player, ISubMenu>)`、
  `WTMenuHost extends appeng.helpers.WirelessTerminalMenuHost`、`getMainMenuIcon()` 均未变。
- `IUniversalWirelessTerminalItem`（15.3.3）**仍要求 `getMenuType(ItemStack)`**
  —— AE2 15.4.10 的 `WirelessTerminalItem.getMenuType()` 是无参版本，不能顶替。
- 15.3.3 的 mandatory 前置（`META-INF/mods.toml`）：`ae2 [15.4.10,16)` / `cloth_config` /
  `architectury` / `curios`，四个都能拿到（见 §10），所以 WUT **完整启用**。
- 配方：`ae2wtlib:upgrade` / `ae2wtlib:combine` 的 JSON 键
  （`terminal`+`terminalName`、`terminalA/B`+`terminalAName/BName`）与 12.9.7 完全一致；
  1.20.1 的 JSON 目录仍是 `data/<ns>/recipes/`（改名 `recipe/` 是 1.21 的事）。
- **配方解析链路实测**（用临时探针配方验证，验证后已删除）：
  - 假序列化器 `aetm:probe_nonexistent_serializer` →
    `Parsing error loading recipe …: Invalid or unsupported recipe type` ⇒ 本模组的
    `data/aetm/recipes/**` 确实被数据包读到；
  - `ae2wtlib:upgrade` + 未注册的 `terminalName` →
    `com.google.gson.JsonSyntaxException: A required attribute is missing or invalid!`
    （`Serializer#validateOutput` 在 `fromJson` 里，名字没登记就抛）⇒ 这条失败链路是活的；
  - 真实 4 条（3×combine + 1×upgrade）**0 错误** ⇒ `throughput` 在数据包加载前就已经登记
    （`FMLCommonSetupEvent#enqueueWork` 早于服务端资源加载）。
- 注意：开发环境的配方计数一直是 **12**（1.19.2 dev 同样 12），说明 dev 只加载了部分数据源；
  这不影响上面的结论（探针证明本模组目录被读），但**不能**用计数判断"某条配方有没有加载"。

## 10. 依赖（`libs/` + `fg.deobf`，全部 `--offline` 可用）

| 用途 | jar | 配置 |
|---|---|---|
| JEI | `jei-1.20.1-forge-15.56.0.205.jar` | `compileOnly` + `runtimeOnly` |
| Jade | `Jade-1.20.1-Forge-11.13.3.jar` | `compileOnly` + `runtimeOnly` |
| WUT | `ae2wtlib-15.3.3-forge.jar` | `compileOnly` + `runtimeOnly` |
| WUT 前置 | `architectury-9.2.14-forge` / `cloth-config-11.1.136-forge` / `curios-forge-5.14.1` | `runtimeOnly` |

- `libs/` 里的 curios jar **改名为 `curios-forge-5.14.1.jar`**（去掉原名的 `+1.20.1`）：
  flatDir + Gradle 会把版本串里的 `+` 当动态版本通配符，解析会失败。
- JEI / Jade 本模组**没有自己的插件类**：集成完全由 AE2 自带的 JEI/Jade 插件完成
  （我们的零件走 `Igtooltip`，即 `client/ClientTooltipFixer`、`client/MonitorTooltipLockFix`），
  这两行依赖的作用是"让开发运行时真的带上它们"，从而验证没有 `NoClassDefFoundError`。
- `mods.toml` 补回 `ae2wtlib` 软依赖段（`mandatory=false`、`[15.3.3,)`、`AFTER`）。

## 11. 第二阶段仍然无法在开发环境验证的东西

- 界面本身：布局 / 贴图对齐 / 折线图形状 / 子窗口拖动与层序 / 「定位」红色描边 ——
  `runClient` 到主菜单只证明"类加载、注册、mixin 没炸"，看不到画面。
- 打开终端/监控器时的样式加载（`StyleManager.loadStyleDoc`）与 `drawFG` 真跑一遍
  —— 要实机放零件、开界面。
- 真机配方/进度解锁：dev 环境数据包不完整（见 §9 末尾），需要在实机整合包里确认
  4 条 WUT 配方出现在配方书里、能合成。

