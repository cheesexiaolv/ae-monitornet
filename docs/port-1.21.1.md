# aetm 1.20.1 → 1.21.1 移植地图（第一阶段：NeoForge 构建骨架）

- 目标：MC **1.21.1** / NeoForge **21.1** / AE2 **19.2.17** / GuideME **21.1.17**
- 源分支：`aetm-1.20.1`（MC 1.20.1 / Forge 47.4.10 / AE2 15.4.10）
- 本阶段只做**准备工作**：`gradlew build` 通过 + 这份地图。**功能不完整**，不可分发（见 §7）。
- 权威参考（不进 git）：
  - `reference/AE2-1.21.1-19.2.17/`（AE2 19.2.17 反编译源码）
  - `reference/GuideME-1.21.1-21.1.17/`（GuideME 21.1.17）
  - `reference/AE2WirelessTerminalLibrary-1.21.1-19.5.1/`（AE2WTLib，仅 `WUTHandler`/`WTMenuHost` 相关）
  - 1.20.1 旧代码原件仍在 `aetm-1.20.1` worktree；本 worktree 里被移出的旧源码在 `legacy/1.20.1-src/`（**gitignore**，仅本机参考）

---

## 0. 验证结果（本机实测，2026-10-22）

> 命令（本 worktree 根目录）：
>
> ```
> set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot
> gradlew.bat build --console=plain
> ```

**结果：`BUILD SUCCESSFUL`**（2026-10-22）。首次成功构建需要先下载 NeoForge 21.1.180 / Minecraft 1.21.1 制品并跑一遍
NeoForm 管线（merge → rename → decompile → inject → patch → jcl 变换 → 重编译，NFRT 自身耗时 **114.7 s**；
整体首次构建约 15–20 分钟，且受本机代理稳定性影响重试了 3 次）。制品落缓存后：

```
> Task :createMinecraftArtifacts UP-TO-DATE
> Task :compileJava UP-TO-DATE
> Task :processResources
> Task :classes
> Task :jar
> Task :assemble
> Task :build

BUILD SUCCESSFUL in 11s
```

`gradlew clean build` 复验：**BUILD SUCCESSFUL in 14s**（5 actionable tasks）。
`gradlew build --offline` 复验：**BUILD SUCCESSFUL in 9s**（制品已全部落缓存）。
产物：`build/libs/ae_monitornet-1.2.0.jar`（82,452 B），`META-INF/neoforge.mods.toml` 占位符已全部展开、
`pack.mcmeta` 为 `pack_format: 34`。

### 0.1 反编译产物与耗时

| 产物 | 目标目录 | 类文件 | 耗时 | 说明 |
|---|---|---|---|---|
| AE2 19.2.17 | `reference/AE2-1.21.1-19.2.17/`（1424 个 `.java`） | 1824 | **12.8 s** | 用 Vineflower 1.10.1（`-dgs=1`），保留包目录后喂入 |
| GuideME 21.1.17 | `reference/GuideME-1.21.1-21.1.17/`（2761 个 `.java`） | 5102 | **25.3 s** | 同上 |
| AE2WTLib 19.5.1 | `reference/AE2WirelessTerminalLibrary-1.21.1-19.5.1/`（75 个 `.java`） | 85 | **1.8 s** | 全量反编译（体积很小，无需只挑类） |

补充：工具 jar 本机原先**不存在**（AGENTS.md 里的 `~/.gradle/.../vineflower-1.10.1.jar` 已被清掉），本轮从 Maven Central 取到
`D:\MC_mod\.tmp\decompile-tools\vineflower-1.10.1.jar`（1.7 MB，校验可用）。解包中间产物在 `D:\MC_mod\.tmp\decompile\`。

### 0.2 `libs/` 依赖清单（都是本机现成 jar，未下载）

| 文件 | 大小 | SHA-256（前 16 位） | 用途 |
|---|---|---|---|
| `appliedenergistics2-19.2.17.jar` | 8,230,896 | `460D779A0609B814` | **硬依赖**：零件/菜单/监控器 API |
| `guideme-21.1.17.jar` | 9,309,396 | `D012ACA7275E2186` | AE2 19.x 的**硬前置**；指南书 |
| `ae2wtlib-19.5.1.jar` | 252,091 | `407E7300351353CB` | 软依赖（WUT 通用终端，第 2 阶段） |
| `jei-1.21.1-neoforge-19.39.0.369.jar` | 1,635,413 | `79B6D034FA233CC8` | 软依赖（本模组无自建插件，仅保证编译/运行期同 jar） |
| `Jade-1.21.1-NeoForge-15.10.6.jar` | 726,853 | `276F1F65A20D04F5` | 软依赖（Igtooltip 提供者，第 2 阶段） |

---

## 1. 依赖矩阵

| 组件 | 版本 | 供应方式 | 备注 |
|---|---|---|---|
| Minecraft | **1.21.1** | ModDevGradle（NFRT 自动下载 + 重命名） | `minecraft_version=1.21.1` |
| NeoForge | **21.1.180** | `neoForge { version = neo_version }`（maven.neoforged.net） | AE2 要求 `[21.1.169,)`；目标整合包 BBB v3.04 实机跑 **21.1.248**，编译期取低版本可保证向上兼容 |
| ModDevGradle | **2.0.107** | Gradle 插件门户 / NeoForged maven | 与 `projects/quench-1.21.1-0.1.0` 同版本（本机已验证可用） |
| Gradle | **8.14.4** | wrapper（`~/.gradle/wrapper/dists` 已有） | 需求是 ≥8.10；8.14.4 **已在本机缓存**，见 §2.1 |
| JDK | **21.0.9**（Microsoft OpenJDK） | `java.toolchain.languageVersion = 21` + foojay | 不改系统 `JAVA_HOME`；构建命令里临时设置 |
| AE2 | **19.2.17** | `libs/`（compileOnly + runtimeOnly） | `neoforge [21.1.169,)`、`guideme [21.1.1,)`、`jade [15.0.0,)` |
| GuideME | **21.1.17** | `libs/` | AE2 硬前置；本模组指南书格式 |
| AE2WTLib | **19.5.1** | `libs/`（compileOnly） | 第 2 阶段；运行期还需要 cloth-config / architectury / curios（未放入 `libs/`） |
| JEI | **19.39.0.369** | `libs/` | 可选 |
| Jade | **15.10.6** | `libs/` | 可选 |

---

## 2. 构建骨架（本阶段实际改动）

### 2.1 Gradle wrapper：保持 8.14.4（与任务书的 8.10 的偏差，附理由）

- 任务书要求“升级到 8.10”。**本 worktree 的 wrapper 原本就已是 8.14.4**（`gradle/wrapper/gradle-wrapper.properties`），
  且该发行版已在本机 `~/.gradle/wrapper/dists/gradle-8.14.4-bin` 缓存里。
- NeoForge 21.1 + MDG 2.x 的要求是 **≥8.8**，8.14.4 满足；`projects/quench-1.21.1-0.1.0`（同机 1.21.1 NeoForge 工程）
  也用 8.14.4。
- 因此**没有**再联网下载 8.10（136 MB）——那只会降低构建确定性。若确实需要固定 8.10，只改
  `distributionUrl` 一行即可（需联网下载）。

### 2.2 文件级改动

| 文件 | 改动 |
|---|---|
| `settings.gradle` | `pluginManagement` 增加 `maven.neoforged.net/releases` + `mavenCentral()`；foojay 解析器 0.7.0 → **0.8.0**（兼容 Gradle 8.14） |
| `build.gradle` | ForgeGradle 6 → **`net.neoforged.moddev` 2.0.107**；`java.toolchain=21`；`neoForge { version; runs{client,server}; mods{...} }`；`libs/*.jar` 用 `compileOnly files(...)`（+ AE2/GuideME 的 `runtimeOnly`）；删除 `fg.deobf`、`reobfJar`、`minecraft{runs}` |
| `gradle.properties` | `minecraft_version=1.21.1`、`neo_version=21.1.180`、`neo_version_range=[21.1.169,)`、`loader_version_range=[1,)`、`mod_version=1.2.0`、`mod_loader` 语义改由 MDG 承担（不再有 `forge_version`/`mapping_*`） |
| `src/main/resources/META-INF/neoforge.mods.toml` | **新文件名**（NeoForge 不读 `mods.toml`）；依赖语法 `type="REQUIRED"/"OPTIONAL"`；`loaderVersion="[1,)"`；`ae2 [19.2.17,)`、`guideme [21.1.1,)` 为 required，`ae2wtlib`/`jei`/`jade` 为 optional |
| `src/main/resources/pack.mcmeta` | 去掉 `forge:*_pack_format`，`pack_format: 15 → 34`（1.21.1 资源包格式；JEI 1.21.1 同为 34） |
| `src/main/resources/META-INF/mods.toml` | **删除**（避免两份元数据混淆） |
| `src/main/java/**` | 45 个文件移出到 `legacy/1.20.1-src/`（gitignore），其中 15 个已移植回来（见 §4） |

### 2.3 关键：AE2 依赖如何接进 NeoForge 构建

NeoForge 1.20.2+ 的生产 jar 直接使用 **Mojang official names**，所以第三方 mod jar **不需要** `fg.deobf`/重映射：

```groovy
compileOnly files("libs/appliedenergistics2-${ae2_version}.jar")
runtimeOnly files("libs/appliedenergistics2-${ae2_version}.jar")
```

`gradlew build` 只解析 compile classpath；已实测 `appeng.*` 全部可解析（本阶段 15 个源文件编译通过即为证明）。

### 2.4 本机网络代理（不写进仓库的机器级配置）

本机 Gradle 的 JVM **不读** Windows 系统代理（WinINET），而 `maven.neoforged.net` 的直连 DNS 被污染
（解析到 `199.59.150.39` → 连接超时）。因此构建前需要让 Gradle 走系统代理 `127.0.0.1:7897`：

- 已写入机器级 `C:\Users\lyf15\.gradle\gradle.properties`：
  `systemProp.http(s).proxyHost/Port=127.0.0.1/7897`、`nonProxyHosts=localhost|127.0.0.1|[::1]`。
- 该文件**不属于仓库**；换机器/关代理时需要删掉或改端口。
- 现象（记录一下，避免下次误判为“依赖不存在”）：
  - 不配代理：`Connect to maven.neoforged.net:443 failed: Connection timed out`
  - 配了代理但代理不稳：`SSLHandshakeException: Remote host terminated the handshake`（重试即可，Gradle 会续传已下载的制品）

---

## 3. API 差异清单（15.4.10 → 19.2.17 + Forge 47 → NeoForge 21.1）

> 每条都给「旧 → 新」+ 证据文件:行号。行号指反编译源码（`reference/...`）或本仓库文件。
> ✅ = 本阶段已改并编译验证；⚠️ = 已确认但属于第 2 阶段；📄 = 资源/数据侧。

### 3.0 基础类型

0. ✅ **`ResourceLocation` 的 public 构造器被删除**
   - 旧：`new ResourceLocation(namespace, path)`（1.20.1 可用；AE2 15.4.10 里有 **209 处**）
   - 新：只有私有构造器，必须用 `ResourceLocation.fromNamespaceAndPath(ns, path)` /
     `ResourceLocation.parse(str)` / `withDefaultNamespace(str)`（AE2 19.2.17 里 `new ResourceLocation` 为 **0 处**）
   - 证据：MC 1.21.1 重命名后的类（NFRT 产物，`javap` 结果）只有 `private ResourceLocation(String, String)` 与
     `fromNamespaceAndPath/parse/withDefaultNamespace/tryBuild/bySeparator`；
     `reference/AE2-1.21.1-19.2.17/appeng/api/ids/AEItemIds.java:199` 用 `fromNamespaceAndPath`
   - 影响：**我方 1.20.1 代码已经全部用工厂方法**（5 处，如 `network/AetmNetwork.java:32`），本阶段无需改动；
     新增代码禁止使用构造器。

### 3.1 模组入口 / 加载器

1. ✅ **`@Mod` 构造器改为 `(IEventBus, ModContainer)`**
   - 旧：`public AeMonitorNet(FMLJavaModLoadingContext context) { context.getModEventBus() }`
   - 新：`public AeMonitorNet(IEventBus modEventBus, ModContainer modContainer)`
   - 证据：`reference/AE2-1.21.1-19.2.17/appeng/core/AppEngBase.java:77`；`FMLJavaModLoadingContext` 在 NeoForge 已删除。
   - 配置注册随之改为 `modContainer.registerConfig(ModConfig.Type.SERVER, SPEC)`（旧 `context.registerConfig`）。

2. ✅ **事件总线类型搬家**
   - `net.minecraftforge.eventbus.api.IEventBus` → `net.neoforged.bus.api.IEventBus`
   - `net.minecraftforge.eventbus.api.SubscribeEvent` → `net.neoforged.bus.api.SubscribeEvent`

3. ⚠️ **`@EventBusSubscriber` 换成 NeoForge 注解**
   - 旧：`@Mod.EventBusSubscriber(modid=..., bus = Mod.EventBusSubscriber.Bus.MOD)`
   - 新：`@net.neoforged.fml.common.EventBusSubscriber(modid = "...", bus = Bus.MOD)`（客户端再加 `value = Dist.CLIENT`，
     `Dist` 在 `net.neoforged.api.distmarker`）
   - 证据：`reference/AE2-1.21.1-19.2.17/appeng/datagen/AE2DataGenerators.java:54-57`（`import ...fml.common.EventBusSubscriber;` + `Bus.MOD`）
   - 影响文件：`CommonEvents`、`ClientEvents`、`PartModelsRegistration` —— 全部第 2 阶段。

4. ✅ **`neoforge.mods.toml`（文件名 + 依赖语法）**
   - 旧：`META-INF/mods.toml`，`[[dependencies.x]] modId=... mandatory=true`
   - 新：`META-INF/neoforge.mods.toml`，`type="REQUIRED"` / `"OPTIONAL"` / `"discouraged"`；`loaderVersion="[1,)"`
   - 证据：AE2 19.2.17 jar 内的 `META-INF/neoforge.mods.toml`（`neoforge [21.1.169,)`、`guideme [21.1.1,)`、`jade [15.0.0,)`）。

5. ✅ **注册表 API 换代**
   - `ForgeRegistries.ITEMS` → `Registries.ITEM`；`DeferredRegister` 在 `net.neoforged.neoforge.registries`
   - `RegistryObject<T>` → `DeferredHolder<R, T>`
   - ⚠️ `net.minecraftforge.registries.RegisterEvent` → `net.neoforged.neoforge.registries.RegisterEvent`；
     `ForgeRegistries.MENU_TYPES.containsKey(...)` → `BuiltInRegistries.MENU.containsKey(...)`（第 2 阶段 `CommonEvents`）

6. ⚠️ **`ModList` / `FMLEnvironment` / `Dist`**
   - `net.minecraftforge.fml.ModList` → `net.neoforged.fml.ModList`
   - `net.minecraftforge.fml.loading.FMLEnvironment` 不变
   - `net.minecraftforge.api.distmarker.{Dist,OnlyIn}` → `net.neoforged.api.distmarker.{Dist,OnlyIn}`

### 3.2 零件 / 监控器 API

7. ✅ **`IPart#onPartActivate` 被拆掉（影响交互路径）**
   - 旧：`boolean onPartActivate(Player player, InteractionHand hand, Vec3 pos)`
   - 新：`boolean onUseItemOn(ItemStack heldItem, Player player, InteractionHand hand, Vec3 pos)` +
     `boolean onUseWithoutItem(Player player, Vec3 pos)`
   - 证据：`reference/AE2-1.21.1-19.2.17/appeng/api/parts/IPart.java:126,130`；
     监控器实现：`appeng/parts/reporting/AbstractMonitorPart.java:167`
   - 用法变化：手持物品由“自己 `player.getItemInHand(hand)` 再判”改为**显式参数** `heldItem`。
   - 影响：`ThroughputMonitorPart`（✅ 已改）、`ThroughputMonitorConfigurator`（✅ 调 `own.onUseItemOn(...)`）、
     `AaeMonitorRef` 的反射方法名与参数（✅ 改为 `onUseItemOn(ItemStack, Player, InteractionHand, Vec3)`）。

8. ✅ **`TickingRequest` 去掉 `canBeAlerted`**
   - 旧（AE2 15.4.10）：record `(minTickRate, maxTickRate, isSleeping, canBeAlerted, initialTickRate)`，
     4 参构造器 `(min, max, sleeping, canBeAlerted)`；`reference/AE2-1.20.1-15.4.10/src/main/java/appeng/api/networking/ticking/TickingRequest.java`
   - 新（AE2 19.2.17）：record `(minTickRate, maxTickRate, isSleeping, initialTickRate)`，3 参构造器 `(min, max, sleeping)`；
     证据 `reference/AE2-1.21.1-19.2.17/appeng/api/networking/ticking/TickingRequest.java:5-7`
   - 影响：`ThroughputMonitorPart#getTickingRequest`（✅ 去掉第 4 个 `true`）；`alertDevice()` 仍然存在
     （`appeng/api/networking/ticking/ITickManager.java:7`），所以 `cycleRoutine()` 的“立刻采样”逻辑不变。

9. ✅ **零件 NBT / 流序列化签名**
   - 旧：`void writeToNBT(CompoundTag)` / `void readFromNBT(CompoundTag)`；`void writeToStream(FriendlyByteBuf)` /
     `boolean readFromStream(FriendlyByteBuf)`
   - 新：`writeToNBT(CompoundTag, HolderLookup.Provider)` / `readFromNBT(CompoundTag, Provider)`；
     `writeToStream(RegistryFriendlyByteBuf)` / `readFromStream(RegistryFriendlyByteBuf)`
   - 证据：`appeng/parts/reporting/AbstractMonitorPart.java:83,94,103,115`；`appeng/api/parts/IPart.java:54,57,88,95`
   - 影响：`ThroughputMonitorPart`（✅ 全部改签名）。`writeVisualStateToNBT/readVisualStateFromNBT` 仍是
     `(CompoundTag)`（`AbstractMonitorPart.java:134,141`），不用改。

10. ✅ **`AEKey` 的通用 NBT 序列化需要 registry lookup**
    - 旧：`AEKey.fromTagGeneric(CompoundTag)` / `key.toTagGeneric()`
    - 新：`AEKey.fromTagGeneric(HolderLookup.Provider, CompoundTag)` / `key.toTagGeneric(HolderLookup.Provider)`
    - 证据：`reference/AE2-1.21.1-19.2.17/appeng/api/stacks/AEKey.java:109,120`
    - 影响：`TerminalSettingsNbt#writeOverrides/readOverrides`（✅ 增加 `HolderLookup.Provider` 参数；
      调用方传 `level.registryAccess()` / `player.level().registryAccess()`）。

11. ✅ **`PartItem` / `AbstractDisplayPart` 基本没变（好消息）**
    - `PartItem` 构造器仍是 `(Properties, Class<T>, Function<IPartItem<T>, T>)`，类仍 `extends AEBaseItem implements IPartItem<T>`：
      `appeng/items/parts/PartItem.java`
    - `MODEL_BASE` / `MODEL_STATUS_OFF|ON|HAS_CHANNEL` 仍是 `AbstractDisplayPart` 的 protected static：
      `appeng/parts/reporting/AbstractDisplayPart.java:10-16`
    - `AbstractMonitorPart(IPartItem<?>, boolean)` 未变：`appeng/parts/reporting/AbstractMonitorPart.java:43`
    - 结论：`ModItems` 的 5 个 `PartItem` 注册写法可以原样保留（本阶段只先接回 `throughput_monitor`）。

12. ⚠️ **`PartModels.registerModels` 仍存在，但注册时机要复核**
    - API 未变：`registerModels(Collection)` / `registerModels(ResourceLocation...)`；
      冻结后抛 `IllegalStateException("Cannot register models after the pre-initialization phase!")`
      （`appeng/api/parts/PartModels.java:22-33`）
    - 我方 `client/PartModelsRegistration` 在 `FMLClientSetupEvent` 里注册——1.20.1 可用，1.21.1 需实机验证
      （AE2 侧 `InitAdditionalModels` 在模型事件里 `freeze()`，顺序应与 1.20.1 相同）。第 2 阶段处理。

### 3.3 菜单 / 界面

13. ⚠️ **屏幕注册时机改为 `RegisterMenuScreensEvent`**
    - 旧：`FMLClientSetupEvent#enqueueWork` 里调 `InitScreens.register(type, factory, stylePath)`
    - 新：`InitScreens.init(RegisterMenuScreensEvent)` 内部 `InitScreens.register(event, type, factory, stylePath)`；
      `RegisterMenuScreensEvent` 在 `net.neoforged.neoforge.client.event`
    - 证据：`reference/AE2-1.21.1-19.2.17/appeng/init/client/InitScreens.java:84,93,137`
    - 影响：`ClientEvents`（第 2 阶段）。

14. ⚠️ **`MenuTypeBuilder` 包路径/`build(String)` 不变，但 `create()` 多了一个重载**
    - `appeng.menu.implementations.MenuTypeBuilder`，`create(MenuFactory, Class)` 与 `create(TypedMenuFactory, Class)`
      两个重载并存（`MenuTypeBuilder.java:44-52`）；`build(String id)` 仍在（`:130`）
    - 我方现有写法 `MenuTypeBuilder.create(ThroughputTerminalMenu::new, ThroughputTerminalPart.class)` 可能因为
      重载二义性需要显式 cast —— 第 2 阶段编译时确认。
    - `build(id)` 仍会把菜单注册进 **AE2 的命名空间**（既有行为，见 1.20.1 的 `CommonEvents` 注释）。

15. ⚠️ **`AEBaseMenu` / `GuiSync` / `SlotSemantics` / `FakeSlot` 未变**
    - `AEBaseMenu(MenuType<?>, int, Inventory, Object host)`：`appeng/menu/AEBaseMenu.java:87`
    - `@GuiSync(short value)`：`appeng/menu/guisync/GuiSync.java`
    - `appeng/menu/SlotSemantics`、`appeng/menu/slot/FakeSlot` 路径不变
    - 结论：三个菜单类（第 2 阶段）主要是 import 与 `ResourceLocation`/`RegistryFriendlyByteBuf` 层面的机械改动。

16. ⚠️ **`AEBaseScreen` / `GuiGraphics`：泛型形状不变，绘制 API 只增不减**
    - 证据：`appeng/client/gui/AEBaseScreen.java:94`（`extends AbstractContainerScreen<T>`）
    - 但 19.2 的 `AEBaseScreen` 依赖 GuideME 的 `guideme.*` 类型（`AEBaseScreen.java:44-52`）——编译 classpath
      必须带上 `guideme` jar（已在 `build.gradle` 的 `compileOnly` 里）。
    - `GuiGraphics.blit(ResourceLocation, x, y, u, v, w, h)` 7 参版本在 1.21.1 仍可用：
      `appeng/client/gui/me/patternaccess/PatternAccessTermScreen.java:451`；我方界面只用 `fill`/`drawString`，
      所以 GUI 绘制入口基本不用改。

### 3.4 网络 / 配置 / 事件

17. ⚠️ **自建网络通道必须整段重写（Forge `SimpleChannel`/`EventNetworkChannel` 已被删除）**
    - 旧：`NetworkRegistry.ChannelBuilder...eventNetworkChannel()` + `NetworkEvent.ServerCustomPayloadEvent` +
      `NetworkDirection.PLAY_TO_CLIENT.buildPacket(...)`
    - 新：`CustomPacketPayload` + `StreamCodec<RegistryFriendlyByteBuf, T>` +
      `RegisterPayloadHandlersEvent`/`PayloadRegistrar` 注册 + `PacketDistributor.sendToPlayer(...)`
    - 证据：`reference/AE2-1.21.1-19.2.17/appeng/core/network/InitNetwork.java:40-45`
      （`import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;`、
      `import net.neoforged.neoforge.network.registration.PayloadRegistrar;`、`event.registrar("ae2")`）；
      发送侧：`appeng/client/gui/AEBaseScreen.java:473`（`PacketDistributor.sendToServer(p, ...)`）
    - 影响：`AetmNetwork`（重写）、`TerminalRowsPacket`（改成 payload + codec）、`TerminalClientPackets`（handler 形状改）。
      `TerminalRow` 只是数据 record，可平移。

18. ⚠️ **Forge 配置 → NeoForge 配置**
    - `net.minecraftforge.common.ForgeConfigSpec` → `net.neoforged.neoforge.common.ModConfigSpec`（Builder API 相同）
    - 注册：`ModContainer#registerConfig(ModConfig.Type.SERVER, SPEC)`（`net.neoforged.fml.config.ModConfig`）
    - 本阶段已改 `AetmConfig`（✅）与入口（✅）。

19. ⚠️ **Tick / 渲染事件类全部换包（第 2 阶段客户端）**
    - `net.minecraftforge.event.TickEvent` 在 NeoForge 21.1 已不存在 → `net.neoforged.neoforge.event.tick.{LevelTickEvent,ServerTickEvent}.Pre/Post`
      （`reference/AE2-1.21.1-19.2.17/appeng/hooks/ticking/TickHandler.java:41-42,193`）
    - `net.minecraftforge.client.event.RenderLevelStageEvent` → `net.neoforged.neoforge.client.event.RenderLevelStageEvent`
      （`appeng/client/render/overlay/OverlayManager.java:13`）
    - `net.minecraftforge.client.event.RegisterColorHandlersEvent.Item` → `net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item`
      （`appeng/init/client/InitItemColors.java:19`）
    - 影响：`ClientEvents`、`ClientHighlights`、`ClientTooltipFixer`、`MonitorTooltipLockFix`（第 2 阶段）。

### 3.5 资源 / 数据（📄，不编译但会“静默不生效”）

20. 📄 **1.21 数据包目录改成单数**
    - 旧：`data/<ns>/advancements/...`、`data/<ns>/recipes/...`
    - 新：`data/<ns>/advancement/...`、`data/<ns>/recipe/...`（1.21 数据包格式变更）
    - 证据：AE2 19.2.17 jar 内条目 `data/ae2/advancement/main/facade.json`、`data/ae2/recipe/misc/...json`
    - 影响：本仓库 `data/ae_monitornet/advancements/**`（4 个）与 `data/ae_monitornet/recipes/**`（10 个）需要整体改名目录。

21. 📄 **GuideME 21.x 的指南注册从“资源元数据”变成“代码 builder”**
    - 旧：`assets/<modid>/guideme_guides/guide.json`（`default_language`）+ `assets/<modid>/guides/<ns>/<path>/`
    - 新：`Guide.builder(id).folder("ae2guide").build()`（自动注册）；默认 folder 仍是
      `guides/<namespace>/<path>`（`reference/GuideME-1.21.1-21.1.17/guideme/GuideBuilder.java:43`）
    - 证据：AE2 19.2 客户端 `appeng/core/AppEngClient.java:186-187`
      （`Guide.builder(AppEng.makeId("guide")).folder("ae2guide")`），页面在 `assets/ae2/ae2guide/**`；
      AdvancedAE 1.21.1 同样用 `assets/advanced_ae/ae2guide/**`
    - 我方现状：`assets/ae_monitornet/guides/ae_monitornet/guide/{index.md,_zh_cn/index.md}` —— **布局与默认 folder 规则一致**，
      所以有两种选择：
      1. 保留现布局，注册 `Guide.builder(AppEng.makeId? 不行/用 ResourceLocation.fromNamespaceAndPath("ae_monitornet","guide"))`（默认 folder = `guides/ae_monitornet/guide`）；
      2. 跟随 AE2 生态改到 `assets/ae_monitornet/ae2guide/**` + `.folder("ae2guide")`（推荐：与 AE2/AAE 一致，便于玩家把页面当“AE2 指南书”一部分查）。
    - `guideme_guides/guide.json` 的 `default_language` 改由 `.defaultLanguage("en_us")` 表达，旧文件应删除或确认被忽略。
    - 语言子目录 `_zh_cn` 仍是 GuideME 约定（我方已有），需实机验证。

22. ⚠️ **屏幕样式 JSON / 贴图路径不变，但 AE2 19.2 的控件样式可能有新增键**
    - 我方 `assets/ae2/screens/{throughput_terminal,advanced_throughput_monitor}.json` 仍按 AE2 的 `StyleManager`
      （`assets/ae2/` 命名空间）读取——这条 1.20.1 的硬规则继续生效。
    - 19.2.17 的 `AEBaseScreen` 引入 GuideME 样式（见第 16 条），需在实机上比对 `screens/*.json` 是否有新必填键（第 2 阶段）。

---

## 4. 逐文件移植策略（45 个 java 文件）

图例：✅ 已移植并进入本阶段构建；⏭ 第 2 阶段（可直接改）；🔁 第 2 阶段（需重写）；🧱 第 2 阶段（纯计算，几乎原样）。

| # | 文件（相对 `com/cheesexiaolv/aetm/`） | 行数 | 状态 | 策略 |
|---|---|---|---|---|
| 1 | `AeMonitorNet.java` | 79 | ✅ | 入口：`@Mod(IEventBus, ModContainer)`；`DeferredRegister`/`DeferredHolder`；`ModConfigSpec` 注册 |
| 2 | `AetmConfig.java` | 55 | ✅ | 只换 `ForgeConfigSpec` → `ModConfigSpec` |
| 3 | `CommonEvents.java` | 78 | ⏭ | `@EventBusSubscriber` 换注解；`RegisterEvent`/`BuiltInRegistries.MENU`；`GridLinkables` API 需核对 19.2（`appeng/api/features/GridLinkables`） |
| 4 | `ClientEvents.java` | 78 | ⏭ | 屏幕注册改 `RegisterMenuScreensEvent`；颜色事件换包；WUT 分支留到 xmod 之后 |
| 5 | `Init/ModItems.java` | 132 | ✅（部分） | 本阶段只注册 configurator + throughput_monitor；其余 3 个 id 等第 2 阶段（**不要提前注册半成品 id**） |
| 6 | `Items/ThroughputMonitorConfigurator.java` | 65 | ✅ | `onPartActivate` → `onUseItemOn` |
| 7 | `Items/WirelessTerminalSettings.java` | 78 | ⏭ | `ITerminalSettingsHost` 的物品版；`TerminalSettingsNbt` 增加 registry 参数 |
| 8 | `Items/WirelessThroughputTerminalItem.java` | 49 | ⏭ | 继承 AE2 `WirelessTerminalItem`；19.2 的构造器/`LINKABLE_HANDLER` 需核对 |
| 9 | `Logic/ThroughputCache.java` | 79 | ✅ | 原样（`TickHandler.instance().getCurrentTick()` 未变） |
| 10 | `Logic/ThroughputHistory.java` | 303 | ✅ | 原样（纯 Java） |
| 11 | `Logic/TerminalSettings.java` | 72 | ✅ | 原样 |
| 12 | `Logic/TerminalSettingsNbt.java` | 145 | ✅ | `AEKey.toTagGeneric/fromTagGeneric` 增加 `HolderLookup.Provider` |
| 13 | `Parts/IMonitorRef.java` | 73 | ✅ | 原样 |
| 14 | `Parts/PartMonitorRef.java` | 82 | ✅ | 原样 |
| 15 | `Parts/MonitorRefs.java` | 147 | ✅ | `ModList` 换包 |
| 16 | `Parts/AaeMonitorRef.java` | 237 | ✅ | 反射方法改 `onUseItemOn(ItemStack, Player, InteractionHand, Vec3)`（AdvancedAE 1.21.1 仍是同类名/字段，已用 `javap` 核对） |
| 17 | `Parts/IAdvancedThroughputMonitorHost.java` | 67 | ✅ | 原样 |
| 18 | `Parts/ITerminalSettingsHost.java` | 41 | ✅ | 原样 |
| 19 | `Parts/ThroughputMonitorPart.java` | 525 | ✅ | 交互/NBT/流/TickingRequest 四处签名（见 §3.7–3.9）；渲染代码不变 |
| 20 | `Parts/AdvancedThroughputMonitorPart.java` | 222 | ⏭ | 依赖 21 号菜单；`TickingRequest` 同样去掉 `canBeAlerted` |
| 21 | `Parts/ThroughputTerminalPart.java` | 178 | ⏭ | 依赖菜单 + 自建包 + `PartModels`；`ResourceLocation` 已用工厂 |
| 22 | `menu/AdvancedThroughputMonitorMenu.java` | 160 | ⏭ | `MenuTypeBuilder.create` 重载二义性；`@GuiSync` 不变 |
| 23 | `menu/ThroughputTerminalMenu.java` | 900 | ⏭ | 行数据改走新 payload；`AEBaseMenu`/`GuiSync`/`MenuOpener` 核对 19.2 |
| 24 | `menu/ThroughputTerminalWirelessMenu.java` | 41 | ⏭ | 依赖 AE2WTLib（第 3 阶段） |
| 25 | `network/AetmNetwork.java` | 89 | 🔁 | `PayloadRegistrar` 注册 + `PacketDistributor.sendToPlayer` |
| 26 | `network/TerminalRow.java` | 42 | 🧱 | 数据 record 可平移（`AEKey` 由 `StreamCodec` 处理） |
| 27 | `network/TerminalRowsPacket.java` | 267 | 🔁 | 改成 `CustomPacketPayload` + `StreamCodec`（4 种 MODE 的读写要重排） |
| 28 | `client/ClientHighlights.java` | 142 | ⏭ | `RenderLevelStageEvent`/`TickEvent` 换包 |
| 29 | `client/TerminalClientPackets.java` | 43 | 🔁 | payload handler 形状（`IPayloadContext`） |
| 30 | `client/ClientTooltipFixer.java` | 32 | ⏭ | `appeng.api.integrations.igtooltip` 19.2 需核对（Jade 15 集成） |
| 31 | `client/MonitorTooltipLockFix.java` | 77 | ⏭ | 同上 |
| 32 | `client/PartModelsRegistration.java` | 56 | ⏭ | 注解换包；`PartModels.registerModels` 不变（时机复核） |
| 33 | `client/gui/AdvancedThroughputMonitorScreen.java` | 143 | ⏭ | `AEBaseScreen` 泛型不变；`drawBG` 签名需核对 |
| 34 | `client/gui/FlatButton.java` | 49 | ⏭ | `renderWidget(GuiGraphics,...)` 未变 |
| 35 | `client/gui/ChartMath.java` | 298 | 🧱 | 纯计算 |
| 36 | `client/gui/ThroughputChart.java` | 368 | ⏭ | `GuiGraphics.fill/drawString` 可用；颜色/贴图不变 |
| 37 | `client/gui/TerminalWindow.java` | 610 | ⏭ | 拖拽窗口逻辑自绘，主要核对 `GuiGraphics`/事件签名 |
| 38 | `client/gui/ThroughputTerminalScreen.java` | 817 | ⏭ | 最大文件；`InitScreens` 注册方式改变（§3.13） |
| 39 | `client/gui/WirelessThroughputTerminalScreen.java` | 22 | ⏭ | 无线版薄壳 |
| 40 | `xmod/ae2wtlib/AE2WtLibPlugin.java` | 58 | ✅ | 软依赖入口（无 `de.mari_023.*` 类型）；`isLoaded()` + 构造器里 `registerTerminals()` |
| 41 | `xmod/ae2wtlib/AE2WtLibHook.java` | 235 | ✅ | 19.5.1 改成 `AddTerminalEvent` + `WTDefinitionBuilder`（见 §8）；物品是 `ItemWT` 子类 |
| 42 | `xmod/ae2wtlib/AE2WtLibClientHook.java` | 30 | ✅ | `RegisterMenuScreensEvent` 里换 WUT 屏幕 |
| 43 | `xmod/ae2wtlib/UniversalThroughputTerminalItem.java` | 55 | ✅ | `extends de.mari_023.ae2wtlib.api.terminal.ItemWT`（旧接口已删）；`getMenuType` 两版都指向本菜单 |
| 44 | `xmod/ae2wtlib/ThroughputTerminalWirelessMenuHost.java` | 49 | ✅ | `WTMenuHost` 子类；`getMainMenuIcon` = 有线终端零件 |
| 45 | `xmod/ae2wtlib/WutThroughputTerminalScreen.java` | 57 | ✅ | `IUniversalTerminalCapable`（`cycleTerminalButton()` / `getHost()` / 热键切换） |

**统计（45 个文件）**：✅ 已移植 15（`ModItems` 只完成 2/5 个物品）；⏭ 待机械改 25；🔁 待重写 3（`AetmNetwork`、
`TerminalRowsPacket`、`TerminalClientPackets`）；🧱 可直接平移 2（`TerminalRow`、`ChartMath`）。
按包分布：root 4 + `Init` 1 + `Items` 3 + `Logic` 4 + `Parts` 9 + `menu` 3 + `network` 3 + `client` 12 + `xmod` 6 = 45。

---

## 5. 本阶段“删除清单”（移出 `src/`，未移植）

全部 45 个文件已移到 `legacy/1.20.1-src/`（gitignore，仅本机），git 里表现为删除；原件仍可从 `aetm-1.20.1` 分支/worktree 取回。
其中**本阶段不编译**的 30 个：`CommonEvents`、`ClientEvents`、`WirelessTerminalSettings`、`WirelessThroughputTerminalItem`、
`AdvancedThroughputMonitorPart`、`ThroughputTerminalPart`、3 个菜单、3 个网络类、11 个客户端类、6 个 xmod 类。

**为什么不注册“半成品物品 id”**：`advanced_throughput_monitor` / `throughput_terminal` / `wireless_throughput_terminal`
三个 id 若以普通 `Item` 占位注册，老存档里的零件/物品会静默变成错误类型；因此本阶段宁可“少注册”，由第 2 阶段
一次性接回真实 `PartItem`。

---

## 6. 未完成项（按恢复顺序）

1. **菜单 + 自建网络**（第 2 阶段的硬骨头）：`AetmNetwork`/`TerminalRowsPacket` 重写为 NeoForge payload；
   3 个菜单类接回，`CommonEvents` 的菜单注册改用 `BuiltInRegistries.MENU`。
   - 恢复顺序建议：`TerminalRow` → `TerminalRowsPacket`（payload）→ `AetmNetwork` → `ThroughputTerminalPart` →
     `ThroughputTerminalMenu` → `AdvancedThroughputMonitorPart` → `AdvancedThroughputMonitorMenu`。
2. **高级监控器 / 聚合终端物品接回**（`ModItems` 再注册 3 个 id、创造标签、配方/成就条件）。
3. **客户端层**：`ClientEvents`/`PartModelsRegistration`/`ClientHighlights` → 屏幕（`ThroughputTerminalScreen` 等）→
   `RegisterMenuScreensEvent` 注册。`ChartMath`/`ThroughputChart` 几乎原样。
4. **数据/资源改名**：`data/ae_monitornet/advancements` → `advancement`、`recipes` → `recipe`；配方条件
   `forge:mod_loaded`/`forge:not` 改为 `neoforge:mod_loaded`/`neoforge:not`（NeoForge 1.21 的 `conditions` 命名空间；
   需要再取一份 1.21.1 配方条件实例核对——AE2 自身的配方没有用条件）。
5. **GuideME 指南**：`Guide.builder(...).folder("ae2guide")` 注册 + 迁移 `assets/ae_monitornet/guides/ae_monitornet/guide/**`
   → `assets/ae_monitornet/ae2guide/**`（或保留默认 folder）；删除/确认 `guideme_guides/guide.json`。
6. ~~**AE2WTLib（WUT）集成**~~ —— ✅ 已完成（19.5.1 API，见 §8）。
7. **JEI/Jade 集成**：本模组无自建插件（集成在 AE2 侧 + 我们两个 Igtooltip 提供者），只需把两个提供者按 19.2 API 复核。

---

## 7. 明确不可分发的理由（给发布流程的提醒）

本阶段 jar **只用于验证工具链**：
- 3 个物品 id 完全未注册 → 老存档里的零件/终端会丢失；
- 无客户端界面（打开 menu 没有屏幕）；
- 数据包目录仍是 1.20.1 的复数形式 → 配方/成就在 1.21.1 不加载。

版本号已按要求设为 `1.2.0`（1.21.1 线起始），但**不要**把本阶段产物作为 release 上传。

---

## 8. xmod / AE2WTLib 19.5.1 移植记录（WUT 通用终端）

**结论**：终端 id `throughput` 已登记，`ae2wtlib:upgrade` / `ae2wtlib:combine` 四条配方从"解析失败"
（`JsonParseException: WTDefinition throughput does not exist`，旧日志里固定 4 条）变为正常加载 ——
专用服务器实测 `Loaded 1802 recipes`（= 旧客户端世界 `1798` + 这 4 条），零 parse error。

### 8.1 19.5.1 的 API 全变了（与 1.20.1 的 15.3.3 不兼容）

| 15.3.3（1.20.1 蓝本） | 19.5.1（本分支） |
|---|---|
| `WUTHandler.addTerminal(name, open, hostFactory, menuType, item, displayNameKey)` | `AddTerminalEvent.register(handler)`，回调里 `event.builder(name, hostFactory, menuType, ItemWT, Icon).addTerminal()` |
| `WUTHandler.wirelessTerminals` / `terminalNames` | `WTDefinition.wirelessTerminals()` / `exists(name)` / `of(name)`（`WTDefinition` 是 record，带 `Codec`/`StreamCodec`） |
| 物品实现 `IUniversalWirelessTerminalItem` | 物品**继承** `ItemWT`（接口已删） |
| `WTMenuHost(player, slot, stack, returnToMain)` | `WTMenuHost(ItemWT, Player, ItemMenuHostLocator, BiConsumer<Player, ISubMenu>)` |
| `CycleTerminalButton` | `IUniversalTerminalCapable#cycleTerminalButton()`（选择盘，非轮换） |
| API 在 `ae2wtlib` jar 根部 | API 在**嵌套 jar** `META-INF/jarjar/de.mari_023.ae2wtlib_api-19.5.1.jar`（独立 mod `ae2wtlib_api`） |

- 编译期：`build.gradle` 新增 `extractAe2wtlibApi`（Copy + `zipTree`）把嵌套 jar 解到
  `build/ae2wtlib-api/`，**只放 compileOnly**（放运行期会重复加载 `ae2wtlib_api`）。
- 运行时：NeoForge 自己解嵌套 jar；`libs/ae2wtlib-19.5.1.jar` 就够，无需额外文件。

### 8.2 两个时序约束（实测，踩过）

1. **注册回调必须在 mod 构造器里挂上**：`AddTerminalEvent` 是一次性的 —— `ae2wtlib_api` 的
   `AE2wtlibAPIEntrypoint` 在物品注册事件里 `run()` 一次，之后 `register(...)` 抛
   `IllegalStateException`。mods.toml 里 `ordering="BEFORE"` 的语义是"**本 mod 先加载**"，
   所以加载序是 `ae2 → ae2wtlib → ae2wtlib_api`；我们在构造器里排队即可，与两者的监听器顺序无关。
   顺带：热键也在同一时刻登记，**不需要** 1.20.1 的"提前占热键 id"（那是 commonSetup 才登记的代价）。
2. **物品不能在 mod 构造期 `new`**：NeoForge 的 `Item` 构造器会 `Registry#createIntrusiveHolder`，
   而原版物品注册表在注册事件之外是冻结的 —— 实测直接在 `@Mod` 构造器里创建会以
   `IllegalStateException: Registry is already frozen` 中止启动。
   因此 `ModItems#wirelessTerminal()` 用**记忆化惰性工厂**：DeferredRegister 的 lambda 与
   AE2WTLib 回调谁先跑谁创建，另一边复用同一实例（两者都在解冻的物品注册事件内）。

### 8.3 降级行为（没装 AE2WTLib）

- 物品退回 `WirelessThroughputTerminalItem`（只依赖 AE2），`xmod` 六个类一次都不会被类加载；
- 四条 `ae2wtlib:*` 配方被 `neoforge:mod_loaded` 条件挡掉；
- 屏幕退回 `WirelessThroughputTerminalScreen`（无"切换终端"按钮）；
- 握手失败（`AddTerminalEvent` 已 run / id 被占 / 物品类型不符）只记 WARN，不崩启动；
  物品自身仍可打开（`UniversalThroughputTerminalItem#getMenuHost` 在定义缺失时直接造我们的宿主）。
