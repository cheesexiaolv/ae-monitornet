---
navigation:
  title: AE MonitorNet
  position: 0
item_ids:
  - ae_monitornet:throughput_monitor
  - ae_monitornet:advanced_throughput_monitor
  - ae_monitornet:throughput_monitor_configurator
  - ae_monitornet:throughput_terminal
  - ae_monitornet:wireless_throughput_terminal
---

# AE MonitorNet

本附属为 Applied Energistics 2（应用能源2）提供吞吐量监控。它有两类设备：

* **监控器**是一个线缆部件：它盯住网络中的**一种**物品或流体，在自己的面板上画出图标、存量与传输速率；
* **终端**也是一个线缆部件：它把同一网络上**所有**监控器列成一张表，并能给某一行打开一个浮动曲线窗口。

## ME吞吐量监控器

<ItemImage id="ae_monitornet:throughput_monitor" scale="2" />
<ItemLink id="ae_monitornet:throughput_monitor" />

最基础的传感器。把它装在 ME 线缆上，手持想监控的物品（或流体）右键它，面板上就会显示该图标、当前存量与速率。
空手右键打开它的界面。

速率档位是「每 tick / 每秒 / 每分钟 / 每 10 分钟」，可用配置器或界面里的按钮循环切换，切换后面板立刻重绘。
没有设置监控对象时，监控器会停止采样。

源自Advanced AE。

## 吞吐量监控配置器

<ItemImage id="ae_monitornet:throughput_monitor_configurator" scale="2" />
<ItemLink id="ae_monitornet:throughput_monitor_configurator" />

手持工具：右键 *ME吞吐量监控器* 即可循环切换它的速率档位（t / s / m / 10m），不用打开任何界面。

源自Advanced AE。

## 高级ME吞吐量监控器

<ItemImage id="ae_monitornet:advanced_throughput_monitor" scale="2" />
<ItemLink id="ae_monitornet:advanced_throughput_monitor" />

同样的传感器，另外带一个界面内的配置槽、采样历史，以及可选时间窗口的折线图（存量 / 流量 / 加速度）。
右键打开界面，把物品或流体放进配置槽即设为监控对象；流体数量可在 mB 与 B 之间切换显示。

只有高级监控器会记录历史，所以某一行只有在有高级监控器参与时才有曲线。



## ME存储监控终端

<ItemImage id="ae_monitornet:throughput_terminal" scale="2" />
<ItemLink id="ae_monitornet:throughput_terminal" />

线缆部件，需要供电与频道。它枚举同一网络上的**所有**吞吐量监控器，并把监控同一对象的多个监控器
合并成一行：图标、存量、速率。头部第二行是全局速率档位（t / s / m / 10m），左侧可切换终端尺寸；
列表用鼠标滚轮滚动。

**双击某一行**即可打开（或关掉）该行的浮动曲线子窗口。子窗口可以按住标题栏拖动，自己的按钮有：
时间窗口、指标（存量 / 流量 / 加速度）、流体行的大小单位（mB / B），以及「定位」——把该监控器在世界里高亮出来。

## 无线 ME 存储监控终端

<ItemImage id="ae_monitornet:wireless_throughput_terminal" scale="2" />
<ItemLink id="ae_monitornet:wireless_throughput_terminal" />

与上面的终端相同，但做成 AE2 无线终端。

## 监控器与终端的关系

* 监控器负责测量，终端只负责显示。网络里一个监控器都没有时，终端的列表是空的。
* 同一网络里的每个监控器都会出现在该网络的每个终端上，与部件装在哪里无关。
* 监控器与终端不必装在同一根线缆上，但必须在同一个 ME 网络里。
* 可以有多个监控器监控同一个对象，它们的读数会合并到同一行。

## 小提示

* 速率由采样推算：刚放下的监控器要攒够两个采样点，速率才会稳定。
* 曲线依赖历史数据：高级监控器在本地保存采样点，终端按节流后的频率向服务端请求某一行的曲线，
  所以曲线不是每帧都刷新。
