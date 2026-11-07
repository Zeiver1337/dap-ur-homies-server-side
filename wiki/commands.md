# 命令

命令分两类：所有人可用的开关与提示，以及需要管理员权限的配置类命令。管理员指原版 OP 权限等级 2 以上，或者拥有 LuckPerms 节点 `dap-ur-homies.admin` 的玩家。

## 玩家命令

| 命令 | 作用 |
| --- | --- |
| `/daphelp` | 显示帮助 |
| `/daphow` | 显示玩法提示 |
| `/dap status` | 查看自己的开关、权限和冷却状态 |
| `/dap on` / `/dap off` | 打开或关闭自己收到的全部互动 |
| `/dap <类型> on\|off` | 单独开关某个玩法 |
| `/daplang` | 在英文和中文之间切换自己的语言 |
| `/daplang self <en\|zh\|global>` | 直接指定自己的语言，`global` 表示跟随服务器 |
| `/clap` | 鼓掌 |
| `/mariojump` | 马里奥式跳跃 |
| `/catchready` | 进入接人准备状态 |
| `/grab` `/push` `/kick` `/hug` | 查看对应玩法的操作提示 |

`<类型>` 可选：`dap`、`grab`、`push`、`kick`、`hug`、`slap`、`clap`、`danse`。

注意 `slap` 只能通过命令开关，没有单独的操作提示命令。

## 管理员命令

| 命令 | 作用 |
| --- | --- |
| `/dap on\|off <玩家>` | 开关指定玩家。`off` 是强制关闭，玩家自己执行 `/dap on` 恢复不了 |
| `/dap <类型> on\|off <玩家>` | 单独开关指定玩家的某个玩法 |
| `/dap status <玩家>` | 查看指定玩家的状态 |
| `/dap ban <玩家> <类型\|all> <分钟>` | 临时封禁，时间到自动解除 |
| `/dap unban <玩家> <类型>` | 提前解除封禁 |
| `/dap cooldown ms <类型> <毫秒>` | 设置所有玩家的冷却，单位毫秒 |
| `/dap cooldown set <类型> <秒>` | 设置所有玩家的冷却，单位秒 |
| `/dap cooldown set <类型> <秒> <玩家>` | 给单个玩家设置冷却覆盖 |
| `/dap admincooldown on\|off` | 管理员是否无视冷却 |
| `/dap sound <tick>` | 击掌音效时长，单位 tick |
| `/dap chargedmode <0\|1>` | 蓄力模式：0 按住蓄力，1 循环蓄力 |
| `/dap reload` | 重新读取配置文件 |
| `/dap update` | 查看当前版本、最新版本和待应用的下载 |
| `/dap update check` | 立即检查 GitHub 上的最新版本 |
| `/dap update download` | 下载最新版并尝试替换，重启后生效 |
| `/daplang global <en\|zh>` | 设置服务器默认语言 |

## 几个容易混的点

`/dap off` 和 `/dap dap off` 不是一回事。前者关闭整个模组对你的所有互动，后者只关碰拳。全局关闭会让服务器停止驱动玩法逻辑，管理员执行 `/dap off` 时还会顺带清理所有人正在进行的抓取、人盾和蓄力状态，避免有人因为逻辑停摆被卡住。

`/dap ban` 的 `<类型>` 只认上面列出的类型名，写 `all` 表示全部。封禁记录写在 `config/dap-ur-homies-admin.json`，重启后还在。

冷却设置支持两种覆盖，优先级从高到低是：单个玩家覆盖、全局覆盖、配置文件里的默认值。设置了覆盖之后，改配置文件不会盖掉覆盖值，要先用命令改回去或者删掉 `admin.json` 里的对应条目。

## 更新

服务器启动时会异步查一次 GitHub 的最新 release，只写日志，不自动下载。检查结果可以在 `/dap update` 里看到。

`/dap update download` 把新 jar 下载到 `mods/.dap-ur-homies-update/`，校验大小和 SHA-256 后尝试替换当前 jar：

- 能删掉正在运行的旧 jar（Linux 常见）时，新 jar 直接移进 `mods/`，重启即生效；
- 删不掉（Windows 会锁住已加载的 jar）时，只保留下载文件，命令会返回完整路径，关服后手动替换即可。

不会把新 jar 直接丢进 `mods/`，因为同 id 的两个 jar 同时存在会让 Fabric 拒绝启动。

控制台也可以执行这三条命令，权限判断只在玩家执行时生效。

想把下载这一步也交给服务端，在配置里把 `autoUpdateEnabled` 改成 `true`。默认关闭，因为自动替换会改动 `mods/` 目录，多数服主希望自己挑时间重启。
