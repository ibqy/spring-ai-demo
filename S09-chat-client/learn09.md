# S09-chat-client：ChatClient 全面详解

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | ChatModel 是底层接口，使用繁琐。ChatClient 是 Spring AI 推荐的高级客户端 API，如何全面掌握 |
| **适用场景** | 所有生产环境的 AI 对话交互（替代直接操作 ChatModel） |
| **前置知识** | S01 ChatModel、S02 提示词、S03 结构化输出、S04 记忆 |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────┐
│  1. 通过 ChatClient.builder(chatModel) 构建客户端         │
│  2. 配置默认行为（defaultSystem/defaultOptions/Advisors） │
│  3. 每次调用：.prompt() → 设置消息 → .call()/.stream()   │
│  4. 获取结果：.content() / .entity() / .chatResponse()   │
└─────────────────────────────────────────────────────────┘
```

## 三、核心知识点（⭐ 重点）

### ⭐ 1. 构建 ChatClient（两种模式）

```java
// 简单构建
ChatClient chatClient = ChatClient.builder(chatModel).build();

// 带默认配置（推荐：为不同场景创建专用客户端）
ChatClient poemClient = ChatClient.builder(chatModel)
    .defaultSystem("你现在扮演著名的诗人{role}")       // 默认系统提示词
    .defaultOptions(ChatOptions.builder().maxTokens(500).build())  // 默认参数
    .defaultAdvisors(new SimpleLoggerAdvisor())       // 默认 Advisor
    .build();
```

> **最佳实践**：按业务场景创建不同的 ChatClient 实例（如客服Client、翻译Client、创作Client）。

### ⭐ 2. 结构化输出

```java
// 单个对象
Poem poem = chatClient.prompt()
    .system("你是唐代诗人")
    .user("写一首关于春天的诗")
    .call()
    .entity(Poem.class);

// 泛型列表
List<Poem> poems = chatClient.prompt()
    .user("写3首关于四季的诗")
    .call()
    .entity(new ParameterizedTypeReference<List<Poem>>() {});
```

### ⭐ 3. 流式调用

```java
@GetMapping(produces = "text/event-stream")
public Flux<String> streamGen(String msg) {
    return chatClient.prompt().user(msg).stream().content();
}
```

### 4. 流式 + 结构化（先收集再转换）

```java
var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<List<Poem>>() {});

Flux<String> flux = chatClient.prompt()
    .user(u -> u.text("{msg}.\n{format}")
        .param("msg", msg)
        .param("format", converter.getFormat()))
    .stream().content();

// 收集完整响应后转换
String content = flux.collectList().block().stream().collect(Collectors.joining());
List<Poem> result = converter.convert(content);
```

### 5. 提示词模板参数化

```java
chatClient.prompt()
    .system(u -> u.text("你扮演{role}").param("role", role))
    .user(u -> u.text("我的问题是：{msg}").params(Map.of("msg", msg)))
    .call().content();
```

### 6. 动态添加 Advisor

```java
chatClient.prompt().user(msg)
    .advisors(
        new SimpleLoggerAdvisor(),
        MessageChatMemoryAdvisor.builder(chatMemory).build())
    .call().content();
```

- Advisor 可在每次请求时动态指定，不必全局配置
- 适合按条件启用不同增强逻辑

### 7. ChatClient API 全景图

| 阶段 | 方法 | 说明 |
|------|------|------|
| 构建 | `builder(chatModel)` | 创建构建器 |
| 默认配置 | `defaultSystem()` / `defaultOptions()` / `defaultAdvisors()` | 全局默认 |
| 消息设置 | `.prompt()` / `.user()` / `.system()` | 设置本次消息 |
| 工具 | `.tools()` / `.toolCallbacks()` | 注册工具 |
| Advisor | `.advisors()` | 动态增强 |
| 调用 | `.call()` / `.stream()` | 同步/流式 |
| 结果 | `.content()` / `.entity()` / `.chatResponse()` | 获取结果 |

## 四、完整示例代码

```java
@RestController
public class PoemController {

    private final ChatClient poemClient;

    public PoemController(ChatModel chatModel) {
        this.poemClient = ChatClient.builder(chatModel)
            .defaultSystem("你是唐代著名诗人{role}")
            .defaultOptions(ChatOptions.builder().maxTokens(500).build())
            .build();
    }

    // 同步 + 结构化
    @GetMapping("/poem")
    public Poem genPoem(String msg, String role) {
        return poemClient.prompt()
            .system(sp -> sp.param("role", role))
            .user(msg)
            .call()
            .entity(Poem.class);
    }

    // 流式输出
    @GetMapping(value = "/poem/stream", produces = "text/event-stream")
    public Flux<String> streamPoem(String msg) {
        return poemClient.prompt().user(msg).stream().content();
    }
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **ChatModel 直接调用** | 底层 API，更灵活但更繁琐 | 需要极致控制底层行为 |
| **Spring AI Alibaba ChatClient** | 阿里增强版，支持 Graph 工作流 | 复杂 Agent 编排 |
| **LangChain4j AiServices** | 声明式接口 + 注解，类似 Feign | 偏好接口声明式风格 |
| **自行封装 Service 层** | 在 ChatModel 上封装业务逻辑 | 特殊定制需求 |

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| 每次请求都创建 ChatClient | 性能浪费 | 构建一次，复用实例 |
| defaultSystem 模板参数未传 | 调用时忘记 `.system(sp -> sp.param(...))` | 确保模板变量有对应值 |
| stream() 后调用 content() 为空 | 流已被消费 | Flux 只能订阅一次 |
| entity() 转换失败 | 模型输出非标准 JSON | 配合 @JsonPropertyDescription |

## 七、本节小结

```
核心收获：
✅ 掌握 ChatClient 构建与默认配置（生产首选 API）
✅ 掌握 同步/流式/结构化 三种调用模式
✅ 掌握 提示词模板参数化
✅ 理解 Advisor 动态注入机制
✅ 建立 ChatClient API 全景认知

下一步学习：S10-cost-advise → 自定义 Advisor 实现调用增强
```
