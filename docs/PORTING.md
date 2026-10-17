# 版本兼容与移植笔记（aetm）

> 工程内部的兼容/移植参考，不是玩家文档。最后更新：2026-10-06（对应 aetm 1.0.4）。
> 每条结论都标了"实际依赖点"，移植时按它逐个核。

## 1. 当前基线

| 项 | 值 |
|---|---|
| Minecraft | **1.19.2** |
| Forge | **43**（编译 43.5.0，整合包实机 43.4.12） |
| AE2 | **12.9.9**（编译依赖 = `libs/appliedenergistics2-forge-12.9.9.jar`） |
| Java / mappings | 17 / official |
| 可选依赖 | `ae2wtlib 12.9.7`（通用终端 WUT 集成）、JEI 11.39、Jade 8.9 —— 全是 `compileOnly`，缺失即降级 |

⚠️ **`forge_version_range` 必须写 `[43,44)`**：写死小版本会被整合包实机的 43.4.12 拒绝启动（`ModSorter: Missing or unsupported mandatory dependencies`）。`gradle.properties` 是单一事实来源。

## 2. 在 1.19.2 内适配不同 AE2 12.x（升级时逐项核）

| 我们依赖的面 | 代码位置 | 12.x 内稳定性 / 注意 |
|---|---|---|
| `MenuTypeBuilder.create/build` | `menu/` | 稳定。`build()` **自己注册菜单**（`ae2:` 命名空间），别再挂 `DeferredRegister<MenuType>`（会 `registered twice`） |
| `AEBaseScreen` 的 `drawBG` / `drawFG` | `client/gui/` | 稳定（`renderBg`/`renderLabels` 是 final，只能覆写这两个）；`drawFG` 里 pose **已平移到面板左上角** → 用面板相对坐标 |
| `WidgetContainer` / `addToLeftToolbar` / `Scrollbar` | `client/gui/` | 工具栏必须在**构造器**里加（`init()` 里才 `populateScreen`） |
| `@GuiSync` / `registerClientAction` / `sendClientAction` | `menu/` | 键名在类层次内必须唯一；只支持标量/String/枚举/`GenericStack`/`PacketWritable` record —— **重映射后若新增字段，先核对键号** |
| 屏幕样式 JSON（`assets/ae2/screens/*.json`） | `resources` | `includes` 多/少都崩（症状：服务端开菜单、客户端无屏幕、日志无痕）；`background` **必须带 `srcRect`** |
| `PartTooltipProviders.bodyProviders`（反射摘除 AE2 的 provider） | `client/MonitorTooltipLockFix` | **内部实现**，AE2 改动即失效 —— 失败必须静默降级（当前就是） |
| `IGrid#getMachineClasses` / `getActiveMachines` | `menu/ThroughputTerminalMenu` | 稳定；必须 `isAssignableFrom` 遍历，直接按基类枚举会漏派生类 |
| `AbstractMonitorPart` / `IMonitorWatcher` / `TickingRequest` | `Parts/`、`Logic/` | `TickingRequest(min,max)` 初始速率 `(min+max)/2`、`FASTER=-2`、`SLOWER=+1`、`clamp` 只在 `setCurrentRate` |
| `GridLinkables` / `WirelessTerminalItem` | `Items/` | 稳定 |
| `de.mari_023.ae2wtlib.*`（WUT） | `xmod/ae2wtlib/` | **全部隔离在这 4 个文件里**，其它代码不得 import（没装 ae2wtlib 时这些类不可解析） |

### 2.1 升级 AE2 小版本的操作顺序

1. 把**玩家实机跑的那个 AE2 jar** 复制进 `libs/`，记 sha256（编译依赖必须等于实机依赖）。
2. `build.gradle` 的 `fg.deobf` 坐标改成新 jar。
3. `.\gradlew.bat compileJava --offline` —— 编译错误会直接指出 API 变更点。
4. 反射点自检：`MonitorTooltipLockFix`（tooltip provider 列表）、`Parts/MonitorRefs`（跨 mod 部件桥）—— 这两处失败时只降级，不会崩，但会**静默丢功能**，必须人工确认一次。
5. 实机：① 监控器面板读数/颜色；② 终端列表与子窗口曲线；③ 双击开窗/拖动/定位高亮。

## 3. 移植到 1.20.1

| 变更面 | 影响与本项目对策 |
|---|---|
| 渲染：`PoseStack` → **`GuiGraphics`** | `client/gui/*` **全部要重写**（`fill`/`drawString`/`blit` 签名全变）。`ThroughputChart` 的 Tesselator 路径也要换成 `GuiGraphics` 版本 |
| AE2 12.x → **15.x** | menu/screen/style/`@GuiSync`/part API 需按 15.x 重核；样式 JSON 结构可能调整 |
| Forge 43 → **47** | `mods.toml` 形态不变；`FMLJavaModLoadingContext` 等弃用项按 47 处理 |
| 数据包路径 | 仍是**复数**：`data/<ns>/recipes/`、`advancements/`（1.21 才改单数） |
| 可原样复用 | `Logic/ThroughputHistory`、`Logic/TerminalSettings(Nbt)`、网络包协议与行合并逻辑（纯逻辑，零 GUI 依赖） |

## 4. 移植到 1.21.x（NeoForge）

| 变更面 | 说明 |
|---|---|
| loader | Forge 线止于 1.20.x → 1.21 只能 **NeoForge**（或 Fabric） |
| 元数据 | `neoforge.mods.toml`；依赖块写法与 1.19 不同 |
| **数据包路径改单数** | `data/<ns>/recipe/`、`advancement/`、`tags/item/`、`tags/block/`、`loot_table/`……（本项目现有 4 个 recipe + 4 个 advancement 需迁移并重命名） |
| 渲染 | `GuiGraphics` 延续；`RenderType`/`RenderSystem` 常量有调整 |
| AE2 | 19.x 系列（以目标版本实际为准） |
| 注册 | `DeferredRegister` 保留但事件与字段有变 |

## 5. 关于 "26.2"

Minecraft 官方目前没有 `26.2` 这个版本号（1.21.x 之后是 1.21.4/1.21.5… 等）。如果你指的是某个具体目标版本，把真实版本号填进 §6 的矩阵，我按那个版本的 API 再补细节 —— 现在不做凭空推测。

## 6. 目标矩阵（待填）

| 目标 | MC | loader | AE2 | Java | 状态 |
|---|---|---|---|---|---|
| 当前 | 1.19.2 | Forge 43 | 12.9.9 | 17 | ✅ 已发布 |
| 目标 A | 1.20.1 | Forge 47 | 15.x（实际为准） | 17 | ⬜ 未开始 |
| 目标 B | 1.21.1 | NeoForge 21.1.x | 19.x（实际为准） | **21** | ⬜ 未开始（本机只有 JDK 17，需 Gradle toolchain 自动下载） |

## 7. 移植检查清单（每个目标版本逐项打勾）

- [ ] `gradle.properties`：`minecraft_version` / loader 版本 / `mappings` / `mod_version` 递增
- [ ] `build.gradle`：插件、依赖坐标、`fg.deobf` / `neoForge` 块
- [ ] `mods.toml`（或 `neoforge.mods.toml`）：`loaderVersion`、`[[dependencies]]`、可选依赖 `mandatory=false`
- [ ] 资源路径：`recipes/` vs `recipe/`、`advancements/` vs `advancement/`
- [ ] 样式 JSON：`includes`、`srcRect`、`textureWidth/Height` 与真实 PNG 尺寸一致
- [ ] GUI：所有绘制调用迁到目标 API（`GuiGraphics`）
- [ ] 反射点复核：tooltip provider、`Parts/MonitorRefs`、`xmod/ae2wtlib` 隔离是否仍然成立
- [ ] `libs/` 里换成目标版本的实机 jar，并记录 sha256
- [ ] 实机：面板读数 → 终端列表 → 子窗口曲线 → 双击/拖动/定位 → WUT（若装 ae2wtlib）
