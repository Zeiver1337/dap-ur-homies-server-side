# 配置

主配置文件是 `config/dap-ur-homies.json`，首次启动自动生成。改完执行 `/dap reload`，不用重启服务器。

## 模块开关

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `enableDap` | `true` | 碰拳与击掌 |
| `enableHighFive` | `true` | 击掌判定 |
| `enableGrabYeet` | `true` | 抓取、投掷、人盾 |
| `enablePush` | `true` | 推搡 |
| `enableKick` | `true` | 踢击与飞踢 |
| `enableHug` | `true` | 拥抱 |
| `enableSlap` | `true` | 拍巴掌 |
| `enableClap` | `true` | 鼓掌 |

关掉一个模块只影响发起，不影响已经在进行的动作。想让某个玩家单独关闭，用 `/dap <类型> off`，改配置文件是服务器级别的。

## 距离

单位是格，判定的是两个人之间的实际距离，不是准星射线。

| 键 | 默认 |
| --- | --- |
| `dapRange` | `2.5` |
| `highFiveRange` | `3.0` |
| `grabRange` | `3.0` |
| `pushRange` | `2.5` |
| `kickRange` | `2.0` |
| `hugRange` | `2.0` |
| `slapRange` | `1.5` |

距离调大以后，判定仍然要求目标在视线方向上大致合理，隔着墙抓人不会成功。

## 冷却

单位毫秒。内部换算成 tick 时向上取整，所以 1 毫秒也会占 1 tick（50 毫秒）。

| 键 | 默认 | 换算 |
| --- | --- | --- |
| `dapCooldownMs` | `1500` | 1.5 秒 |
| `highFiveCooldownMs` | `1500` | 1.5 秒 |
| `grabCooldownMs` | `1000` | 1 秒 |
| `pushCooldownMs` | `3000` | 3 秒 |
| `kickCooldownMs` | `2000` | 2 秒 |
| `hugCooldownMs` | `5000` | 5 秒 |
| `slapCooldownMs` | `3000` | 3 秒 |

这里的值是默认兜底。用 `/dap cooldown` 设置过全局或玩家覆盖之后，覆盖值优先。

## 权限与保护

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `adminBypassCooldown` | `true` | 管理员无视冷却。关掉后管理员也要等冷却 |
| `luckPermsControlsReceive` | `false` | 是否让 LuckPerms 同时决定“能不能被别人交互” |
| `protectionCheckEnabled` | `true` | 交互前是否检查领地保护 |

`protectionCheckEnabled` 默认开着。服务器装了 GriefPrevention、Lands 或 Flan 时建议保持开启；什么都没装时这项检查会直接放行，不会产生额外开销。

## 音效与蓄力

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `highFiveSoundTicks` | `40` | 击掌音效总时长，40 tick 等于 2 秒 |
| `clapIntervalTicks` | `5` | 击掌音效的播放间隔 |
| `chargedDapMode` | `0` | `0` 按住蓄力，`1` 循环蓄力 |

## 语言

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `globalLang` | `"en"` | 玩家语言设为 `global` 时使用的语言，可选 `en`、`zh` |

单个玩家的语言存在 `config/dap-ur-homies-perms.json`，玩家可以用 `/daplang` 自己改。

## 哪些文件不要手改

`config/dap-ur-homies-admin.json` 存封禁、冷却覆盖和管理员强制关闭状态，由命令维护。服务器运行时手改这个文件，内存里的状态和文件会对不上，下一次写入会把你的修改盖掉。要清空这些状态，先停服再删文件。

`config/dap-ur-homies-perms.json` 可以手改，格式是玩家 UUID 到偏好对象的映射，改完同样需要 `/dap reload`。
