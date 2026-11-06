# 安装与升级

## 服务端需要什么

| 项目 | 版本 |
| --- | --- |
| Minecraft | Java 版 1.21.11 |
| Fabric Loader | 0.16.14 或更高 |
| Fabric API | 0.141.6+1.21.11 |
| Java | 21 |

模组在 `fabric.mod.json` 里把 Minecraft 限定在 `~1.21.11`。1.21.1 到 1.21.10 都跑不起来，不要靠改版本号硬试。

客户端只需要 Fabric API，不需要装本模组。所有交互判定、状态和特效都在服务端执行，原版客户端能看到动作和提示。

Polymer 已经打进 jar，负责把模组资源发给原版客户端，不用另外下载。

## 安装

1. 给服务端装好 Fabric Loader 和 Fabric API。
2. 把 `dap-ur-homies-0.6.3.jar` 放进服务端的 `mods/` 目录。
3. 启动服务器。

第一次启动会生成三个文件：

```
config/dap-ur-homies.json         主配置
config/dap-ur-homies-perms.json   玩家偏好与语言
config/dap-ur-homies-admin.json   封禁、冷却覆盖、管理员强制关闭
```

前两个可以手改，改完执行 `/dap reload` 生效。第三个由命令写入，不要在服务器运行时手动编辑。

启动日志末尾会打印一行联动状态，用来确认装了哪些插件：

```
[DapUrHomies] Initialized. Optional integrations: LuckPerms=false, Danse=false, protection=none (Polymer is bundled, no separate install needed)
```

## Mahito 药水贴图

原版客户端没有药水贴图时，有两种办法：

- 让玩家手动加载 `dap-ur-homies-resourcepack.zip`。
- 开启 Polymer 的自动资源包下发，玩家进服时自动接收。

不处理也不影响玩法，只是药水显示成普通物品。

## 升级

替换 `mods/` 里的 jar，重启服务器。配置文件不会被动过，新版本新增的配置项会以默认值生效。

从 0.6.2 升到 0.6.3 不需要改任何配置。

如果要退回旧版本，直接换回旧 jar 即可，但新版本写入过的 `dap-ur-homies-admin.json` 字段旧版本可能读不懂，建议顺手删掉这个文件让它重新生成。
