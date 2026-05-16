# Client ↔ Master 通信接口与协议文档

> **快速导航**：本文档说明 Client 与 Master 之间、以及 RegionServer 与 Master 之间的全部通信接口。  
> 源码位置参见文末"源码索引"章节。

---

## 1. 总体架构

```
Client ──[TCP 12345]──► MasterServer ◄──[TCP 12345]── RegionServer
           路由查询                          状态上报
Client ──[TCP 22222]──► RegionServer
           SQL 执行
```

Master 在单一端口（默认 **12345**）上同时接受来自 Client 和 RegionServer 的连接，通过消息头中的 `[client]` 或 `[region]` 前缀来区分。

---

## 2. 协议格式

**文本行协议**：每条消息为一行文本（以 `\n` 结尾），UTF-8 编码。

### 消息结构

```
[<source>] <verb> [<arg1>] [<arg2>] ...
```

| 字段 | 说明 | 可选值 |
|------|------|--------|
| `source` | 消息来源 | `client` / `master` / `region` |
| `verb`   | 操作动词 | 见下表 |
| `arg...` | 参数列表 | 空格分隔，数量由 verb 决定 |

### 合法 verb 列表

| verb | 使用场景 |
|------|----------|
| `create`  | 建表路由请求 / 路由响应 / Region 通知建表 |
| `select`  | 查询路由请求 / 路由响应 |
| `insert`  | 写入路由请求 / 路由响应 |
| `delete`  | 删除路由请求 / 路由响应 |
| `drop`    | 删表路由请求 / 路由响应 / Region 通知删表 |
| `show`    | 查询所有表名 |
| `recover` | Region 上报已有表 / Master 令 Region 清空数据 |
| `copy`    | Master 令 Region 推送数据文件到另一节点 |
| `ok`      | 通用成功响应 |
| `error`   | 通用错误响应（附带错误信息） |

---

## 3. Client ↔ Master 接口

> 每次交互均为短连接：Client 发一行，Master 回一行，连接关闭。

### 3.1 查询表路由

**请求**：

```
[client] <verb> <tableName>
```

| verb | 含义 |
|------|------|
| `create` | 新建表——Master 分配两个 Region |
| `select` | 查询读路由 |
| `insert` | 查询写路由 |
| `delete` | 查询删除路由 |
| `drop`   | 查询删表路由 |

示例：
```
[client] create users
[client] select orders
[client] insert logs
```

**成功响应**：

```
[master] <verb> <primaryIP> <secondaryIP> <tableName>
```

若只有一个副本，`secondaryIP` 为 `-`（占位符）：

```
[master] create 10.0.0.1 10.0.0.2 users
[master] select 10.0.0.1 10.0.0.2 orders
[master] insert 10.0.0.1 - logs
```

**错误响应**：

```
[master] error <message words...>
```

示例：
```
[master] error Table does not exist: ghost
[master] error At least two healthy regions are required to create table users
```

---

### 3.2 查询所有表

**请求**：
```
[client] show tables
```

**成功响应**（按字典序）：
```
[master] show users orders logs
```

若无表：
```
[master] show
```

---

## 4. RegionServer → Master 接口

> Region 主动连接 Master（TCP 12345），发一行通知，Master 回一行确认。

### 4.1 启动 / 重连时上报已有表

```
[region] recover [<table1>] [<table2>] ...
```

示例（有表）：
```
[region] recover users orders
```

示例（无表）：
```
[region] recover
```

Master 根据 **TCP 连接的对端 IP** 识别是哪个 Region（不依赖命令参数），将这些表注册到路由表中。

**响应**：
```
[master] ok recover accepted
```

---

### 4.2 新建表后通知

```
[region] create <tableName>
```

**响应**：
```
[master] ok create accepted
```

---

### 4.3 删除表后通知

```
[region] drop <tableName>
```

**响应**：
```
[master] ok drop accepted
```

---

## 5. Master → RegionServer 接口

> Master 主动连接 Region（TCP 22222，与 Client 共用端口），发一行命令，读一行响应。

### 5.1 令 Region 清空本地数据并重新待命

触发时机：ZooKeeper 检测到 Region 上线时（无论是首次还是重新上线）。

```
[master] recover
```

Region 应清空本地数据库文件后，通过 `[region] recover` 向 Master 汇报当前状态（此时表列表为空）。

---

### 5.2 令 Region 将数据文件推送到另一节点

触发时机：某 Region 宕机后，Master 要求幸存副本将数据同步到新副本节点。

```
[master] copy <targetIP> <tableName>.txt
```

示例：
```
[master] copy 10.0.0.3 users.txt
```

收到此命令的 Region 需将 `users.txt`（SQL 日志文件）通过 TCP 推送至 `10.0.0.3:1117`（migrationPort）。目标 Region 接收后重放 SQL 以恢复数据。

---

## 6. Client ↔ RegionServer 接口

> 直接发送 SQL 字符串，无协议头，以 `;;` 结尾（与单机版 miniSQL 保持一致）。

**请求**（Client → Region，TCP 22222）：
```
SELECT * FROM users WHERE id = 1;;
```

**响应**（Region → Client）：纯文本执行结果，多行间以空行作为结束信号，或连接关闭表示结束。

---

## 7. 路由缓存策略

Client 本地维护路由缓存 `RouteCache`：

| 操作 | 缓存行为 |
|------|----------|
| `CREATE TABLE` | 始终向 Master 查询（Master 需分配副本），结果存入缓存 |
| `SELECT/INSERT/DELETE` | 优先查本地缓存，未命中再查 Master |
| `DROP TABLE` | 查路由（同上），执行成功后**主动清除缓存** |
| Region 连接失败 | 主动清除对应表的缓存，重新查 Master 获取最新路由 |

---

## 8. 双写策略（写操作）

对于写操作（INSERT / DELETE / CREATE TABLE / DROP TABLE / CREATE INDEX / DROP INDEX），Client 执行流程：

1. 向 Master 获取路由（主 Region IP + 副 Region IP）
2. **先写主 Region**（必须成功）
3. 若主 Region 失败：清除缓存 → 重查路由 → 重试一次
4. **再写副 Region**（失败仅打印 WARN，不中断流程）

读操作（SELECT）只发主 Region。

---

## 9. 默认配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `minisql.master.host` | `127.0.0.1` | Master 主机地址（Client 使用） |
| `minisql.master.port` | `12345` | Master 监听端口 |
| `minisql.region.client-port` | `22222` | Region 的 Client / Master 命令端口 |
| `minisql.region.migration-port` | `1117` | Region 数据迁移接收端口 |
| `minisql.socket.timeout-millis` | `15000` | Socket 读写超时（毫秒） |
| `minisql.zookeeper.connect-string` | `127.0.0.1:2181` | ZooKeeper 地址 |
| `minisql.zookeeper.registry-path` | `/db` | ZK 服务注册路径 |

可通过 JVM 参数覆盖，例如：
```
-Dminisql.master.port=12346
```

---

## 10. 源码索引

| 组件 | 文件 | 说明 |
|------|------|------|
| 协议编解码 | `minisql-protocol/.../codec/LineProtocol.java` | 所有协议消息的编码/解码工具 |
| 命令动词枚举 | `minisql-protocol/.../command/CommandVerb.java` | 合法 verb 定义 |
| 命令来源枚举 | `minisql-protocol/.../command/CommandSource.java` | `client/master/region` |
| 路由信息 | `minisql-protocol/.../command/master/RouteInfo.java` | 主副 Region 路由记录 |
| SQL 解析器 | `minisql-protocol/.../parser/SqlCommandParser.java` | SQL → SqlRequest（提取表名和操作类型） |
| Client 主循环 | `minisql-client/.../cli/ClientShell.java` | 命令行交互、双写逻辑 |
| 路由缓存 | `minisql-client/.../cache/RouteCache.java` | 表名→Region 本地缓存 |
| Client 路由器 | `minisql-client/.../router/QueryRouter.java` | 缓存+Master 查询决策 |
| Master 通信 | `minisql-client/.../masterlink/MasterClient.java` | Client 侧 Master 连接封装 |
| Region 通信 | `minisql-client/.../regionlink/RegionClient.java` | Client 侧 Region 连接封装 |
| Master 启动 | `minisql-master/.../bootstrap/MasterServerApplication.java` | Master 入口 |
| Master Socket | `minisql-master/.../socket/MasterSocketServer.java` | 监听 12345，分发请求 |
| Client 请求处理 | `minisql-master/.../handler/client/ClientCommandHandler.java` | 路由查询/建表 |
| Region 请求处理 | `minisql-master/.../handler/region/RegionCommandHandler.java` | 状态上报处理 |
| 元数据中心 | `minisql-master/.../metadata/TableManager.java` | 内存路由表 |
| 生命周期管理 | `minisql-master/.../strategy/RegionLifecycleService.java` | Region 上下线策略 |
| 主动下发命令 | `minisql-master/.../strategy/RegionCommandSender.java` | Master→Region recover/copy |
| ZK 服务发现 | `minisql-master/.../zk/ZookeeperRegionRegistry.java` | 监听 Region 上下线 |

---

## 11. 测试命令与预期输出

### 11.1 编译并运行所有单元测试

```bash
# 在项目根目录执行
mvn -pl minisql-protocol,minisql-common,minisql-master,minisql-client -am clean test
```

**预期输出**（关键行）：

```
Tests run: 17, Failures: 0, Errors: 0  ← protocol 模块（LineProtocolTest 14 + SqlCommandParserTest 3）
Tests run: 19, Failures: 0, Errors: 0  ← master 模块（RegionCommandHandlerTest 7 + ClientCommandHandlerTest 9 + TableManagerTest 3）
Tests run: 10, Failures: 0, Errors: 0  ← client 模块（RouteCacheTest 10）
BUILD SUCCESS
```

---

### 11.2 单独运行各模块测试

```bash
# 仅测试 protocol 模块
mvn -pl minisql-protocol -am clean test

# 仅测试 master 模块
mvn -pl minisql-master -am clean test

# 仅测试 client 模块
mvn -pl minisql-client -am clean test
```

---

### 11.3 手动 telnet 测试（需要 Master 已启动）

```bash
# 启动 Master（需要 ZooKeeper，或忽略 ZK 连接失败继续运行）
mvn -pl minisql-master -am install -DskipTests -q
mvn -f minisql-master/pom.xml exec:java
```

**测试 SHOW TABLES**（无 Region 已注册时）：

```bash
echo "[client] show tables" | nc 127.0.0.1 12345
```

预期输出：
```
[master] show
```

**测试 CREATE 但无 Region**：

```bash
echo "[client] create users" | nc 127.0.0.1 12345
```

预期输出：
```
[master] error At least two healthy regions are required to create table users
```

**测试 SELECT 不存在的表**：

```bash
echo "[client] select ghost" | nc 127.0.0.1 12345
```

预期输出：
```
[master] error Table does not exist: ghost
```

**模拟 Region 上报（两个 Region，无表）再建表**：

```bash
# 终端1：模拟 Region A 上报空表列表
echo "[region] recover" | nc 127.0.0.1 12345
# 预期：[master] ok recover accepted

# 终端2：模拟 Region B 上报空表列表（注意：NC 只能模拟单连接，IP 均为 127.0.0.1，
# 实际联调需要两台不同 IP 的机器）
echo "[region] recover" | nc 127.0.0.1 12345
# 预期：[master] ok recover accepted

# 此时 Master 认为 127.0.0.1 是唯一的 Region（两次来自同一 IP）
# 如需测试双 Region 分配，需在不同机器上分别启动 Region

# 测试建表（此时只有 1 个唯一 IP，仍会报错）
echo "[client] create users" | nc 127.0.0.1 12345
# 预期：[master] error At least two healthy regions are required to create table users
```

> **注意**：本地 `nc` 测试时，所有连接的对端 IP 均为 `127.0.0.1`，Master 无法区分不同 Region。  
> 完整联调需要部署在不同 IP 的机器或使用不同网卡地址。

---

### 11.4 启动 Client（依赖 Master 已运行）

```bash
mvn -pl minisql-client -am install -DskipTests -q
mvn -f minisql-client/pom.xml exec:java
```

**预期输出**：

```
MiniSQL Client started. Type SQL ending with ;;, or exit/quit to leave.
minisql> 
```

**输入 SQL 示例**：

```sql
minisql> show tables;;
```

预期（无表）：
```
No tables.
```

```sql
minisql> select * from users;;
```

预期（路由不存在）：
```
ERROR: Table does not exist: users
```

```sql
minisql> exit
```

（退出）
