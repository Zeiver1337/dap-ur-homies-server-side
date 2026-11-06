# 开发

## 构建

需要 JDK 21，仓库自带 Gradle wrapper。

```bash
./gradlew build
```

产物在 `build/libs/`：主 jar、sources jar，以及五个 Polymer 模块的嵌套 jar。主 jar 已经包含 Polymer，可以直接丢进 `mods/`。

跑测试：

```bash
./gradlew test
```

## 目录结构

```
src/main/java/com/dap/dapurhomies/
├── DapUrHomiesMod.java        入口：初始化、事件注册
├── ModLifecycle.java          唯一 tick 驱动、断线清理、关闭清理
├── command/                   /dap 与各玩法命令
├── compat/                    Danse、领地保护等软依赖适配
├── config/                    配置读写
├── interaction/               各交互处理器与共享子系统
├── permission/                权限提供者与玩家偏好
└── util/                      冷却、封禁、时钟等基础设施
```

## 几条实现约定

游戏逻辑只在 `ServerTickEvents.END_SERVER_TICK` 一处驱动。各模块通过 `InteractionModules` 注册 tick、cleanup、purge，注册顺序就是执行顺序，不要另外挂 tick 事件，否则时序会乱。

抓取、投掷、人盾拆在三个类里：`GrabYeetHandler` 负责入口和输入，`ThrowTracker` 管飞行与撞墙，`HumanShieldController` 管人盾。它们共享 `holding` / `heldBy` 状态，改动时注意清理顺序，先关人盾再解除持有关系。

软依赖一律走反射，不要写成编译期依赖。`compat` 包里的类在静态初始化时探测目标插件，探测失败就降级，不允许抛异常影响启动。

冷却统一走 `InteractionCooldowns`。它负责把毫秒配置向上取整成 tick，并处理管理员豁免和玩家覆盖。新加玩法时别自己算 tick。

## 测试

`src/test/java` 下是 JUnit 5 单元测试，目前覆盖玩家偏好的映射语义和毫秒到 tick 的取整。涉及世界状态的逻辑没有做自动化测试，改动后需要实际上服验证。

## 上游

本仓库是 [Anteryo/Dap-ur-homie](https://github.com/Anteryo/Dap-ur-homie) 的服务端移植。玩法设计、动画和原版材质来自上游，本仓库只做服务端适配。

代码以 MIT 协议发布，保留上游版权声明。提交改动时不要覆盖 `LICENSE` 里的原作者署名。
