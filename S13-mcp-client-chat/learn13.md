# S13-mcp-client-chat：MCP Client 集成对话

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | 如何在 Spring AI 应用中作为 MCP Client，连接远程 MCP Server 并将其工具集成到 AI 对话中 |
| **适用场景** | AI 应用调用外部工具服务、多 MCP Server 聚合、构建带工具能力的聊天系统 |
| **前置知识** | S07 MCP Server、S06 Function Tool、S04 Chat Memory |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────┐
│  1. 引入 MCP Client Starter 依赖                         │
│  2. 配置远程 MCP Server 连接地址（application.yml）       │
│  3. Starter 自动创建 McpClient + ToolCallbackProvider     │
│  4. 将 ToolCallbackProvider 注入 ChatClient               │
│  5. 对话时模型自动发现并调用远程 MCP 工具                  │
└─────────────────────────────────────────────────────────┘
```

## 三、核心知识点（⭐ 重点）

### ⭐ 1. MCP Client 配置（零代码连接）

```yaml
spring:
  ai:
    mcp:
      client:
        sse:
          connections:
            my-server:
              url: http://localhost:8080  # MCP Server 地址
```

> 只需配置 URL，Starter 自动完成：连接建立 → 工具发现 → 注册为 ToolCallback。

### ⭐ 2. 一行代码集成所有 MCP 工具

```java
public ChatController(ChatModel chatModel, ToolCallbackProvider toolCallbackProvider) {
    this.chatClient = ChatClient.builder(chatModel)
        .defaultToolCallbacks(toolCallbackProvider)  // 所有远程 MCP 工具自动可用
        .build();
}
```

- `ToolCallbackProvider` 由 MCP Client Starter 自动注入
- 包含所有已连接 MCP Server 暴露的全部工具
- 模型对话时自动判断是否需要调用这些工具

### ⭐ 3. 直接调用 MCP 工具（不经过大模型）

```java
@Autowired
private List<McpAsyncClient> mcpClients;

// 直接调用指定工具
Mono<McpSchema.CallToolResult> result = mcpClients.get(0).callTool(
    new McpSchema.CallToolRequest("getTimeByZoneId", Map.of("area", "Asia/Shanghai"))
);
```

适用场景：确定性调用（不需要模型决策时直接调工具）。

### 4. 对话记忆集成

```java
ChatClient.builder(chatModel)
    .defaultToolCallbacks(toolCallbackProvider)
    .defaultAdvisors(
        MessageChatMemoryAdvisor.builder(
            MessageWindowChatMemory.builder().build()).build())
    .build();
```

- 工具调用 + 对话记忆可同时生效
- `MessageWindowChatMemory`：限制历史消息条数，防止 Token 溢出

### 5. 前端 HTMX 局部刷新（附加）

```java
@PostMapping("/ask")
public HtmxResponse chat(String message, Model model) {
    model.addAttribute("response", chatClient.prompt(message).call().content());
    return HtmxResponse.builder().view("chat :: chatFragment").build();
}
```

- 无需写 JavaScript 即可实现异步局部更新
- 依赖 `htmx-spring-boot-thymeleaf`

## 四、完整示例代码

```java
@Controller
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatModel chatModel, ToolCallbackProvider toolCallbackProvider) {
        this.chatClient = ChatClient.builder(chatModel)
            .defaultToolCallbacks(toolCallbackProvider)
            .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(
                    MessageWindowChatMemory.builder().build()).build())
            .build();
    }

    @PostMapping("/ask")
    public HtmxResponse chat(String message, Model model) {
        String response = chatClient.prompt(message).call().content();
        model.addAttribute("response", response);
        return HtmxResponse.builder().view("chat :: chatFragment").build();
    }
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **本地 Function Tool（S06）** | 工具与 AI 应用在同一进程 | 工具不需要跨应用共享 |
| **REST API 手动调用** | 在工具方法中用 RestClient 调远程服务 | 少量固定接口 |
| **多 MCP Server 聚合** | 配置多个 connections，工具自动合并 | 微服务架构，各服务暴露各自工具 |
| **Spring AI Alibaba MCP** | 支持 Nacos 注册发现的 MCP 方案 | 企业级服务治理 |

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| ToolCallbackProvider 注入失败 | 未引入 mcp-client Starter | 添加 `spring-ai-starter-mcp-client` |
| 连接 MCP Server 超时 | Server 未启动或 URL 错误 | 确认 Server 运行且 /sse 可访问 |
| 工具调用无结果 | 模型未选择调用工具 | 优化用户提问或工具 description |
| 多个 Server 工具名冲突 | 不同 Server 有同名工具 | 确保工具名全局唯一 |

## 七、本节小结

```
核心收获：
✅ 掌握 MCP Client 的零代码配置连接
✅ 掌握 ToolCallbackProvider 自动注入远程工具
✅ 理解 MCP Client + ChatClient 的集成模式
✅ 了解直接调用 MCP 工具（绕过模型决策）

下一步学习：S15-openai-style-model → 对接 OpenAI 兼容接口
```
