================================================================================
Dap-ur-homies v0.6.3 - 发布说明
================================================================================

【版本信息】
版本号      : 0.6.3
发布日期    : 2026-11-06
适用服务端  : Minecraft Java 1.21.11 + Fabric
环境要求    : Fabric Loader 0.16.14+ / Fabric API 0.141.6+1.21.11 / Java 21
模组类型    : 纯服务端（客户端无需安装本模组）
许可证      : MIT（保留上游 Anteryo/Dap-ur-homie 的版权声明，本仓库为非官方移植）

【安装】
1. 服务端安装 Fabric Loader 与 Fabric API。
2. 将 dap-ur-homies-0.6.3.jar 放入服务端 mods/ 目录。
3. 启动服务器，自动生成：
   - config/dap-ur-homies.json         主配置
   - config/dap-ur-homies-perms.json   玩家权限与语言
   - config/dap-ur-homies-admin.json   封禁 / 冷却覆盖 / 强制关闭（重启保留）
4. 客户端直接进服即可。
5. 若客户端看不到 Mahito 药水贴图，可手动加载 dap-ur-homies-resourcepack.zip，
   或由服务器开启 Polymer 自动资源包下发。

【这一版改了什么】
0.6.3 只整理内部结构，命令、配置项、玩法和数据文件格式都没有变化。
从 0.6.2 升级可以直接换 jar，配置和玩家开关不用迁移。

- 入口类只保留模组初始化与注册；断线清理、tick 驱动、输入检测和关闭时的清理
  搬到 ModLifecycle。
- 命令拆成 DapCommands（/dap）和 InteractionCommands（其余命令），
  命令名、参数和权限判断与旧版一致。
- 玩家开关与语言改由 PlayerPreferences 描述，持久化仍由 FilePermissionProvider 负责。
- 冷却的检查、提示和覆盖值解析集中到 InteractionCooldowns，
  击掌的共享冷却与碰拳的各自冷却保持原来的区别。
- 从 DapHandler 拆出击掌特效 DapVisualEffects，
  从 GrabYeetHandler 拆出投掷追踪 ThrowTracker 和人盾 HumanShieldController。
- 删除只写不读的 PoseState，以及一批没有引用的字段和导入。

【文件清单】
- dap-ur-homies-0.6.3.jar                         主模组（放入服务端 mods/）
- dap-ur-homies-0.6.3-sources.jar                 源码 jar（开发参考）
- dap-ur-homies-source-code-releasev0.6.3.txt     完整源码文本快照（39 个 Java 文件）
- dap-ur-homies-resourcepack.zip                  资源包（可选，客户端贴图）
- 使用说明-v0.6.3.txt                              安装、操作、命令、配置
- 架构说明-v0.6.3.txt                              分层结构、启动流程、模块表、构建
- 更新日志.txt                                     累计更新日志（v0.4.0 ~ v0.6.3）
- README-v0.6.3.txt                                本文件

【快速操作】
- 空手右键玩家            普通碰拳
- 潜行 + 空手右键玩家      蓄力碰拳（松开潜行释放）
- 疾跑 + 右键玩家          推搡
- 潜行 + 左键玩家          抓取
- 抓取后右键               开始蓄力（BossBar）
- 蓄力后左键               投掷
- 未蓄力左键               放下
- 抓取后潜行 + 右键        切换人肉盾牌
- 被抓者按跳跃             尝试挣脱（20%）
- 双方潜行 + 互瞄头部 + 各自右键   拥抱（0.5 秒内）
- 潜行 + 看脚部 + 右键两次 蓄力飞踢
- 看脚部 + 右键            普通踢
- 从背后右键               拍巴掌
- /clap /mariojump /catchready     鼓掌 / 马里奥跳 / 接人准备

完整命令与配置见 使用说明-v0.6.3.txt。

【构建信息】
Gradle 9.5.0（项目自带 wrapper）/ Fabric Loom 1.17.21 / JDK 21
Yarn 1.21.11+build.6 / Loader 0.16.14 / Fabric API 0.141.6+1.21.11
构建命令：在 production/dap-ur-homies 下执行 gradlew.bat clean build test --no-daemon
================================================================================
