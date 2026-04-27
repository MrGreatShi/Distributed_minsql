# Distributed MiniSQL

## 仓库结构

```text
Distributed_MiniSQL/
|- pom.xml                               # 父工程（packaging=pom，聚合所有子模块）
|- README.md
|- architecture.png
|- 分布式miniSQL系统总体设计报告.md
|- minisql-common/
|  |- pom.xml
|  \- src/main/java/org/example/minisql/common/
|     |- config/                # 配置抽象与实现
|     |- constant/              # 常量定义
|     |- exception/             # 自定义异常类
|     \- util/                  # 工具类（如序列化、网络工具等）
|- minisql-protocol/
|  |- pom.xml
|  \- src/main/java/org/example/minisql/protocol/
|     |- command/client/        # Client 发给 Master 的命令对象
|     |- command/master/        # Master 回给 Client 或发给 Region 的命令对象
|     |- command/region/        # Region 发给 Master 的命令对象（如心跳、上报等）
|     |- codec/                 # 编解码器（Java 对象 <-> 字节流）
|     \- parser/                # SQL 解析器（SQL 字符串 -> Command 对象）
|- minisql-engine/
|  |- pom.xml
|  \- src/main/java/org/example/minisql/engine/
|     |- interpreter/           # SQL 解释器（入口）
|     |- api/                   # 对外暴露的 API 接口
|     |- buffer/                # 缓冲池管理
|     |- catalog/               # 元数据管理（表、索引等）
|     |- index/                 # 索引管理（B+树实现）
|     |- record/                # 记录存储与检索
|     \- storage/               # 磁盘存储管理（文件 I/O 封装）
|- minisql-master/
|  |- pom.xml
|  \- src/main/java/org/example/minisql/master/
|     |- bootstrap/             # 启动类
|     |- zk/                    # ZooKeeper 相关逻辑（注册、监听等）
|     |- metadata/              # 集群元数据管理（表分布、Region 状态等）
|     |- strategy/              # 负载均衡、故障切换等策略实现
|     |- socket/                # Master 监听客户端和 Region 的 Socket 处理
|     \- handler/{client,region}/               # Master 处理 Client 和 Region 请求的 Handler
|- minisql-region/
|  |- pom.xml
|  \- src/main/java/org/example/minisql/region/
|     |- bootstrap/             # 启动类
|     |- zk/                    # ZooKeeper 相关逻辑（注册、监听等）
|     |- masterlink/            # 与 Master 的通信逻辑（心跳、上报、接收命令等）
|     |- clientsvc/             # 接收 Client 请求的服务逻辑
|     |- replication/           # 数据复制相关逻辑（主副本同步等）
|     |- migration/             # 数据迁移相关逻辑（Region 之间的数据迁移）
|     \- executor/              # SQL 执行逻辑（调用 miniSQL Engine 执行 SQL）
|- minisql-client/
|  |- pom.xml
|  \- src/main/java/org/example/minisql/client/
|     |- cli/                   # 命令行入口
|     |- cache/                 # 路由缓存（表 -> Region 映射）
|     |- router/                # 路由逻辑（查询 Master 获取表分布信息）
|     |- masterlink/            # 与 Master 的通信逻辑（发送命令、接收结果等）
|     \- regionlink/            # 与 Region 的通信逻辑（发送 SQL、接收结果等）
|- minisql-dist/                         # 统一打包/发布
   \- pom.xml
```

## 模块职责

- `minisql-common`：共享常量、工具类、异常和配置抽象。
- `minisql-protocol`：面向 Client-Master-Region 文本协议的命令模型与解析/编解码。
- `minisql-engine`：单节点 miniSQL 内核（`Interpreter -> API -> managers`）。
- `minisql-master`：元数据路由、ZooKeeper 监听、故障切换与策略编排。
- `minisql-region`：SQL 执行节点、Master 命令处理、复制/迁移相关逻辑。
- `minisql-client`：命令行入口、路由缓存、查询 Master、转发 Region 请求。
- `minisql-dist`：用于后续打包脚本的分发聚合模块。

## 默认配置

下面这些配置已写入 `minisql-common/src/main/resources/minisql-default.properties`，并通过 `MiniSqlConfigLoader` 统一加载：

- ZooKeeper 注册路径：`/db`
- Master 监听端口：`12345`
- Region 客户端端口：`22222`
- Region 数据迁移端口：`1117`

可通过 JVM 参数覆盖（示例）：`-Dminisql.master.port=12346`

## 构建与运行

在项目根目录（`E:\Distributed_MiniSQL`）下执行：

```powershell
mvn clean verify
```

运行：

```powershell
mvn -pl minisql-master -am install -DskipTests
mvn -f minisql-master\pom.xml exec:java

mvn -pl minisql-region -am install -DskipTests
mvn -f minisql-region\pom.xml exec:java

mvn -pl minisql-client -am install -DskipTests
mvn -f minisql-client\pom.xml exec:java
```

快速迭代时，只构建单个模块(例如)：

```powershell
mvn -pl minisql-protocol -am clean test
mvn -pl minisql-client -am clean test
```

## 开发说明

- 保持依赖方向单向：`common <- protocol <- (master/region/client)`，并且 `region -> engine`。
- 避免 `master` 和 `region` 之间出现直接实现依赖；通过协议契约进行通信。
- 新代码请放在各模块自己的包根目录下，不要继续写入旧的根目录 `src/`。

