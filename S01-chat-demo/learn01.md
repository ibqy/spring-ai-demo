# S01-chat-demo：Spring AI 对话入门

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | 如何在 Spring Boot 项目中以最低成本接入大模型，实现 AI 对话能力 |
| **适用场景** | 智能客服、问答机器人、内容生成等一切需要 LLM 交互的应用起点 |
| **前置知识** | Spring Boot 基础、Maven 依赖管理、RESTful API |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────┐
│  1. 引入模型 Starter 依赖                                │
│  2. 配置 API Key（application.yml / 环境变量）            │
│  3. 注入 ChatModel Bean                                 │
│  4. 构建 Prompt（封装用户消息）                           │
│  5. 调用 call()（同步）或 stream()（流式）               │
│  6. 解析 ChatResponse 获取模型回复                       │
└─────────────────────────────────────────────────────────┘
```

## 三、核心知识点（⭐ 重点）

### ⭐ 1. ChatModel —— 最核心的抽象接口

`ChatModel` 是 Spring AI 对**所有大模型**的统一抽象，无论底层是智谱、OpenAI 还是阿里百炼，上层调用方式完全一致。

```java
// 同步调用：传入字符串，直接返回 String
String reply = chatModel.call("你好，介绍一下自己");
```

> **重点理解**：`call(String)` 是语法糖，底层自动完成 `String → Prompt → HTTP请求 → 响应解析 → String` 全流程。

### ⭐ 2. Prompt + Message 体系

```java
var prompt = new Prompt(new UserMessage(message));
chatModel.stream(prompt);  // 返回 Flux<ChatResponse>
```

| 类 | 职责 | 重要程度 |
|---|---|---|
| `Prompt` | 封装一次请求的消息集合 + 参数选项 | ⭐⭐⭐ |
| `UserMessage` | 用户输入消息 | ⭐⭐⭐ |
| `SystemMessage` | 系统指令（设定角色/行为） | ⭐⭐⭐ |
| `ChatResponse` | 模型响应，包含 Generation 列表 | ⭐⭐ |
| `Generation` | 单次生成结果（含文本 + 元数据） | ⭐⭐ |

### ⭐ 3. 流式调用（SSE）

```java
@GetMapping(value = "/ai/generateStream", produces = "text/event-stream")
public Flux<ChatResponse> generateStream(String message) {
    var prompt = new Prompt(new UserMessage(message));
    return chatModel.stream(prompt);
}
```

- 接口声明 `produces = "text/event-stream"` → 浏览器以 SSE 协议接收
- 返回 `Flux<ChatResponse>`，每个元素是一小段增量文本
- 依赖 Spring WebFlux 的响应式支持

### 4. 自动装配机制

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-zhipuai</artifactId>
</dependency>
```

```yaml
spring:
  ai:
    zhipuai:
      api-key: ${ZHIPUAI_API_KEY}
```

引入 Starter 后，Spring Boot 自动创建 `ZhiPuAiChatModel` Bean 并注入容器，**无需手动 new**。

## 四、完整示例代码

```java
@RestController
public class ChatController {

    private final ZhiPuAiChatModel chatModel;

    @Autowired
    public ChatController(ZhiPuAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    // 同步调用
    @GetMapping("/ai/generate")
    public Map generate(String message) {
        return Map.of("generation", chatModel.call(message));
    }

    // 流式调用（SSE）
    @GetMapping(value = "/ai/generateStream", produces = "text/event-stream")
    public Flux<ChatResponse> generateStream(String message) {
        var prompt = new Prompt(new UserMessage(message));
        return chatModel.stream(prompt);
    }
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **ChatClient（推荐）** | 高级流式 API，链式调用更优雅（见 S09） | 生产环境首选 |
| **直接 HTTP 调用** | 用 RestClient/WebClient 手动调 模型 API | 学习底层原理 |
| **LangChain4j** | 第三方 Java LLM 框架，API 风格不同 | 已有 LangChain 经验 |
| **SDK 直调** | 使用模型厂商官方 Java SDK（如 DashScope SDK） | 需要厂商特有功能 |

> **建议**：入门用 `ChatModel.call()`，生产用 `ChatClient`（S09 详解）。

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| 注入 ChatModel 报 NoSuchBean | 未引入对应 Starter 或未配置 api-key | 检查依赖和 yml |
| 流式接口无响应 | 缺少 WebFlux 依赖或 produces 未声明 | 添加 `spring-boot-starter-webflux` |
| API Key 泄露 | 硬编码在代码中 | 使用环境变量 `${ZHIPUAI_API_KEY}` |

## 七、本节小结

```
核心收获：
✅ 理解 ChatModel 是 Spring AI 的统一模型抽象
✅ 掌握 call() 同步调用 和 stream() 流式调用
✅ 理解 Prompt → ChatResponse 的请求响应模型
✅ 了解 Starter 自动装配机制

下一步学习：S02-prompt-demo → 掌握提示词工程
```
