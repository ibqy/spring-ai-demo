# S04-chat-memory：多轮对话记忆

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | 大模型本身无状态，每次请求独立。如何让模型"记住"历史对话，实现上下文连续交互 |
| **适用场景** | 智能客服、多轮问答、角色扮演、任何需要上下文关联的对话系统 |
| **前置知识** | S01 ChatModel、S02 提示词、Advisor 概念 |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────┐
│  1. 创建 ChatMemory 实例（存储对话历史）                  │
│  2. 构建 Memory Advisor（决定如何注入历史）               │
│  3. 将 Advisor 注册到 ChatClient                        │
│  4. 每次调用时传入 CONVERSATION_ID（会话隔离）            │
│  5. Advisor 自动：读取历史 → 注入消息 → 调用模型 → 存储新消息 │
└─────────────────────────────────────────────────────────┘
```

## 三、核心知识点（⭐ 重点）

### ⭐ 1. MessageChatMemoryAdvisor —— 消息级记忆（最常用）

```java
ChatMemory chatMemory = MessageWindowChatMemory.builder().build();

ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(
        MessageChatMemoryAdvisor.builder(chatMemory).build())
    .build();
```

**工作原理**：
- 每次调用前，从 ChatMemory 检索该会话的历史消息
- 将历史消息作为**独立的 Message 对象**插入消息列表
- 模型看到完整的多轮对话：`[System, User1, AI1, User2, AI2, ..., UserN]`

### ⭐ 2. 多用户会话隔离（生产必备）

```java
chatClient.prompt()
    .user(msg)
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId))
    .call().content();
```

> **核心要点**：通过 `CONVERSATION_ID` 区分不同用户/会话，记忆互不干扰。

### ⭐ 3. PromptChatMemoryAdvisor —— 摘要级记忆

```java
PromptChatMemoryAdvisor.builder(chatMemory).build()
```

| 对比项 | MessageChatMemoryAdvisor | PromptChatMemoryAdvisor |
|--------|--------------------------|-------------------------|
| 注入方式 | 历史消息作为独立 Message | 历史摘要追加到系统提示词 |
| Token 消耗 | 高（完整历史） | 低（压缩摘要） |
| 上下文精度 | 高 | 中（有信息损失） |
| 适用场景 | 短对话、精度要求高 | 长对话、上下文窗口有限 |

### 4. 系统提示词模板化

```java
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultSystem("你现在是{role}，我们现在开始对话")
    .build();

// 调用时动态传入参数
chatClient.prompt()
    .system(sp -> sp.param("role", role))
    .user(msg)
    .call().content();
```

### 5. 日志 Advisor（调试利器）

```java
new SimpleLoggerAdvisor(
    ModelOptionsUtils::toJsonStringPrettyPrinter,
    ModelOptionsUtils::toJsonStringPrettyPrinter, 0)
```

- 打印完整的请求/响应 JSON，便于调试对话内容
- 生产环境应移除或降级日志级别

## 四、完整示例代码

```java
@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatModel chatModel) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
        this.chatClient = ChatClient.builder(chatModel)
            .defaultSystem("你是一个友好的AI助手")
            .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                new SimpleLoggerAdvisor())
            .build();
    }

    @GetMapping("/chat")
    public String chat(String msg, String user) {
        return chatClient.prompt()
            .user(msg)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user))
            .call().content();
    }
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **JDBC 持久化** | 对话历史存入 MySQL/H2（见 advance A01/A02） | 需要跨重启保留历史 |
| **Redis 持久化** | 对话历史存入 Redis（见 advance A03） | 高并发 + 需要过期策略 |
| **手动拼接消息** | 自己维护 List\<Message\> 每次传入 | 学习原理、极简场景 |
| **RAG 检索增强** | 将历史对话向量化，按相关性检索 | 超长对话、知识库问答 |
| **LangGraph4j** | 使用状态图管理对话流转（见 advance A04） | 复杂多轮对话流程 |

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| 模型不记得之前说的话 | 未传 CONVERSATION_ID，每次都是新会话 | 确保同一用户传相同 ID |
| Token 超限报错 | 历史消息太多超出上下文窗口 | 使用 MessageWindowChatMemory 限制条数 |
| 不同用户记忆串了 | CONVERSATION_ID 写死或重复 | 用 userId/sessionId 作为唯一标识 |
| 内存溢出 | 使用 InMemoryChatMemory 且无清理策略 | 生产用 Redis/JDBC + TTL |

## 七、本节小结

```
核心收获：
✅ 理解大模型无状态本质，记忆由客户端维护
✅ 掌握 MessageChatMemoryAdvisor 的使用（最核心）
✅ 掌握 CONVERSATION_ID 实现多用户隔离
✅ 理解消息级 vs 摘要级记忆的取舍

下一步学习：S05-self-model → 自定义模型接入 Spring AI
```
