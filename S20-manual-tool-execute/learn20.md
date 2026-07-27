# S20-manual-tool-execute：手动控制工具执行

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | 默认 Spring AI 自动执行工具，开发者无法干预。如何手动控制工具执行链路，插入自定义逻辑 |
| **适用场景** | 工具调用前权限校验、调用后结果修改/审计、Human-in-the-Loop 人工确认、工具调用日志 |
| **前置知识** | S06 Function Tool、S09 ChatClient、S10 Advisor |

## 二、标准处理流程

### 自动模式（默认）
```
用户输入 → LLM → 返回 tool_calls → 框架自动执行 → 结果回传 LLM → 最终回答
```

### 手动模式（本节核心）
```
┌─────────────────────────────────────────────────────────┐
│  1. 设置 internalToolExecutionEnabled(false)             │
│  2. 第 1 次调用 LLM → 获取 tool_calls 请求              │
│  3. 开发者手动执行工具（可加自定义逻辑）                  │
│  4. 组装 ToolResponseMessage（工具结果）                  │
│  5. 第 2 次调用 LLM（传入完整消息链）→ 获取最终回答       │
└─────────────────────────────────────────────────────────┘
```

## 三、核心知识点（⭐ 重点）

### ⭐ 1. 禁用自动执行（关键配置）

```java
ToolCallingChatOptions options = ToolCallingChatOptions.builder()
    .internalToolExecutionEnabled(false)  // 关键：禁用自动执行
    .toolContext(toolContextData)
    .build();
```

### ⭐ 2. 手动执行完整流程

```java
// 第 1 次调用：获取工具调用请求
ChatResponse chatResponse = chatClient.prompt(new Prompt(msg, options))
    .toolCallbacks(tools).call().chatResponse();
AssistantMessage assistantMsg = chatResponse.getResult().getOutput();

// 判断是否有工具调用
if (!CollectionUtils.isEmpty(assistantMsg.getToolCalls())) {
    List<ToolResponseMessage.ToolResponse> toolResults = new ArrayList<>();
    
    // 手动执行每个工具
    for (var call : assistantMsg.getToolCalls()) {
        for (ToolCallback callback : tools) {
            if (callback.getToolDefinition().name().equals(call.name())) {
                // ★ 这里可以插入自定义逻辑：权限校验、日志、参数修改等
                String toolResult = callback.call(call.arguments(), 
                    new ToolContext(toolContextData));
                
                toolResults.add(new ToolResponseMessage.ToolResponse(
                    call.id(), call.name(), toolResult));
            }
        }
    }

    // 第 2 次调用：将工具结果给模型总结
    ToolResponseMessage toolMsg = ToolResponseMessage.builder()
        .responses(toolResults).build();
    
    ChatResponse finalResp = chatClient.prompt(new Prompt(List.of(
        new UserMessage(msg),      // 原始问题
        assistantMsg,              // AI 的工具调用请求
        toolMsg                    // 工具执行结果
    ))).call().chatResponse();
    
    String answer = finalResp.getResult().getOutput().getText();
}
```

### ⭐ 3. ToolContext 工具上下文

```java
// 构建上下文数据
Map<String, Object> toolContextData = new HashMap<>();
toolContextData.put("sessionId", "demo-session-123");
toolContextData.put("userId", "demo-user-456");

// 工具方法中获取上下文
@Tool(description = "查询天气")
String getWeather(@ToolParam(description = "城市") String city, ToolContext context) {
    String userId = (String) context.getContext().get("userId");
    // 可以基于 userId 做权限判断、个性化等
    return weatherService.query(city, userId);
}
```

> **核心价值**：ToolContext 让工具方法能感知调用环境（谁在调、从哪来），实现精细化控制。

### 4. MethodToolCallbackProvider 批量注册

```java
ToolCallback[] tools = MethodToolCallbackProvider.builder()
    .toolObjects(quizTools, weatherTools)  // 多个工具类
    .build()
    .getToolCallbacks();
```

### 5. 自动 vs 手动对比

| 对比项 | 自动模式 | 手动模式 |
|--------|----------|----------|
| 配置 | `internalToolExecutionEnabled(true)`（默认） | `internalToolExecutionEnabled(false)` |
| LLM 调用次数 | 框架自动处理（内部多次） | 开发者显式控制（至少 2 次） |
| 自定义能力 | 无（黑盒） | 完全可控 |
| 复杂度 | ⭐ | ⭐⭐⭐ |
| 适用场景 | 简单工具调用 | 需要审计/确认/修改的场景 |

## 四、完整示例代码

```java
@RestController
public class QaController {

    private final ChatClient chatClient;
    private final ToolCallback[] tools;

    public QaController(ChatModel chatModel, QuizTools quizTools, WeatherTools weatherTools) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.tools = MethodToolCallbackProvider.builder()
            .toolObjects(quizTools, weatherTools)
            .build()
            .getToolCallbacks();
    }

    @GetMapping("/qa")
    public Map<String, Object> qa(String msg) {
        Map<String, Object> toolContextData = Map.of("userId", "demo-user");
        
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
            .internalToolExecutionEnabled(false)
            .toolContext(toolContextData)
            .build();

        // 第 1 次调用
        ChatResponse resp = chatClient.prompt(new Prompt(msg, options))
            .toolCallbacks(tools).call().chatResponse();
        AssistantMessage assistantMsg = resp.getResult().getOutput();

        if (CollectionUtils.isEmpty(assistantMsg.getToolCalls())) {
            return Map.of("answer", assistantMsg.getText(), "toolUsed", false);
        }

        // 手动执行工具
        List<ToolResponseMessage.ToolResponse> results = new ArrayList<>();
        for (var call : assistantMsg.getToolCalls()) {
            for (ToolCallback cb : tools) {
                if (cb.getToolDefinition().name().equals(call.name())) {
                    String result = cb.call(call.arguments(), new ToolContext(toolContextData));
                    results.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), result));
                }
            }
        }

        // 第 2 次调用
        ToolResponseMessage toolMsg = ToolResponseMessage.builder().responses(results).build();
        ChatResponse finalResp = chatClient.prompt(new Prompt(List.of(
            new UserMessage(msg), assistantMsg, toolMsg
        ))).call().chatResponse();

        return Map.of(
            "answer", finalResp.getResult().getOutput().getText(),
            "toolUsed", true,
            "tools", assistantMsg.getToolCalls().stream().map(c -> c.name()).toList()
        );
    }
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **Advisor 拦截（S10）** | 在调用链层面拦截，无需手动管理消息 | 只需日志/监控，不需修改工具结果 |
| **ToolMetadata.returnDirect** | 工具结果直接返回用户，不经模型总结 | 工具结果即最终答案 |
| **Human-in-the-Loop** | 工具调用前暂停，等用户确认后继续 | 高风险操作需人工审批 |
| **Agent 框架** | LangGraph4j 等框架管理复杂工具编排 | 多步骤、多工具的复杂任务 |

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| 工具名重复报错 | 不同类中有同名 @Tool 方法 | 确保工具名全局唯一 |
| 第 2 次调用结果异常 | 消息列表顺序不对 | 严格按 User→Assistant→Tool 顺序 |
| ToolContext 获取为 null | 调用 callback.call() 时未传 ToolContext | 确保传入 new ToolContext(data) |
| 模型不返回 tool_calls | 问题不需要工具或 description 不清 | 优化 @Tool description |

## 七、本节小结

```
核心收获：
✅ 掌握 internalToolExecutionEnabled(false) 禁用自动执行
✅ 掌握手动模式的两次 LLM 调用流程
✅ 掌握 ToolContext 传递调用上下文
✅ 理解消息链组装顺序（User → Assistant → ToolResponse）
✅ 理解手动模式的适用场景：审计、确认、修改

恭喜完成 S01-S20 全部基础教程！
进阶学习：advance-projects → 对话持久化 / LangGraph4j Agent
```
