# S05-self-model：自定义模型接入

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | Spring AI 官方未提供某些模型的 Starter（如讯飞星火），如何自己实现 ChatModel 接口接入 |
| **适用场景** | 对接私有模型、新发布的模型、官方尚未适配的第三方模型 |
| **前置知识** | S01 ChatModel 抽象、RestClient/WebClient HTTP 调用、JSON 序列化 |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────┐
│  1. 分析目标模型的 API 文档（请求/响应格式）              │
│  2. 创建自定义类实现 ChatModel 接口                      │
│  3. 实现 call(Prompt) 方法：                             │
│     a. Prompt → 第三方请求体（格式转换）                  │
│     b. 发送 HTTP 请求（RestClient）                      │
│     c. 第三方响应 → Spring AI ChatResponse（格式转换）    │
│  4. 实现 getDefaultOptions() 返回默认参数                │
│  5. 注册为 Spring Bean（@Component）                     │
│  6. 像使用官方模型一样使用（ChatClient/Advisor 全兼容）    │
└─────────────────────────────────────────────────────────┘
```

## 三、核心知识点（⭐ 重点）

### ⭐ 1. 实现 ChatModel 接口（核心）

```java
@Component
public class SparkLiteModel implements ChatModel {

    @Override
    public ChatOptions getDefaultOptions() {
        return ChatOptions.builder().model(model).build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        // 1. 将 Spring AI 的 Prompt 转为第三方 API 的请求体
        Map<String, Object> reqBody = buildRequestBody(prompt);
        
        // 2. 通过 RestClient 发送 HTTP 请求
        String res = restClient.post().body(reqBody).retrieve().body(String.class);
        
        // 3. 将第三方响应转为 Spring AI 的 ChatResponse
        return convertToChatResponse(res);
    }
}
```

> **核心思想**：ChatModel 是一个适配器模式的接口，你只需要做好"双向格式转换"。

### ⭐ 2. RestClient 构建 HTTP 调用

```java
this.restClient = RestClient.builder()
    .baseUrl("https://spark-api-open.xf-yun.com/v1/chat/completions")
    .defaultHeaders(h -> {
        h.setBearerAuth(apiKey);
        h.setContentType(MediaType.APPLICATION_JSON);
    }).build();

String res = restClient.post().body(reqBody).retrieve().body(String.class);
```

### ⭐ 3. 响应转换（最关键的一步）

```java
// 第三方 JSON → 自定义 POJO
SparkPOJO.ChatCompletionChunk chunk = JsonUtil.fromStr(res, ...);

// 自定义 POJO → Spring AI Generation
List<Generation> generations = POJOConvert.generationList(chunk);

// 组装 ChatResponse
ChatResponse response = new ChatResponse(generations, metadata);
```

**转换要点**：
- 提取模型回复文本 → `AssistantMessage`
- 封装为 `Generation` 对象
- 组装 `ChatResponse`（可附带 metadata 如 token 用量）

### 4. 配置注入

```java
@Value("${spring.ai.spark.api-key:}")
private String apiKey;

@Value("${spring.ai.spark.chat.options.model:lite}")
private String model;
```

### 5. 与生态无缝配合

```java
// 自定义模型注入后，ChatClient、Advisor 等全部可用
ChatClient client = ChatClient.builder(sparkLiteModel)
    .defaultSystem("你是李白")
    .defaultAdvisors(new SimpleLoggerAdvisor())
    .build();
```

> **这就是接口抽象的威力**：只要实现 `ChatModel`，整个 Spring AI 生态（ChatClient、Advisor、Memory、Tool Calling）全部兼容。

## 四、完整示例代码

```java
@Component
public class SparkLiteModel implements ChatModel {

    private final RestClient restClient;
    
    @Value("${spring.ai.spark.chat.options.model:lite}")
    private String model;

    public SparkLiteModel(@Value("${spring.ai.spark.api-key:}") String apiKey) {
        this.restClient = RestClient.builder()
            .baseUrl("https://spark-api-open.xf-yun.com/v1/chat/completions")
            .defaultHeaders(h -> {
                h.setBearerAuth(apiKey);
                h.setContentType(MediaType.APPLICATION_JSON);
            }).build();
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ChatOptions.builder().model(model).build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Map<String, Object> reqBody = buildRequestBody(prompt);
        String res = restClient.post().body(reqBody).retrieve().body(String.class);
        return convertToChatResponse(res);
    }
    
    private Map<String, Object> buildRequestBody(Prompt prompt) {
        // 将 prompt.getInstructions() 转为第三方消息格式
        List<Map<String, String>> messages = prompt.getInstructions().stream()
            .map(msg -> Map.of("role", msg.getMessageType().getValue(), 
                               "content", msg.getText()))
            .toList();
        return Map.of("model", model, "messages", messages);
    }
    
    private ChatResponse convertToChatResponse(String json) {
        // 解析第三方响应 → Generation → ChatResponse
        // ...具体实现根据第三方 API 格式
    }
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **OpenAI 兼容接口（推荐）** | 若模型提供 OpenAI 风格 API，直接用 OpenAI Starter 改 base-url（见 S15） | 大多数国内模型首选 |
| **Spring AI 官方 Starter** | 检查是否已有对应模型的 Starter | 主流模型优先查看官方支持 |
| **自定义 ChatModel（本节）** | 完全自己实现接口 | 无 OpenAI 兼容接口的模型 |
| **代理网关** | 部署 LiteLLM/OneAPI 等网关统一转为 OpenAI 格式 | 多模型统一管理 |

> **优先级建议**：官方 Starter > OpenAI 兼容接口 > 自定义实现 > 代理网关

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| ChatClient 调用报错 | call() 返回的 ChatResponse 结构不完整 | 确保 Generation 和 AssistantMessage 非空 |
| Tool Calling 不生效 | 自定义模型未处理 tool_calls 响应 | 需要额外解析工具调用逻辑 |
| 流式调用不支持 | 只实现了 call() 未实现 stream() | 需要额外实现 StreamingChatModel 接口 |
| 与 Memory Advisor 冲突 | 响应格式不标准导致消息存储异常 | 确保 metadata 正确填充 |

## 七、本节小结

```
核心收获：
✅ 理解 ChatModel 是适配器模式，核心是"双向格式转换"
✅ 掌握 RestClient 调用第三方 HTTP API
✅ 掌握 第三方响应 → ChatResponse 的转换链路
✅ 理解实现接口后即可享受 Spring AI 全生态能力

下一步学习：S06-function-tool → 让大模型调用外部工具
```
