# 权限

权限由三层决定，从外到内是：服务器的模块开关、玩家自己的偏好、管理员施加的限制。

## 管理员怎么认定

满足任意一条就是管理员：

- 原版 OP 权限等级 2 以上
- LuckPerms 节点 `dap-ur-homies.admin`

管理员默认无视冷却，也常用管理员命令。是否无视冷却由 `adminBypassCooldown` 控制，关掉之后管理员一样要等。

## 发起交互时

服务端按这个顺序判断，遇到第一个不通过的就停：

1. 被管理员强制关闭（`/dap off <玩家>`）→ 拒绝。这一条优先于管理员身份，玩家自己恢复不了。
2. 是管理员 → 放行。
3. 被封禁（全部或该类型）→ 拒绝。
4. 玩家偏好里关掉了这个玩法 → 拒绝。
5. 通过。

## 被交互时

1. 被强制关闭 → 拒绝。
2. 被封禁 → 拒绝。
3. 玩家偏好里关掉了这个玩法 → 拒绝。
4. 发起者是管理员 → 直接通过。

也就是说，管理员可以强行和目标互动，但目标自己被管理员封禁或被强制关闭的情况仍然拦得住。

## 玩家偏好文件

`config/dap-ur-homies-perms.json`，键是玩家 UUID，值是一组布尔开关：

```json
{
  "069a79f4-44e9-4726-a5be-fca90e38aaf5": {
    "enabled": true,
    "dap": true,
    "grab": true,
    "push": true,
    "kick": true,
    "hug": true,
    "slap": true,
    "clap": true,
    "danse": true,
    "lang": "global"
  }
}
```

`enabled` 是总开关，关掉之后其余项都按关闭处理。`lang` 取 `en`、`zh` 或 `global`。

玩家用 `/dap <类型> on|off` 改的就是这里，不用手动编辑。手改之后执行 `/dap reload`。

## 封禁与强制关闭

这两类状态写在 `config/dap-ur-homies-admin.json`，结构由命令维护：

- `/dap ban <玩家> <类型|all> <分钟>` 到期自动失效，重启不清空。
- `/dap off <玩家>` 会一直有效，直到管理员执行 `/dap on <玩家>`。
- `/dap cooldown set ... <玩家>` 的玩家级覆盖也在这里。

想整包清空，停服后删文件，重启会自动生成空文件。

## LuckPerms 节点

| 节点 | 作用 |
| --- | --- |
| `dap-ur-homies.admin` | 管理员 |
| `dap-ur-homies.dap` | 允许发起碰拳 |
| `dap-ur-homies.grab` | 允许发起抓取 |
| `dap-ur-homies.push` | 允许发起推搡 |
| `dap-ur-homies.kick` | 允许发起踢击 |
| `dap-ur-homies.hug` | 允许发起拥抱 |
| `dap-ur-homies.slap` | 允许发起拍巴掌 |
| `dap-ur-homies.clap` | 允许鼓掌 |
| `dap-ur-homies.danse` | 允许触发 Danse 动画 |
| `dap-ur-homies.receive.<类型>` | 允许被该类型交互，仅在 `luckPermsControlsReceive=true` 时检查 |

没有显式设置的节点会回退到 `dap-ur-homies-perms.json` 里的玩家偏好，所以开 LuckPerms 不会让原本的设置失效。

默认 `luckPermsControlsReceive=false`，原因是很多服务器只把 LuckPerms 当管理权限用，不希望玩家“被交互”也受节点影响。要让 LuckPerms 全权接管接收判定，把这个开关打开。
