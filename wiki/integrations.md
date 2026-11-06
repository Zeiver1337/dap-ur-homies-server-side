# 插件联动

这几项都是软依赖：不装照常开服，装了也不会成为启动前提。模组通过反射访问它们，所以它们的版本变化不会把服务器拖崩，最坏情况是联动自动降级。

启动时日志会打印一行实际状态：

```
[DapUrHomies] Initialized. Optional integrations: LuckPerms=false, Danse=true, protection=GriefPrevention (Polymer is bundled, no separate install needed)
```

下面分别说明每一项做了什么、怎么确认生效。

## Polymer（已内嵌）

Polymer 负责把模组资源打包下发给原版客户端，玩家进服时自动收到资源包，不需要手动下载。

五个 Polymer 模块已经 `include` 进 jar，不要再往 `mods/` 里放 Polymer，重复安装会导致类冲突。

玩家看不到 Mahito 药水贴图时，先确认服务器允许下发资源包，再考虑手动分发 `dap-ur-homies-resourcepack.zip`。

## Danse

Danse 提供玩家动画。本模组在播放动画前会查一次动画注册表：

- 注册表里有同名动画，就用自定义动画；
- 没有，就退回 Danse 自带的动作，比如 `dab`、`wave`、`fall`、`facepalm`、`ascend`；
- Danse 的接口对不上，就整体跳过动画，并打印一条警告。

所以装了 Danse 但没装动画包时，看到的是内置动作，而不是什么都不发生。想看到碰拳、击掌这类专属动作，需要在服务端加入包含同名动画（`dap`、`highfive`、`hug`、`yeet` 等）的模型包。

Danse 在 `fabric.mod.json` 的 `suggests` 里，缺失时不会报缺依赖。

## LuckPerms

装了 LuckPerms 之后，权限判定走 Fabric Permissions API，节点见 [权限](permissions.md)。

历史版本这里是坏的：代码找一个不存在的接口签名，每次都静默回退到文件权限，表现出来就是“装了 LuckPerms 但节点完全不起作用”。现在会先试 `Permissions.check(Entity, String, boolean)`，再退回旧版命令源签名，两种都能用。

确认方式看启动日志的 `LuckPerms=true`。如果显示 `false`，说明 Permissions API 没加载上，这时权限仍然按文件判定，服务器不会出错。

## 领地保护

支持三种：GriefPrevention、Lands、Flan。检查发生在交互生效之前，以及投掷撞墙破坏方块之前。

| 插件 | 检查方式 |
| --- | --- |
| GriefPrevention | 取目标位置的 claim，调用 `allowBuild` 判断发起者有没有权限 |
| Flan | 玩家交互用 `HURTPLAYER`，撞墙破坏用 `BREAK`，调用 `ClaimHandler.canInteract` |
| Lands | 只能判断目标是否位于领地内，读不到细分 flag |

Lands 这一条是已知短板。Lands 没有公开的细粒度查询接口，所以只要目标在领地里，本模组会在日志里警告一次，然后按放行处理。需要严格限制的话，请在 Lands 侧收紧玩家交互权限，或者换成前两种。

三项 API 必须完整解析成功，才会在日志里标记为已加载。以前那种“日志说加载了、检查其实没执行”的情况已经修掉。

要临时关闭全部保护检查，把配置里的 `protectionCheckEnabled` 改成 `false`。不建议在有领地插件的服务器上长期关闭。
