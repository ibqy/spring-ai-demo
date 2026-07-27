# S07-mcp-server：MCP 协议服务端

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | 如何将本地工具以标准 MCP（Model Context Protocol）协议暴露，让任何 MCP 客户端都能发现并调用 |
| **适用场景** | 工具跨应用共享、为 Claude Desktop/Cursor 等 AI IDE 提供自定义工具、构建工具市场 |
| **前置知识** | S06 Function Tool、SSE 基础概念 |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────┐
│  1. 引入 MCP Server WebMVC Starter                       │
│  2. 用 @Tool 注解声明工具方法                             │
│  3. 通过 ToolCallbackProvider 注册工具到 MCP Server       │
│  4. 启动应用 → 自动暴露 /sse 和 /mcp/messages 端点       │
│  5. 外部 MCP Client 连接 → 自动发现工具 → 远程调用        │
└─────────────────────────────────────────────────────────┘
```

## 三、核心知识点（⭐ 重点）

### ⭐ 1. MCP 协议核心概念

| 概念 | 说明 |
|------|------|
| **MCP Server** | 工具提供方，暴露工具列表和执行能力 |
| **MCP Client** | 工具消费方（AI 应用），发现并调用远程工具 |
| **SSE 传输** | 基于 Server-Sent Events 的通信方式 |
| **工具发现** | 客户端连接后自动获取所有可用工具及其 Schema |

### ⭐ 2. @Tool 声明 MCP 工具（与 S06 完全一致）

```java
@Service
public class DateService {
    @Tool(description = "传入时区，返回对应时区的当前时间给用户")
    public String getTimeByZoneId(@ToolParam(description = "需要查询时间的时区") ZoneId area) {
        ZonedDateTime time = ZonedDateTime.now(area);
        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
```

### ⭐ 3. ToolCallbackProvider 注册（关键一步）

```java
@Configuration
public class ToolConfig {
    @Bean
    public ToolCallbackProvider tools(DateService dateService) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(dateService)
            .build();
    }
}
```

> **重点**：`MethodToolCallbackProvider` 扫描对象上的 `@Tool` 方法，自动注册为 MCP 可发现工具。

### 4. 自动暴露的端点

| 端点 | 作用 |
|------|------|
| `/sse` | SSE 连接入口，客户端通过此建立长连接 |
| `/mcp/messages` | 消息处理端点，接收工具调用请求 |

### 5. 配置

```yaml
spring:
  ai:
    mcp:
      server:
        name: my-mcp-server
        version: 1.0.0
```

### 6. 与 S06 Function Tool 的本质区别

| 对比项 | S06 Function Tool | S07 MCP Server |
|--------|-------------------|----------------|
| 调用方 | 同一应用内的大模型 | 外部任意 MCP Client |
| 协议 | Spring AI 内部机制 | 标准 MCP 协议（SSE） |
| 工具发现 | 代码显式传入 | 客户端自动发现 |
| 部署 | 与 AI 应用一体 | 独立部署，可复用 |
| 跨语言 | 仅 Java | 任何支持 MCP 的语言/工具 |

## 四、完整示例代码

```java
// 1. 工具服务
@Service
public class DateService {
    @Tool(description = "传入时区，返回对应时区的当前时间给用户")
    public String getTimeByZoneId(@ToolParam(description = "需要查询时间的时区") ZoneId area) {
        return ZonedDateTime.now(area).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}

// 2. 注册配置
@Configuration
public class ToolConfig {
    @Bean
    public ToolCallbackProvider tools(DateService dateService) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(dateService)
            .build();
    }
}

// 3. 启动类（无需额外代码，Starter 自动配置）
@SpringBootApplication
public class S07Application {
    public static void main(String[] args) {
        SpringApplication.run(S07Application.class, args);
    }
}
```

**客户端连接配置示例**（Claude Desktop `claude_desktop_config.json`）：
```json
{
  "mcpServers": {
    "my-server": {
      "url": "http://localhost:8080/sse"
    }
  }
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **MCP STDIO 传输** | 通过标准输入输出通信（非 HTTP） | 本地进程间通信 |
| **REST API + OpenAPI** | 传统 REST 接口 + Swagger 描述 | 非 AI 场景的工具暴露 |
| **gRPC 服务** | 高性能 RPC 协议 | 内部微服务间工具调用 |
| **Spring AI Alibaba MCP** | 阿里生态的 MCP 实现，支持 Nacos 注册发现 | 企业级 MCP 工具治理 |

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| 客户端连不上 | 端口/路径配置错误 | 确认 `/sse` 端点可访问 |
| 工具未被发现 | 未注册 ToolCallbackProvider Bean | 检查 @Configuration 是否生效 |
| SSE 连接断开 | 超时或代理切断长连接 | 配置 Nginx 的 proxy_read_timeout |
| 工具执行超时 | 工具方法耗时过长 | 异步化或增加超时配置 |

## 七、本节小结

```
核心收获：
✅ 理解 MCP 是 AI 工具调用的标准化协议
✅ 掌握 ToolCallbackProvider 注册机制（核心）
✅ 理解 MCP Server 与 Function Tool 的本质区别
✅ 了解 SSE 传输层的工作原理

下一步学习：S08-mcp-server-basic-auth → 为 MCP Server 添加鉴权
```
