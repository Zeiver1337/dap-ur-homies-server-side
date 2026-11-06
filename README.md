# Dap ur Homies（服务端版）

Minecraft 1.21.11 的 Fabric 服务端模组，把这个原版的多人互动玩法做成纯服务端实现：碰拳、完美击拳、击掌、拥抱、抓取投掷、人肉盾牌、飞踢、推搡、马里奥跳，还有 Mahito 药水。客户端不用装这个模组，有 Fabric API 就能进服玩。

原项目是 [Anteryo/Dap-ur-homie](https://github.com/Anteryo/Dap-ur-homie)（MIT）。这里是它的服务端移植版，和原作者没有隶属关系，也不是官方发布。

## 环境要求

- Minecraft Java 1.21.11
- Fabric Loader 0.16.14 或更高
- Fabric API 0.141.6+1.21.11
- Java 21
- Polymer 已经打进 jar，不用另装
- Danse 是可选的软依赖，只影响服务端动画兼容

只支持 1.21.11。`fabric.mod.json` 里写的是 `~1.21.11`，1.21.1 到 1.21.10 没测过，不建议直接换上。

## 安装

1. 服务端装好 Fabric Loader 和 Fabric API。
2. 把 `dap-ur-homies-0.6.3.jar` 放进服务端 `mods/`。
3. 开服后会在 `config/` 下生成三个文件：
   - `dap-ur-homies.json`：距离、冷却、模块开关、语言
   - `dap-ur-homies-perms.json`：每个玩家的开关和语言
   - `dap-ur-homies-admin.json`：封禁、冷却覆盖、强制关闭
4. 客户端直接进服。

Mahito 药水贴图不显示的话，客户端手动加载 `release/dap-ur-homies-resourcepack.zip`，或者让服务器开 Polymer 自动下发资源包。

## 操作

| 操作 | 方式 |
|---|---|
| 普通碰拳 | 空手右键玩家 |
| 蓄力碰拳 | 潜行 + 空手右键玩家，松开潜行释放 |
| 击掌 / 完美击拳 | 双方在短时间内互相碰拳 |
| 抓取 | 潜行 + 左键玩家 |
| 蓄力投掷 | 抓取后右键蓄力，再左键扔出 |
| 放下 | 抓取后直接左键（未蓄力） |
| 人肉盾牌 | 抓取后潜行 + 右键切换 |
| 挣脱 | 被抓时按跳跃，20% 成功率 |
| 推搡 | 疾跑 + 右键玩家 |
| 普通踢 | 看玩家脚部 + 右键 |
| 飞踢 | 潜行 + 看脚 + 右键两次，3 秒内完成，超时自动放出 |
| 拥抱 | 双方潜行、互瞄头部，0.5 秒内各右键一次 |
| 拍巴掌 | 从背后右键 |
| 接人 | `/catchready` 之后接住坠落的玩家 |

## 命令

```
/daphelp
/dap status [玩家]
/dap on|off [玩家]
/dap <类型> on|off [玩家]        类型：dap grab push kick hug slap clap danse
/dap ban <玩家> <类型|all> <分钟>
/dap unban <玩家> <类型>
/dap cooldown ms <类型> <毫秒>
/dap cooldown set <类型> <秒> [玩家]
/dap admincooldown on|off
/dap sound <tick>
/dap chargedmode <0|1>
/daplang [global <en|zh>]
/clap  /mariojump  /catchready
```

玩家给自己开关功能、用 `/daplang` 切语言不需要权限。其余管理命令要求 OP 等级 ≥ 2，或者 LuckPerms 节点 `dap-ur-homies.admin`。

安装、配置和命令的完整说明见 [使用说明](release/使用说明-v0.6.3.txt)，代码结构见 [架构说明](release/架构说明-v0.6.3.txt)，历次改动见 [更新日志](release/更新日志.txt)。

## 仓库结构

```
production/dap-ur-homies/   可构建的 Gradle 工程
release/                    当前版本的 jar 与文档
```

## 构建

需要 JDK 21：

```powershell
cd production\dap-ur-homies
gradlew.bat clean build test --no-daemon --console=plain
```

产物在 `build/libs/`，`dap-ur-homies-0.6.3.jar` 和 `dap-ur-homies-0.6.3-sources.jar`。Gradle wrapper 首次运行会从腾讯镜像下载 9.5.0，依赖走阿里云镜像，国内网络不用另外配代理。

## 已知限制

- 纯服务端实现，读不到客户端自定义按键。旋转、鞘翅加速、空中操控这类依赖按键的功能没有接入。
- 只声明兼容 1.21.11。
- 客户端要装 Fabric API，但不需要装本模组。
- 服务端装了领地或保护插件时，建议保持 `protectionCheckEnabled=true`，投掷撞墙前会先查权限。

## 授权

MIT，保留上游 Anteryo 的版权声明，全文见 [LICENSE](LICENSE)。
