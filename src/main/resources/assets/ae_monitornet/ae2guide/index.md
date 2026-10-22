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

This addon adds throughput monitoring to Applied Energistics 2. It has two kinds of devices:

* a **monitor** is a cable part that watches exactly one item or fluid on the network and draws its icon, the stored
  amount and the transfer rate on its own face;
* a **terminal** is a cable part that lists every monitor on the same network and opens a floating chart window for a
  row.

## ME Throughput Monitor

<ItemImage id="ae_monitornet:throughput_monitor" scale="2" />
<ItemLink id="ae_monitornet:throughput_monitor" />

The basic sensor. Place it on an ME cable and right-click it with the item (or fluid) you want to watch; the face then
shows that icon, the current amount and the rate. Right-click with an empty hand for its GUI.

The rate tier is per tick, per second, per minute or per 10 minutes. Cycle it with the configurator or from the GUI;
the face is redrawn immediately. The monitor stops sampling while nothing is being watched.

## Advanced ME Throughput Monitor

<ItemImage id="ae_monitornet:advanced_throughput_monitor" scale="2" />
<ItemLink id="ae_monitornet:advanced_throughput_monitor" />

The same sensor, plus a configuration slot in its GUI, a sampling history and a line chart (storage / flow /
acceleration) with a selectable time window. Right-click opens the GUI; put an item or a fluid into the configuration
slot to choose what is watched. Fluid amounts can be shown in mB or B.

Only the advanced monitor records history. A row can only show a chart when an advanced monitor contributes to it.

## Throughput Monitor Configurator

<ItemImage id="ae_monitornet:throughput_monitor_configurator" scale="2" />
<ItemLink id="ae_monitornet:throughput_monitor_configurator" />

A hand-held tool: right-click a monitor to cycle its rate tier (t / s / m / 10m) without opening any GUI.

## ME Storage Monitor Terminal

<ItemImage id="ae_monitornet:throughput_terminal" scale="2" />
<ItemLink id="ae_monitornet:throughput_terminal" />

A cable part that needs power and a channel. It lists every throughput monitor on the same network and merges the
monitors that watch the same key into one row: icon, amount and rate. The header holds the global rate tier
(t / s / m / 10m) and the terminal size setting; scroll the list with the mouse wheel.

**Double-click a row** to open (or close) that row's floating chart window. The window is dragged by its title bar and
has its own buttons: time window, metric (storage / flow / acceleration), fluid unit (mB / B) for fluid rows, and
Locate, which highlights the monitor in the world.

## Wireless ME Storage Monitor Terminal

<ItemImage id="ae_monitornet:wireless_throughput_terminal" scale="2" />
<ItemLink id="ae_monitornet:wireless_throughput_terminal" />

The same terminal as an AE2 wireless terminal. Link it in a Security Terminal, then use it while inside the range of a
Wireless Access Point. The wired and the wireless terminal share the same menu and screen; only the link and the power
source differ.

## Monitors and terminals together

* A monitor measures; a terminal only displays. A terminal whose network has no monitor shows an empty list.
* Every monitor of one network appears on every terminal of that network, wherever the parts are placed.
* A monitor and a terminal do not have to sit on the same cable, but they must be in the same ME network.
* Several monitors can watch the same key; their readings are merged into a single row.

## Tips

* The rate is derived from samples, so a freshly placed monitor needs a couple of samples before the rate settles.
* The chart needs history: the advanced monitor stores its samples locally, and the terminal asks the server for a
  row's curve at a throttled rate, so the curve is not updated every frame.
