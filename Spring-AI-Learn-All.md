# Spring AI 系列教程知识点汇总

> 本文档汇总了 S01~S20 所有项目的核心知识点，按学习顺序排列。

---

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


---

# S02-prompt-demo：提示词工程实战

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | 如何通过提示词（Prompt）工程精确控制大模型的行为、角色和输出格式 |
| **适用场景** | 角色扮演、领域专家设定、动态模板渲染、提示词复用与管理 |
| **前置知识** | S01 的 ChatModel 基础调用 |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────┐
│  1. 确定模型参数（model、temperature 等）                 │
│  2. 编写 SystemMessage 设定 AI 角色与行为边界            │
│  3. 使用模板引擎管理提示词（支持变量替换）                │
│  4. 组装 Prompt（SystemMessage + UserMessage + Options）  │
│  5. 调用模型获取响应                                     │
└─────────────────────────────────────────────────────────┘
```

## 三、核心知识点（⭐ 重点）

### ⭐ 1. ChatOptions —— 模型行为参数

```java
ZhiPuAiChatOptions.builder()
    .model(ZhiPuAiApi.ChatModel.GLM_4_Flash.getValue())
    .temperature(0.7d)     // 控制随机性：0=确定性输出，1=高度创造性
    .user("user_yihuihui") // 调用方标识（用于审计/配额）
    .build()
```

| 参数 | 作用 | 推荐值 |
|------|------|--------|
| `temperature` | 输出随机性，越低越保守 | 问答=0.3，创作=0.8 |
| `model` | 指定模型版本 | 按需求选择 |
| `maxTokens` | 最大输出 token 数 | 按需设置 |

### ⭐ 2. SystemMessage —— 角色设定（最常用）

```java
new Prompt(
    Arrays.asList(
        new SystemMessage("你现在是一个专注于给3-5岁儿童聊天的助手"),
        new UserMessage(message)
    ), options)
```

> **核心原则**：`SystemMessage` 决定 AI "是谁"，`UserMessage` 决定 AI "做什么"。

### ⭐ 3. SystemPromptTemplate —— 模板化提示词

```java
SystemPromptTemplate promptTemplate = new SystemPromptTemplate(
    "你来扮演{personality}的{aiRole}, 我来扮演{myRole}");

Message systemMsg = promptTemplate.createMessage(
    Map.of("personality", personality, "aiRole", aiRole, "myRole", myRole));
```

- 默认占位符语法：`{变量名}`
- 适合提示词结构固定、内容动态变化的场景

### 4. 自定义占位符（避免与 JSON 冲突）

```java
PromptTemplate promptTemplate = PromptTemplate.builder()
    .renderer(StTemplateRenderer.builder()
        .startDelimiterToken('<').endDelimiterToken('>').build())
    .template("你来扮演<personality>的<aiRole>")
    .build();
String text = promptTemplate.render(Map.of(...));
```

> **何时使用**：当提示词中包含 JSON 示例（大量花括号）时，默认 `{}` 占位符会冲突，改用 `<>` 包裹变量。

### 5. 外部文件加载模板（工程化实践）

```java
@Value("classpath:/prompts/system-message.st")
private Resource systemResource;

SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemResource);
Message text = systemPromptTemplate.createMessage(Map.of(...));
```

- 模板文件放在 `resources/prompts/` 目录下
- 好处：提示词与代码解耦，非开发人员也能维护

## 四、完整示例代码

```java
@GetMapping("/ai/role")
public String roleChat(String msg, String personality, String aiRole, String myRole) {
    ZhiPuAiChatOptions options = ZhiPuAiChatOptions.builder()
        .model(ZhiPuAiApi.ChatModel.GLM_4_Flash.getValue())
        .temperature(0.7d)
        .build();

    SystemPromptTemplate promptTemplate = new SystemPromptTemplate(
        "你来扮演{personality}的{aiRole}, 我来扮演{myRole}");
    Message systemMsg = promptTemplate.createMessage(
        Map.of("personality", personality, "aiRole", aiRole, "myRole", myRole));

    Prompt prompt = new Prompt(Arrays.asList(systemMsg, new UserMessage(msg)), options);
    return chatModel.call(prompt).getResult().getOutput().getText();
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **ChatClient.defaultSystem()** | 构建时设定默认系统提示词（见 S09） | 固定角色的专用客户端 |
| **Advisor 注入** | 通过 Advisor 在调用链中动态追加提示词 | 需要按条件切换提示词 |
| **数据库/配置中心管理** | 将提示词存入 DB 或 Nacos，运行时加载 | 需要热更新提示词 |
| **Prompt Flow 编排** | 使用 Spring AI Alibaba 的 Graph 工作流 | 多步骤复杂提示词链路 |

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| 模板变量未替换 | 变量名拼写不一致或 Map 缺少 key | 检查占位符与 Map key 完全匹配 |
| JSON 示例导致解析错误 | 默认 `{}` 与 JSON 花括号冲突 | 使用 `StTemplateRenderer` 自定义分隔符 |
| temperature=0 仍有差异 | 模型服务端实现差异 | 理解"确定性"是近似而非绝对 |

## 七、本节小结

```
核心收获：
✅ 掌握 ChatOptions 参数调优（temperature 是核心）
✅ 理解 SystemMessage 的角色设定作用
✅ 掌握 PromptTemplate 模板化提示词（含自定义分隔符）
✅ 学会从外部文件加载模板（工程化最佳实践）

下一步学习：S03-structured-output → 让模型返回 Java 对象
```


---

# S03-structured-output：结构化输出

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | 大模型默认返回纯文本，如何让返回结果自动映射为 Java 对象（Bean / List / Map） |
| **适用场景** | 数据提取、表单填充、API 对接、任何需要程序化处理模型输出的场景 |
| **前置知识** | S01 ChatModel 调用、S02 提示词基础、Java 泛型 |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────┐
│  1. 定义目标 Java 类（record / POJO）                    │
│  2. 生成 JSON Schema 格式约束（自动/手动）               │
│  3. 将格式约束拼入提示词，告知模型按格式输出              │
│  4. 调用模型获取 JSON 文本响应                           │
│  5. 反序列化为目标 Java 对象                             │
└─────────────────────────────────────────────────────────┘
```

> Spring AI 将步骤 2~5 封装为一行代码：`.call().entity(TargetClass.class)`

## 三、核心知识点（⭐ 重点）

### ⭐ 1. ChatClient.entity() —— 一行搞定（最推荐）

```java
ActorsFilms result = ChatClient.create(chatModel)
    .prompt(prompt)
    .call()
    .entity(ActorsFilms.class);
```

**底层原理**：
1. 根据目标类自动生成 JSON Schema
2. 在提示词末尾追加格式约束："请按以下 JSON 格式输出..."
3. 将模型返回的 JSON 字符串反序列化为对象

### ⭐ 2. 泛型类型映射（List\<Bean\>）

```java
List<ActorsFilms> list = chatClient.prompt()
    .user("列出3位动作演员及其电影")
    .call()
    .entity(new ParameterizedTypeReference<List<ActorsFilms>>() {});
```

> **重点**：Java 泛型擦除导致无法直接传 `List.class`，必须用 `ParameterizedTypeReference` 保留泛型信息。

### ⭐ 3. BeanOutputConverter —— 手动控制

```java
BeanOutputConverter<ActorsFilms> converter = new BeanOutputConverter<>(ActorsFilms.class);

// 步骤1：获取格式提示文本（拼入你的提示词中）
String format = converter.getFormat();

// 步骤2：将模型返回的 JSON 文本转为对象
ActorsFilms result = converter.convert(generation.getOutput().getText());
```

适用场景：需要自定义提示词结构，不想用 `entity()` 的默认行为。

### 4. MapOutputConverter

```java
MapOutputConverter mapConverter = new MapOutputConverter();
String format = mapConverter.getFormat();          // 格式提示
Map<String, Object> result = mapConverter.convert(text);  // 转换
```

### 5. ListOutputConverter

```java
ListOutputConverter listConverter = new ListOutputConverter(new DefaultConversionService());
List<String> result = listConverter.convert(text);
```

### 6. 辅助注解（提升输出质量）

```java
@JsonPropertyOrder({"actor", "movies"})  // 控制 JSON 字段顺序
record ActorsFilms(
    @JsonPropertyDescription("演员姓名") String actor,
    @JsonPropertyDescription("代表作品列表") List<String> movies
) {}
```

> `@JsonPropertyDescription` 不仅控制序列化，还会写入 JSON Schema，帮助模型理解每个字段含义。

## 四、完整示例代码

```java
// 定义目标结构
@JsonPropertyOrder({"actor", "movies"})
public record ActorsFilms(String actor, List<String> movies) {}

// 方式一：ChatClient 一行搞定
@GetMapping("/films")
public ActorsFilms getFilms(String actor) {
    return chatClient.prompt()
        .user("列出演员 " + actor + " 的代表电影")
        .call()
        .entity(ActorsFilms.class);
}

// 方式二：泛型列表
@GetMapping("/filmsList")
public List<ActorsFilms> getFilmsList() {
    return chatClient.prompt()
        .user("列出3位著名动作演员及其代表电影")
        .call()
        .entity(new ParameterizedTypeReference<List<ActorsFilms>>() {});
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **Function Calling** | 让模型调用一个"返回结构化数据"的工具（见 S06） | 需要模型主动决定何时输出结构化数据 |
| **手动 JSON 解析** | 提示词要求输出 JSON + Jackson 手动解析 | 需要极致控制提示词内容 |
| **模型原生 JSON Mode** | 部分模型支持 `response_format: json_object` | 模型原生支持时优先使用 |
| **OutputParser（LangChain 风格）** | 自定义解析器 + 重试机制 | 输出格式不稳定需要容错 |

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| 反序列化失败 | 模型输出包含 markdown 代码块标记 | 提示词强调"纯 JSON，不要 ```" |
| 字段为 null | 模型未正确理解字段含义 | 添加 `@JsonPropertyDescription` |
| 泛型转换报错 | 使用了 `List.class` 而非 ParameterizedTypeReference | 使用匿名内部类保留泛型 |
| 输出字段顺序不一致 | 模型自由决定顺序 | 使用 `@JsonPropertyOrder` |

## 七、本节小结

```
核心收获：
✅ 掌握 entity() 一行实现结构化输出（最推荐）
✅ 理解底层原理：JSON Schema 约束 + 自动反序列化
✅ 掌握 ParameterizedTypeReference 处理泛型
✅ 了解 @JsonPropertyDescription 提升输出质量

下一步学习：S04-chat-memory → 实现多轮对话记忆
```


---

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


---

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


---

# S06-function-tool：工具调用（Function Calling）

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | 大模型无法获取实时信息、执行计算或操作外部系统，如何让它自动调用开发者提供的工具 |
| **适用场景** | 实时数据查询（天气/时间/股价）、数据库操作、API 编排、任何需要"模型决策 + 程序执行"的场景 |
| **前置知识** | S01 ChatModel、S09 ChatClient 基础 |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────────┐
│  用户提问 → 模型分析意图 → 判断需要调用工具                    │
│  → 模型返回 tool_call（工具名 + 参数）                        │
│  → Spring AI 框架自动执行对应工具方法                          │
│  → 将工具执行结果返回给模型                                    │
│  → 模型基于工具结果生成最终回答                                │
└─────────────────────────────────────────────────────────────┘
```

> **核心理解**：模型不执行工具，模型只"决定"调用哪个工具、传什么参数；真正的执行由 Spring AI 框架完成。

## 三、核心知识点（⭐ 重点）

### ⭐ 1. @Tool 注解声明工具（最常用）

```java
class DateTimeTools {

    @Tool(description = "不需要关注用户时区，直接返回当前的时间给用户")
    String getCurrentDateTime() {
        return LocalDateTime.now().toString();
    }

    @Tool(description = "传入时区，返回对应时区的当前时间给用户")
    String getTimeByZoneId(@ToolParam(description = "需要查询时间的时区") ZoneId area) {
        ZonedDateTime time = ZonedDateTime.now(area);
        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
```

| 注解 | 作用 | 要点 |
|------|------|------|
| `@Tool` | 标记方法为可调用工具 | description 决定模型何时选择该工具 |
| `@ToolParam` | 描述参数含义 | 帮助模型正确生成参数值 |

> **重点**：`description` 的质量直接决定模型能否正确选择和调用工具！

### ⭐ 2. 使用工具的三种方式

```java
// 方式一：ChatClient.tools(Object) —— 最简洁（推荐）
chatClient.prompt(msg).tools(new DateTimeTools()).call().content();

// 方式二：ToolCallbacks + ChatModel —— 底层控制
ToolCallback[] tools = ToolCallbacks.from(new DateTimeTools());
ToolCallingChatOptions options = ToolCallingChatOptions.builder()
    .toolCallbacks(tools).build();
chatModel.call(new Prompt(msg, options));

// 方式三：通过 Bean 名称引用 —— 全局注册
chatClient.prompt(msg).tools("nowService").call().content();
```

### ⭐ 3. 编程式 MethodToolCallback（动态注册）

```java
Method method = ReflectionUtils.findMethod(DateTimeTools.class, "getTimeByZoneId", ZoneId.class);

ToolDefinition toolDefinition = ToolDefinition.builder()
    .name("getTimeByZoneId")
    .description("传入时区，返回对应时区的当前时间给用户")
    .inputSchema(JsonSchemaGenerator.generateForMethodInput(method))
    .build();

ToolCallback callBack = MethodToolCallback.builder()
    .toolDefinition(toolDefinition)
    .toolMethod(method)
    .toolObject(new DateTimeTools())
    .build();

chatClient.prompt(msg).toolCallbacks(callBack).call().content();
```

适用场景：需要动态注册、运行时决定工具集合、自定义元数据。

### 4. 函数式 FunctionToolCallback

```java
// 基于 java.util.function.Function<I, O> 实现
ToolCallback callBack = FunctionToolCallback
    .builder("nowDateByArea", new NowService())  // Function<AreaReq, AreaResp>
    .description("传入时区，返回对应时区的当前时间给用户")
    .inputType(AreaReq.class)
    .build();
```

```java
// 入参和返回值必须是 POJO
public record AreaReq(@ToolParam(description = "时区") ZoneId zoneId) {}
public record AreaResp(String time) {}

public static class NowService implements Function<AreaReq, AreaResp> {
    @Override
    public AreaResp apply(AreaReq req) {
        return new AreaResp(ZonedDateTime.now(req.zoneId()).toString());
    }
}
```

### 5. 四种工具注册方式对比

| 方式 | 复杂度 | 灵活性 | 适用场景 |
|------|--------|--------|----------|
| `@Tool` + `tools(obj)` | ⭐ | 低 | 日常开发首选 |
| `@Tool` + Bean 名称 | ⭐ | 中 | 全局共享工具 |
| `MethodToolCallback` | ⭐⭐⭐ | 高 | 动态注册、自定义 Schema |
| `FunctionToolCallback` | ⭐⭐ | 中 | 函数式编程风格 |

## 四、完整示例代码

```java
@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ZhiPuAiChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel)
            .defaultAdvisors(new SimpleLoggerAdvisor())
            .build();
    }

    // 最简用法：一行接入工具
    @RequestMapping(path = "time")
    public String getTime(String msg) {
        return chatClient.prompt(msg).tools(new DateTimeTools()).call().content();
    }

    // 对比：不加工具时模型无法回答实时问题
    @RequestMapping(path = "timeNoTools")
    public String getTimeNoTools(String msg) {
        return chatClient.prompt(msg).call().content();
    }
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **MCP Server（见 S07）** | 将工具以标准 MCP 协议暴露，跨应用共享 | 工具需要被多个 AI 应用复用 |
| **手动工具执行（见 S20）** | 禁用自动执行，开发者手动控制调用链 | 需要权限校验、日志、结果修改 |
| **RAG 检索增强** | 从知识库检索信息代替工具调用 | 静态知识问答 |
| **Agent 框架** | 使用 LangGraph4j/Spring AI Alibaba 编排多工具 | 复杂多步骤任务 |

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| 模型不调用工具 | description 描述不清晰，模型不知道何时该用 | 优化 @Tool description |
| 参数传错 | @ToolParam 描述模糊 | 明确参数格式和示例 |
| 工具方法未执行 | 方法非 public 或类未实例化 | 确保方法可访问 |
| 多个工具名冲突 | 不同类中工具方法同名 | 使用不同方法名或自定义 name |

## 七、本节小结

```
核心收获：
✅ 理解 Function Calling 的本质：模型决策 + 框架执行
✅ 掌握 @Tool/@ToolParam 注解声明工具（最核心）
✅ 掌握 4 种工具注册方式及各自适用场景
✅ 理解 description 质量对工具调用的决定性影响

下一步学习：S07-mcp-server → 将工具以标准协议暴露给外部
```


---

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


---

# S08-mcp-server-basic-auth：MCP Server 鉴权

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | MCP Server 暴露后任何人都能调用，如何添加简单的鉴权保护 |
| **适用场景** | 内部工具服务保护、防止未授权访问、API 配额控制 |
| **前置知识** | S07 MCP Server、Servlet Filter、HTTP 认证基础 |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────┐
│  1. 创建 Servlet Filter 拦截 MCP 端点                    │
│  2. 从请求头提取 Authorization 字段                       │
│  3. 根据认证方式（Bearer/Basic）校验凭证                  │
│  4. 校验通过 → 放行；失败 → 返回 401                     │
│  5. 客户端连接时携带认证头                                │
└─────────────────────────────────────────────────────────┘
```

## 三、核心知识点（⭐ 重点）

### ⭐ 1. Servlet Filter 拦截 MCP 端点

```java
@WebFilter(asyncSupported = true)  // ⚠️ SSE 必须开启异步支持
public class ReqFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        String url = request.getRequestURI();
        String auth = request.getHeader("Authorization");

        // 只拦截 MCP 相关端点
        if (url.equals("/sse") || url.equals("/mcp/messages")) {
            if (auth == null) {
                throw new RuntimeException("missing authorization");
            }
            if (auth.startsWith("Bearer ")) {
                // Bearer Token 校验
            } else if (auth.startsWith("Basic ")) {
                // Basic Auth 校验
            }
        }
        chain.doFilter(req, res);
    }
}
```

### ⭐ 2. Bearer Token 鉴权

```java
String token = auth.substring(7);  // "Bearer " 长度为 7
if (!EXPECTED_TOKEN.equals(token)) {
    throw new RuntimeException("token error");
}
```

请求头格式：`Authorization: Bearer yihuihui-blog`

### ⭐ 3. Basic Auth 鉴权

```java
String encoded = auth.substring(6);  // "Basic " 长度为 6
String decoded = new String(Base64.getDecoder().decode(encoded));
String[] credentials = decoded.split(":", 2);  // [username, password]
// 校验 credentials[0] 和 credentials[1]
```

请求头格式：`Authorization: Basic eWlodWk6MTIzNDU2Nzg=`（Base64 编码的 `username:password`）

### 4. 启用 Filter 注册

```java
@SpringBootApplication
@ServletComponentScan  // 关键：自动扫描 @WebFilter 注解
public class S08Application {
    public static void main(String[] args) {
        SpringApplication.run(S08Application.class, args);
    }
}
```

### 5. 关键注意事项

| 要点 | 说明 |
|------|------|
| `asyncSupported = true` | **必须开启**，否则 SSE 长连接被 Filter 阻断 |
| 精确拦截路径 | 只拦截 `/sse` 和 `/mcp/messages`，不影响其他接口 |
| 生产环境 | 本方案仅适合开发/测试，生产应使用 Spring Security |

## 四、完整示例代码

```java
@WebFilter(asyncSupported = true)
public class ReqFilter implements Filter {

    private static final String TOKEN = "yihuihui-blog";
    private static final String USERNAME = "yihui";
    private static final String PASSWORD = "12345678";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        String url = request.getRequestURI();
        String auth = request.getHeader("Authorization");

        if ("/sse".equals(url) || "/mcp/messages".equals(url)) {
            if (auth == null || auth.isBlank()) {
                ((HttpServletResponse) res).sendError(401, "Unauthorized");
                return;
            }
            if (auth.startsWith("Bearer ")) {
                if (!TOKEN.equals(auth.substring(7))) {
                    ((HttpServletResponse) res).sendError(403, "Invalid token");
                    return;
                }
            } else if (auth.startsWith("Basic ")) {
                String decoded = new String(Base64.getDecoder().decode(auth.substring(6)));
                String[] parts = decoded.split(":", 2);
                if (!USERNAME.equals(parts[0]) || !PASSWORD.equals(parts[1])) {
                    ((HttpServletResponse) res).sendError(403, "Invalid credentials");
                    return;
                }
            }
        }
        chain.doFilter(req, res);
    }
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **Spring Security（推荐）** | 完整的认证授权框架，支持 OAuth2/JWT | 生产环境首选 |
| **API Gateway 鉴权** | 在网关层统一校验（如 Spring Cloud Gateway） | 微服务架构 |
| **mTLS 双向证书** | 传输层安全，客户端也需证书 | 高安全要求场景 |
| **IP 白名单** | 只允许特定 IP 访问 | 内网固定调用方 |

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| SSE 连接立即断开 | Filter 未设置 `asyncSupported = true` | 添加异步支持 |
| Filter 未生效 | 缺少 `@ServletComponentScan` | 启动类添加注解 |
| 所有接口都被拦截 | 未做路径判断 | 精确匹配 MCP 端点 |
| 客户端认证失败 | MCP Client 未配置 Authorization 头 | 客户端配置中添加 headers |

## 七、本节小结

```
核心收获：
✅ 掌握 Servlet Filter 实现轻量级鉴权
✅ 理解 Bearer Token 和 Basic Auth 两种认证方式
✅ 牢记 asyncSupported = true 对 SSE 的必要性
✅ 了解生产环境应升级到 Spring Security

下一步学习：S09-chat-client → 全面掌握 ChatClient 高级 API
```


---

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


---

# S10-cost-advise：Advisor 机制与调用增强

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | 如何在不修改业务代码的前提下，对大模型调用进行横切增强（耗时统计、日志、权限校验、限流等） |
| **适用场景** | 性能监控、调用审计、成本控制、敏感词过滤、灰度策略 |
| **前置知识** | S09 ChatClient、Spring AOP/拦截器思想 |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────┐
│  请求 → Advisor1(前) → Advisor2(前) → ... → 模型调用     │
│  响应 ← Advisor1(后) ← Advisor2(后) ← ... ← 模型响应     │
│                                                          │
│  类似 Spring MVC 的 HandlerInterceptor / Servlet Filter   │
│  也类似 AOP 的 Around 通知                                │
└─────────────────────────────────────────────────────────┘
```

## 三、核心知识点（⭐ 重点）

### ⭐ 1. 自定义 Advisor（同时支持同步和流式）

```java
public class CostAdvisor implements CallAdvisor, StreamAdvisor {

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long start = System.currentTimeMillis();
        request.context().put("start-time", start);

        // 执行下一个 Advisor 或最终模型调用
        ChatClientResponse response = chain.nextCall(request);

        long cost = System.currentTimeMillis() - start;
        response.context().put("cost-time", cost);
        log.info("模型调用耗时: {}ms", cost);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        long start = System.currentTimeMillis();
        Flux<ChatClientResponse> response = chain.nextStream(request);
        
        // 聚合流式响应后执行回调
        return new ChatClientMessageAggregator().aggregateChatClientResponse(response, (res) -> {
            res.context().put("cost-time", System.currentTimeMillis() - start);
        });
    }

    @Override
    public String getName() { return "costAdvisor"; }

    @Override
    public int getOrder() { return Integer.MIN_VALUE; }  // 最高优先级（最外层）
}
```

### ⭐ 2. 关键接口体系

| 接口 | 作用 | 必须实现 |
|------|------|----------|
| `CallAdvisor` | 拦截同步调用 | `adviseCall()` |
| `StreamAdvisor` | 拦截流式调用 | `adviseStream()` |
| `CallAdvisorChain` | 调用链，`nextCall()` 传递到下一环 | — |
| `StreamAdvisorChain` | 流式调用链 | — |
| `ChatClientMessageAggregator` | 聚合流式响应后执行回调 | — |

> **重点**：如果只实现 `CallAdvisor`，流式调用时该 Advisor 不生效！

### ⭐ 3. Context 上下文传递

```java
// 写入（请求阶段）
request.context().put("start-time", start);
request.context().put("user-id", userId);

// 读取（响应阶段 / 下游 Advisor）
long startTime = (long) response.context().get("start-time");
```

- `context()` 是贯穿整个调用链的 Map
- Advisor 之间可通过 context 共享数据
- 调用方也可通过 context 获取增强信息（如耗时）

### 4. 注册 Advisor

```java
// 全局注册
ChatClient.builder(chatModel)
    .defaultAdvisors(new CostAdvisor(), new SimpleLoggerAdvisor())
    .build();

// 请求级动态注册
chatClient.prompt().user(msg)
    .advisors(new CostAdvisor())
    .call().content();
```

### 5. getOrder() 执行顺序

```
getOrder() 值越小 → 越先执行（越外层）
getOrder() 值越大 → 越后执行（越内层）

Integer.MIN_VALUE = 最外层（适合耗时统计，包裹所有其他 Advisor）
Integer.MAX_VALUE = 最内层（最接近模型调用）
```

## 四、完整示例代码

```java
// 耗时统计 Advisor
public class CostAdvisor implements CallAdvisor, StreamAdvisor {
    
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long start = System.currentTimeMillis();
        ChatClientResponse response = chain.nextCall(request);
        long cost = System.currentTimeMillis() - start;
        response.context().put("cost-time", cost);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        long start = System.currentTimeMillis();
        return new ChatClientMessageAggregator()
            .aggregateChatClientResponse(chain.nextStream(request), 
                res -> res.context().put("cost-time", System.currentTimeMillis() - start));
    }

    @Override
    public String getName() { return "costAdvisor"; }

    @Override
    public int getOrder() { return Integer.MIN_VALUE; }
}

// 使用
@RestController
public class ChatController {
    private final ChatClient chatClient;

    public ChatController(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel)
            .defaultAdvisors(new CostAdvisor())
            .build();
    }

    @GetMapping("/chat")
    public Map<String, Object> chat(String msg) {
        ChatClientResponse resp = chatClient.prompt(msg).call().chatClientResponse();
        return Map.of(
            "content", resp.chatResponse().getResult().getOutput().getText(),
            "cost", resp.context().get("cost-time")
        );
    }
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **Spring AOP** | 在 ChatModel 方法上切面 | 只需简单日志/监控 |
| **Micrometer + Actuator** | 指标采集 + 监控面板 | 生产级可观测性 |
| **自定义 ChatModel 装饰器** | 包装原始 ChatModel，添加增强逻辑 | 不使用 ChatClient 时 |
| **OpenTelemetry** | 分布式链路追踪 | 微服务全链路监控 |

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| 流式调用 Advisor 不生效 | 只实现了 CallAdvisor | 同时实现 StreamAdvisor |
| 耗时统计不准 | getOrder() 不是最小值 | 设为 Integer.MIN_VALUE |
| Context 数据丢失 | 流式场景未用 Aggregator | 使用 ChatClientMessageAggregator |
| Advisor 顺序混乱 | 多个 Advisor 未设置 order | 明确每个 Advisor 的 getOrder() |

## 七、本节小结

```
核心收获：
✅ 理解 Advisor 是 Spring AI 的 AOP 机制（调用链模式）
✅ 掌握 CallAdvisor + StreamAdvisor 双接口实现
✅ 掌握 Context 上下文在调用链中传递数据
✅ 理解 getOrder() 控制执行顺序

下一步学习：S11-image-model → 文生图能力接入
```


---

# S11-image-model：文生图模型

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | 如何通过 Spring AI 调用图像生成模型，根据文本描述生成图片 |
| **适用场景** | AI 绘画、海报生成、产品图设计、创意内容生产 |
| **前置知识** | S01 Spring AI 基础、HTTP 响应流处理 |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────┐
│  1. 引入模型 Starter（含 ImageModel 自动配置）            │
│  2. 注入 ImageModel Bean                                 │
│  3. 构建 ImagePrompt（文本描述 + 图像参数）               │
│  4. 调用 imgModel.call() 获取 ImageResponse              │
│  5. 从响应中提取图片 URL                                 │
│  6. 下载图片 / 直接返回 URL / 转为流输出                  │
└─────────────────────────────────────────────────────────┘
```

## 三、核心知识点（⭐ 重点）

### ⭐ 1. ImageModel 接口

```java
private final ImageModel imgModel;  // 自动注入（如 ZhiPuAiImageModel）
```

> Spring AI 的图像生成与对话模型类似，通过统一的 `ImageModel` 接口抽象不同厂商实现。

### ⭐ 2. 构建图像生成请求

```java
ImageResponse response = imgModel.call(new ImagePrompt(msg,
    ImageOptionsBuilder.builder()
        .height(1024)
        .width(1024)
        .model("CogView-3-Flash")     // 图像模型名
        .responseFormat("png")         // 返回格式
        .style("natural")              // vivid=生动 / natural=自然
        .build()));
```

| 参数 | 说明 | 常用值 |
|------|------|--------|
| `width/height` | 图片尺寸 | 1024x1024、1792x1024 |
| `model` | 图像模型 | CogView-3-Flash、dall-e-3 |
| `style` | 风格 | vivid（生动）/ natural（自然） |
| `responseFormat` | 输出格式 | png / url |

### ⭐ 3. 获取生成结果

```java
Image img = response.getResult().getOutput();
String url = img.getUrl();  // 远程图片 URL（有时效性）
```

### 4. 将远程图片转为流返回前端

```java
@GetMapping(value = "/img/gen", produces = "application/octet-stream")
public void genImg(String msg, HttpServletResponse res) throws Exception {
    ImageResponse response = imgModel.call(new ImagePrompt(msg, options));
    String imgUrl = response.getResult().getOutput().getUrl();
    
    BufferedImage image = ImageIO.read(new URL(imgUrl));
    res.setContentType("image/png");
    ImageIO.write(image, "png", res.getOutputStream());
}
```

### 5. 核心类

| 类 | 作用 |
|---|---|
| `ImageModel` | 图像生成统一抽象接口 |
| `ImagePrompt` | 封装提示词 + 图像选项 |
| `ImageOptionsBuilder` | 配置尺寸、模型、风格等参数 |
| `ImageResponse` | 响应结果，包含图片 URL 或 Base64 |
| `Image` | 单张生成图片（url / b64Json） |

## 四、完整示例代码

```java
@RestController
public class ImageController {

    private final ImageModel imgModel;

    @Autowired
    public ImageController(ImageModel imgModel) {
        this.imgModel = imgModel;
    }

    // 返回图片 URL
    @GetMapping("/img/url")
    public String genImgUrl(String msg) {
        ImageResponse response = imgModel.call(new ImagePrompt(msg,
            ImageOptionsBuilder.builder()
                .height(1024).width(1024)
                .model("CogView-3-Flash")
                .style("natural")
                .build()));
        return response.getResult().getOutput().getUrl();
    }

    // 直接输出图片流
    @GetMapping(value = "/img/gen", produces = "application/octet-stream")
    public void genImg(String msg, HttpServletResponse res) throws Exception {
        ImageResponse response = imgModel.call(new ImagePrompt(msg,
            ImageOptionsBuilder.builder()
                .height(1024).width(1024)
                .model("CogView-3-Flash")
                .build()));
        
        BufferedImage image = ImageIO.read(new URL(response.getResult().getOutput().getUrl()));
        res.setContentType("image/png");
        ImageIO.write(image, "png", res.getOutputStream());
    }
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **OpenAI DALL-E Starter** | `spring-ai-starter-model-openai` 含 ImageModel | 使用 DALL-E 系列 |
| **Stability AI** | Spring AI 有对应 Starter | 使用 Stable Diffusion |
| **直接调 SDK** | 使用厂商原生 SDK（如智谱 SDK） | 需要厂商特有参数 |
| **ComfyUI/SD WebUI API** | 本地部署开源模型 + HTTP 调用 | 私有化部署、成本敏感 |

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| 图片 URL 过期无法访问 | 生成的 URL 有时效性（通常几分钟） | 及时下载或转存到 OSS |
| ImageModel Bean 不存在 | Starter 未包含图像模型配置 | 确认 Starter 支持 ImageModel |
| 生成速度慢 | 图像生成本身耗时较长 | 改为异步 + 轮询结果 |
| 图片尺寸不支持 | 模型只支持特定尺寸 | 查阅模型文档确认支持的尺寸 |

## 七、本节小结

```
核心收获：
✅ 掌握 ImageModel 统一接口及 ImagePrompt 构建
✅ 掌握图像参数配置（尺寸/风格/模型）
✅ 掌握图片 URL 获取与流式输出
✅ 了解生成 URL 有时效性，需及时处理

下一步学习：S12-multimodality-model → 让模型"看图说话"
```


---

# S12-multimodality-model：多模态（图文理解）

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | 如何让大模型"看图说话"——同时发送图片和文本指令，实现图像识别与分析 |
| **适用场景** | 食材识别/卡路里计算、OCR 文字提取、图片内容审核、视觉问答 |
| **前置知识** | S01 ChatModel、S03 结构化输出、S09 ChatClient |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────┐
│  1. 获取图片数据（URL 下载 / 本地文件 / Base64）          │
│  2. 构建 Media 对象（指定 MimeType + 图片数据）           │
│  3. 构建 UserMessage（文本指令 + Media）                  │
│  4. 调用 ChatClient / ChatModel                          │
│  5. 获取纯文本结果 或 结构化对象                          │
└─────────────────────────────────────────────────────────┘
```

## 三、核心知识点（⭐ 重点）

### ⭐ 1. 构建图片 Media 对象

```java
byte[] imgs = HttpUtil.downloadBytes(imgUrl);  // 下载远程图片

Media media = Media.builder()
    .mimeType(MimeTypeUtils.IMAGE_PNG)  // 指定图片类型
    .data(imgs)                          // 图片二进制数据
    .build();
```

支持的 MimeType：`IMAGE_PNG`、`IMAGE_JPEG`、`IMAGE_GIF`、`IMAGE_WEBP`

### ⭐ 2. 构建图文混合消息

```java
Message userMsg = UserMessage.builder()
    .text("请将图片内容进行识别，并返回结果")  // 文本指令
    .media(media)                             // 附带图片
    .build();

Prompt prompt = new Prompt(userMsg);
```

> **重点**：一个 `UserMessage` 可以同时包含文本和**多个** Media（多图分析）。

### ⭐ 3. 结构化输出（结合 S03）

```java
FoodDetail detail = chatClient.prompt(prompt).call().entity(FoodDetail.class);
```

```java
public record FoodDetail(
    @JsonPropertyDescription("整张图片的描述") String desc,
    @JsonPropertyDescription("总的卡路里") Double totalCalorie,
    @JsonPropertyDescription("图片中的食材列表") List<FoodItem> itemList
) {}

public record FoodItem(
    @JsonPropertyDescription("食材名称") String name,
    @JsonPropertyDescription("卡路里") Double calorie
) {}
```

> `@JsonPropertyDescription` 在多模态场景尤为重要——引导模型准确理解每个字段该填什么。

### 4. 纯文本返回

```java
String result = chatClient.prompt(prompt).call().content();
```

### 5. 图片来源的多种方式

| 来源 | 实现方式 |
|------|----------|
| 远程 URL | `HttpUtil.downloadBytes(url)` |
| 本地文件 | `new FileSystemResource("path/to/img.png")` |
| Base64 | `Base64.getDecoder().decode(base64Str)` |
| 前端上传 | `MultipartFile.getBytes()` |

## 四、完整示例代码

```java
@RestController
public class FoodController {

    private final ChatClient chatClient;

    public FoodController(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    // 图片识别 → 结构化输出
    @GetMapping("/food/analyze")
    public FoodDetail analyze(String imgUrl) {
        byte[] imgs = HttpUtil.downloadBytes(imgUrl);
        
        Media media = Media.builder()
            .mimeType(MimeTypeUtils.IMAGE_PNG)
            .data(imgs)
            .build();

        Message userMsg = UserMessage.builder()
            .text("请识别图片中的食材，计算每种食材的卡路里和总卡路里")
            .media(media)
            .build();

        return chatClient.prompt(new Prompt(userMsg))
            .call()
            .entity(FoodDetail.class);
    }
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **专用视觉模型 API** | 直接调用 OCR/图像识别 API（如百度 OCR） | 只需固定格式识别 |
| **本地模型推理** | 部署 LLaVA/Qwen-VL 等开源多模态模型 | 私有化、数据安全 |
| **图片 URL 直传** | 部分模型支持直接传 URL（无需下载） | 模型原生支持时 |
| **视频帧提取 + 多模态** | 抽帧后逐帧分析 | 视频内容理解 |

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| 模型无法识别图片 | 使用了非视觉模型（如 GLM-4 非 GLM-4V） | 确认模型支持视觉能力 |
| 图片过大报错 | 超出模型输入限制 | 压缩图片或降低分辨率 |
| MimeType 不匹配 | 实际格式与声明不一致 | 根据真实格式设置 MimeType |
| 结构化字段为空 | 模型不理解字段含义 | 加强 @JsonPropertyDescription 描述 |

## 七、本节小结

```
核心收获：
✅ 掌握 Media 对象构建（MimeType + 二进制数据）
✅ 掌握 UserMessage 图文混合消息构建
✅ 掌握多模态 + 结构化输出的组合使用
✅ 理解模型必须具备视觉能力（如 GLM-4V）

下一步学习：S13-mcp-client-chat → 作为 MCP Client 集成远程工具
```


---

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


---

# S15-openai-style-model：OpenAI 兼容接口对接

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | 国内大模型（阿里百炼、DeepSeek、MoonShot 等）多提供 OpenAI 兼容接口，如何零适配代码直接对接 |
| **适用场景** | 快速切换模型供应商、多模型共存、降低迁移成本 |
| **前置知识** | S01 ChatModel 基础、S05 自定义模型（对比理解） |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────┐
│  1. 引入 spring-ai-starter-model-openai 依赖             │
│  2. 配置 base-url 为模型厂商的兼容接口地址               │
│  3. 配置 api-key 和 model 名称                           │
│  4. 自动装配 OpenAiChatModel → 像用 OpenAI 一样使用      │
│  5.（可选）手动构建多模型实例实现多模型共存               │
└─────────────────────────────────────────────────────────┘
```

## 三、核心知识点（⭐ 重点）

### ⭐ 1. 配置文件方式（自动装配，最简）

```yaml
spring:
  ai:
    openai:
      base-url: https://dashscope.aliyuncs.com/compatible-mode
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: qwen-plus
```

```java
// 直接注入即可使用
@Autowired
private ChatModel chatModel;

chatClient.prompt(msg).call().content();
```

> **核心要点**：只需修改 `base-url` 和 `model`，就能对接任何 OpenAI 兼容接口，无需改一行代码！

### ⭐ 2. 手动编程方式（多模型共存）

```java
// 构建阿里百炼模型
OpenAiApi dashApi = OpenAiApi.builder()
    .apiKey(dashApiKey)
    .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
    .build();

ChatModel dashModel = OpenAiChatModel.builder()
    .openAiApi(dashApi)
    .defaultOptions(OpenAiChatOptions.builder().model("qwen-plus").build())
    .build();

// 构建 DeepSeek 模型
OpenAiApi deepApi = OpenAiApi.builder()
    .apiKey(deepApiKey)
    .baseUrl("https://api.deepseek.com")
    .build();

ChatModel deepModel = OpenAiChatModel.builder()
    .openAiApi(deepApi)
    .defaultOptions(OpenAiChatOptions.builder().model("deepseek-chat").build())
    .build();
```

适用场景：同一应用中根据任务类型选择不同模型。

### ⭐ 3. 常见 OpenAI 兼容接口地址

| 厂商 | base-url | 常用 model |
|------|----------|------------|
| 阿里百炼 | `https://dashscope.aliyuncs.com/compatible-mode` | qwen-plus、qwen-max |
| DeepSeek | `https://api.deepseek.com` | deepseek-chat、deepseek-reasoner |
| MoonShot | `https://api.moonshot.cn` | moonshot-v1-8k |
| SiliconFlow | `https://api.siliconflow.cn` | 多种开源模型 |
| 智谱 | `https://open.bigmodel.cn` | glm-4-plus |

### 4. API Key 多种注入方式

```java
// 优先级：启动参数 > JVM参数 > 环境变量 > 配置文件
environment.getProperty("dash-api-key");     // --dash-api-key=xxx
System.getProperty("dash-api-key");           // -Ddash-api-key=xxx
System.getenv("dash-api-key");                // 环境变量
```

### 5. 核心类

| 类 | 作用 |
|---|---|
| `OpenAiApi` | 封装 HTTP 通信，可自定义 baseUrl |
| `OpenAiChatModel` | OpenAI 兼容的 ChatModel 实现 |
| `OpenAiChatOptions` | 配置 model、temperature 等参数 |

## 四、完整示例代码

```java
@Configuration
public class MultiModelConfig {

    @Bean("dashModel")
    public ChatModel dashModel(@Value("${dash-api-key}") String apiKey) {
        OpenAiApi api = OpenAiApi.builder()
            .apiKey(apiKey)
            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
            .build();
        return OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(OpenAiChatOptions.builder().model("qwen-plus").build())
            .build();
    }

    @Bean("deepModel")
    public ChatModel deepModel(@Value("${deep-api-key}") String apiKey) {
        OpenAiApi api = OpenAiApi.builder()
            .apiKey(apiKey)
            .baseUrl("https://api.deepseek.com")
            .build();
        return OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(OpenAiChatOptions.builder().model("deepseek-chat").build())
            .build();
    }
}

// 使用时按名称注入
@RestController
public class ChatController {
    @Autowired @Qualifier("dashModel")
    private ChatModel dashModel;
    
    @Autowired @Qualifier("deepModel")
    private ChatModel deepModel;
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **厂商专属 Starter** | 如 `spring-ai-starter-model-zhipuai` | 只需单一厂商且有专属 Starter |
| **自定义 ChatModel（S05）** | 完全自己实现接口 | 无 OpenAI 兼容接口的模型 |
| **LiteLLM 代理** | 部署代理统一转为 OpenAI 格式 | 大量模型统一管理 |
| **Spring AI Alibaba** | 阿里官方框架，原生支持百炼 | 深度使用阿里生态 |

> **决策建议**：有 OpenAI 兼容接口 → 用本节方案（最简）；无兼容接口 → S05 自定义实现。

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| 401 Unauthorized | api-key 配置错误或过期 | 检查密钥有效性 |
| model not found | model 名称拼写错误 | 查阅厂商文档确认模型名 |
| 与自动配置冲突 | 手动创建 Bean 与 yml 配置同时存在 | 二选一，或用 @Primary 指定 |
| completionsPath 不对 | 部分厂商路径非标准 | 使用 `.completionsPath()` 自定义 |

## 七、本节小结

```
核心收获：
✅ 掌握 OpenAI Starter 对接任何兼容接口（改 base-url 即可）
✅ 掌握手动构建多模型实例（多模型共存）
✅ 了解主流国内模型的兼容接口地址
✅ 理解这是对接新模型的最优先方案

下一步学习：S16-stream-call → 深入流式调用
```


---

# S16-stream-call：流式调用深入

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | 大模型生成内容较慢（数秒~数十秒），如何以流式（SSE）逐步返回给前端，提升用户体验 |
| **适用场景** | 聊天界面逐字输出、长文本生成实时展示、降低用户感知等待时间 |
| **前置知识** | S01 流式基础、S09 ChatClient、Reactor/Flux 基础 |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────┐
│  1. 接口声明 produces = "text/event-stream"              │
│  2. 调用 chatModel.stream() 或 chatClient.stream()       │
│  3. 获取 Flux<ChatResponse> 或 Flux<String>              │
│  4. 直接返回 Flux（框架自动 SSE 推送）                    │
│     或手动通过 SseEmitter 控制推送                        │
│  5. 前端通过 EventSource 或 fetch 接收                    │
└─────────────────────────────────────────────────────────┘
```

## 三、核心知识点（⭐ 重点）

### ⭐ 1. ChatModel 直接流式

```java
@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ChatResponse> chatV1(String msg) {
    return chatModel.stream(new Prompt(msg));
}
```

- 返回 `Flux<ChatResponse>`，每个元素包含增量文本 + 元数据
- 适合需要 token 统计、finish_reason 等信息的场景

### ⭐ 2. ChatClient 流式（推荐）

```java
@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> chatV3(String msg) {
    return chatClient.prompt(msg).stream().content();
}
```

- `.stream().content()` 直接返回纯文本 Flux
- 最简洁，生产环境首选

### ⭐ 3. 流式收集为完整文本

```java
// 方式一：collectList + joining
List<ChatResponse> responses = chatModel.stream(new Prompt(msg))
    .collectList().block();
String content = responses.stream()
    .map(r -> r.getResult().getOutput().getText())
    .collect(Collectors.joining());

// 方式二：reduce
String content = chatClient.prompt(msg).stream().content()
    .reduce("", (a, b) -> a + b).block();
```

适用场景：后端需要完整文本做后处理（如存储、分析），不需要实时推送。

### 4. SseEmitter 手动推送

```java
@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chatV5(String msg) {
    SseEmitter emitter = new SseEmitter(60_000L);  // 60秒超时
    
    chatClient.prompt(msg).stream().content()
        .doOnComplete(emitter::complete)
        .doOnError(emitter::completeWithError)
        .subscribe(txt -> {
            try {
                emitter.send(txt);
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });
    
    return emitter;
}
```

适用场景：需要在推送前做额外处理（过滤、格式化、添加事件类型）。

### 5. 三种流式方式对比

| 方式 | 返回类型 | 适用场景 | 复杂度 |
|------|----------|----------|--------|
| `Flux<ChatResponse>` | 含元数据 | 需要 token 统计 | ⭐ |
| `Flux<String>` | 纯文本 | 简单文本流式输出（推荐） | ⭐ |
| `SseEmitter` | 手动控制 | 需要精细控制推送逻辑 | ⭐⭐⭐ |

## 四、完整示例代码

```java
@RestController
public class StreamController {

    private final ChatModel chatModel;
    private final ChatClient chatClient;

    public StreamController(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    // 方式1：ChatModel 原始流
    @GetMapping(value = "/v1/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> streamV1(String msg) {
        return chatModel.stream(new Prompt(msg));
    }

    // 方式2：ChatClient 纯文本流（推荐）
    @GetMapping(value = "/v2/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamV2(String msg) {
        return chatClient.prompt(msg).stream().content();
    }

    // 方式3：收集为完整文本
    @GetMapping("/v3/collect")
    public String collect(String msg) {
        return chatClient.prompt(msg).stream().content()
            .reduce("", String::concat).block();
    }

    // 方式4：SseEmitter 手动控制
    @GetMapping(value = "/v4/emitter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter emitter(String msg) {
        SseEmitter emitter = new SseEmitter(60_000L);
        chatClient.prompt(msg).stream().content()
            .doOnComplete(emitter::complete)
            .doOnError(emitter::completeWithError)
            .subscribe(txt -> {
                try { emitter.send(txt); }
                catch (IOException e) { emitter.completeWithError(e); }
            });
        return emitter;
    }
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **WebSocket** | 双向通信，支持客户端中途发消息 | 需要实时交互（如打断生成） |
| **Long Polling** | 客户端轮询获取增量内容 | 不支持 SSE 的旧浏览器 |
| **gRPC Stream** | 高性能二进制流 | 内部服务间流式通信 |
| **前端 fetch + ReadableStream** | 原生 JS 读取流 | 自定义前端渲染逻辑 |

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| 前端收到一整坨而非逐字 | Nginx 缓冲了 SSE 响应 | 添加 `X-Accel-Buffering: no` 头 |
| Flux 只能订阅一次 | 重复消费同一个 Flux | 每次请求创建新的 Flux |
| SseEmitter 超时断开 | 默认 30 秒超时 | 设置更长的超时时间 |
| 中文乱码 | 未指定字符编码 | 设置 `produces` 含 charset=UTF-8 |

## 七、本节小结

```
核心收获：
✅ 掌握 ChatModel.stream() 和 ChatClient.stream() 两种流式 API
✅ 掌握 Flux<String> 纯文本流（生产首选）
✅ 掌握流式收集为完整文本的方法
✅ 掌握 SseEmitter 手动控制推送
✅ 理解 SSE 与 Nginx 缓冲的兼容问题

下一步学习：S17-reasoncontent → 推理模型与思考过程获取
```


---

# S17-reasoncontent：推理模型与思考过程

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | 推理型大模型（如 qwen-plus、glm-4.5-flash）回答前会"思考"，如何获取并展示推理过程 |
| **适用场景** | 展示 AI 思考链路、增强可解释性、调试模型推理质量、Token 消耗分析 |
| **前置知识** | S15 OpenAI 兼容接口、S16 流式调用 |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────┐
│  1. 选择支持推理的模型（qwen-plus-latest、glm-4.5-flash）│
│  2. 通过 extraBody 开启推理模式（部分模型默认开启）       │
│  3. 调用模型获取响应                                     │
│  4. 从 metadata 中提取 reasoningContent（思考过程）       │
│  5. 从 output.getText() 获取最终回答                     │
│  6.（可选）统计 Token 用量                               │
└─────────────────────────────────────────────────────────┘
```

## 三、核心知识点（⭐ 重点）

### ⭐ 1. 开启推理模式

```java
// 阿里百炼：通过 extraBody 显式开启
OpenAiChatOptions options = OpenAiChatOptions.builder()
    .model("qwen-plus-latest")
    .extraBody(Map.of("enable_thinking", true))
    .build();

// 智谱 glm-4.5-flash：默认开启推理
// 关闭方式：.extraBody(Map.of("thinking", Map.of("type", "disabled")))
```

> **注意**：不同厂商开启推理的方式不同，需查阅对应文档。

### ⭐ 2. 获取推理内容（同步）

```java
ChatResponse response = chatModel.call(new Prompt(new UserMessage(msg), options));
Generation generation = response.getResult();

// 思考过程（在 metadata 中，不是正文！）
Object reasoning = generation.getOutput().getMetadata().get("reasoningContent");

// 最终回答
String answer = generation.getOutput().getText();
```

> **核心要点**：`reasoningContent` 存放在 `metadata` 中，与正文 `getText()` 分离。

### ⭐ 3. 流式场景下的推理输出

```java
StringBuilder reason = new StringBuilder();
StringBuilder content = new StringBuilder();

Flux<ChatResponse> res = dashModel.stream(new Prompt(new UserMessage(msg), options));
res.subscribe(chunk -> {
    var output = chunk.getResult().getOutput();
    
    // 思考片段
    Object r = output.getMetadata().get("reasoningContent");
    if (r != null) reason.append(r);
    
    // 回答片段
    String t = output.getText();
    if (t != null) content.append(t);
});
```

- 流式时思考和回答**交替输出**，需分别拼接
- 通常先输出完所有思考，再输出回答

### 4. Token 用量统计

```java
var usage = response.getMetadata().getUsage();
long promptTokens = usage.getPromptTokens();
long completionTokens = usage.getCompletionTokens();
long totalTokens = usage.getTotalTokens();
```

> 推理模型的 completionTokens 包含思考 + 回答的总消耗。

### 5. 手动构建多模型（不同厂商）

```java
// 阿里百炼
OpenAiApi dashApi = OpenAiApi.builder()
    .apiKey(dashKey)
    .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
    .build();

// 智谱（注意 completionsPath 不同）
OpenAiApi zhipuApi = OpenAiApi.builder()
    .apiKey(zhipuKey)
    .baseUrl("https://open.bigmodel.cn")
    .completionsPath("/api/paas/v4/chat/completions")
    .build();
```

- `completionsPath()`：自定义补全接口路径（不同厂商路径不同）

## 四、完整示例代码

```java
@RestController
public class ReasonController {

    private final ChatModel dashModel;

    public ReasonController(@Value("${dash-api-key}") String apiKey) {
        OpenAiApi api = OpenAiApi.builder()
            .apiKey(apiKey)
            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
            .build();
        this.dashModel = OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(OpenAiChatOptions.builder()
                .model("qwen-plus-latest")
                .extraBody(Map.of("enable_thinking", true))
                .build())
            .build();
    }

    @GetMapping("/reason")
    public Map<String, Object> reason(String msg) {
        ChatResponse response = dashModel.call(new Prompt(new UserMessage(msg)));
        Generation gen = response.getResult();
        
        return Map.of(
            "thinking", gen.getOutput().getMetadata().getOrDefault("reasoningContent", ""),
            "answer", gen.getOutput().getText(),
            "tokens", response.getMetadata().getUsage().getTotalTokens()
        );
    }
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **Chain-of-Thought 提示词** | 在提示词中要求"请一步步思考" | 非推理模型也想看思考过程 |
| **模型原生 reasoning API** | 如 OpenAI o1 的 reasoning 字段 | 使用 OpenAI 原生推理模型 |
| **LangGraph 思维链** | 用工作流显式编排思考步骤 | 需要可控的多步推理 |
| **日志 + 回放** | 记录完整推理日志供后续分析 | 模型评估和质量审计 |

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| reasoningContent 为 null | 模型不支持或未开启推理 | 确认模型支持 + extraBody 配置 |
| 思考内容为空字符串 | 模型认为不需要思考 | 正常现象，简单问题可能跳过 |
| Token 消耗异常高 | 推理过程消耗大量 Token | 评估是否真的需要推理模式 |
| completionsPath 404 | 厂商接口路径非标准 | 查阅文档配置正确路径 |

## 七、本节小结

```
核心收获：
✅ 掌握通过 extraBody 开启推理模式
✅ 掌握从 metadata 中获取 reasoningContent
✅ 掌握流式场景下思考/回答的分别拼接
✅ 了解 Token 用量统计方法
✅ 理解不同厂商的推理开启方式差异

下一步学习：S18-dashscop-audio → 文本转语音（TTS）
```


---

# S18-dashscop-audio：文本转语音（TTS）

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | 如何实现文本转语音（TTS），将文字内容合成为自然语音音频 |
| **适用场景** | AI 语音助手、有声读物生成、无障碍朗读、语音播报 |
| **前置知识** | HTTP 文件下载、音频格式基础 |
| **特别说明** | 本项目直接使用阿里 DashScope SDK，**非 Spring AI 标准接口** |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────┐
│  1. 引入 DashScope SDK 依赖                              │
│  2. 创建 MultiModalConversation 实例                     │
│  3. 构建 TTS 参数（模型、文本、语音角色、语言）           │
│  4. 调用 call() 获取合成结果                             │
│  5. 从结果中提取音频 URL                                 │
│  6. 下载音频文件 / 流式返回给客户端                      │
└─────────────────────────────────────────────────────────┘
```

## 三、核心知识点（⭐ 重点）

### ⭐ 1. DashScope TTS 调用

```java
MultiModalConversation conv = new MultiModalConversation();

MultiModalConversationParam param = MultiModalConversationParam.builder()
    .apiKey(apiKey)
    .model("qwen3-tts-flash-2025-11-27")  // TTS 模型
    .text(text)                             // 要合成的文本
    .voice(AudioParameters.Voice.CHERRY)    // 语音角色
    .languageType("Chinese")                // 语言
    .build();

MultiModalConversationResult result = conv.call(param);
String audioUrl = result.getOutput().getAudio().getUrl();
```

| 参数 | 说明 | 可选值 |
|------|------|--------|
| `model` | TTS 模型 | qwen3-tts-flash 等 |
| `voice` | 语音角色 | CHERRY、SERENA 等 |
| `languageType` | 语言 | Chinese、English |
| `text` | 合成文本 | 任意文本 |

### ⭐ 2. 下载音频文件

```java
OkHttpClient client = new OkHttpClient();
Request request = new Request.Builder().url(audioUrl).build();
Response response = client.newCall(request).execute();
byte[] audioData = response.body().bytes();

// 写入本地文件
try (FileOutputStream out = new FileOutputStream("output.wav")) {
    out.write(audioData);
}
```

> **注意**：返回的音频 URL 有时效性，需及时下载！

### 3. 配置

```yaml
spring:
  tts:
    api-key: ${DASH_API_KEY}
    model: qwen3-tts-flash-2025-11-27
    url: https://dashscope.aliyuncs.com/api/v1
```

### 4. 为什么不用 Spring AI？

| 对比 | Spring AI | DashScope SDK（本节） |
|------|-----------|----------------------|
| TTS 支持 | 暂无标准 TTS 接口 | 完整支持 |
| 生态集成 | ChatModel/Advisor 体系 | 独立调用 |
| 适用性 | 对话/图像/转录 | 语音合成 |

> 截至当前版本，Spring AI 尚未提供标准 TTS 抽象接口，故直接使用厂商 SDK。

## 四、完整示例代码

```java
@Service
public class TtsService {

    @Value("${spring.tts.api-key}")
    private String apiKey;

    @Value("${spring.tts.model}")
    private String model;

    public byte[] textToSpeech(String text) throws Exception {
        MultiModalConversation conv = new MultiModalConversation();
        
        MultiModalConversationParam param = MultiModalConversationParam.builder()
            .apiKey(apiKey)
            .model(model)
            .text(text)
            .voice(AudioParameters.Voice.CHERRY)
            .languageType("Chinese")
            .build();

        MultiModalConversationResult result = conv.call(param);
        String audioUrl = result.getOutput().getAudio().getUrl();

        // 下载音频
        OkHttpClient client = new OkHttpClient();
        Response response = client.newCall(new Request.Builder().url(audioUrl).build()).execute();
        return response.body().bytes();
    }
}

@RestController
public class TtsController {

    @Autowired
    private TtsService ttsService;

    @GetMapping(value = "/tts", produces = "audio/wav")
    public byte[] tts(String text) throws Exception {
        return ttsService.textToSpeech(text);
    }
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **OpenAI TTS API** | Spring AI OpenAI Starter 支持 SpeechModel | 使用 OpenAI 生态 |
| **Azure Speech SDK** | 微软认知服务，多语言多角色 | 企业级多语言需求 |
| **本地 TTS（VITS/Edge-TTS）** | 开源模型本地部署 | 离线场景、成本敏感 |
| **浏览器 Web Speech API** | 前端原生朗读 | 简单朗读、无需后端 |

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| 音频 URL 无法访问 | URL 已过期（通常几分钟有效） | 生成后立即下载 |
| 合成结果不自然 | 文本含特殊符号或代码 | 预处理文本，移除特殊字符 |
| 中文发音错误 | languageType 未设置 | 明确指定 "Chinese" |
| SDK 版本不兼容 | DashScope SDK 更新频繁 | 锁定版本号，关注更新日志 |

## 七、本节小结

```
核心收获：
✅ 掌握 DashScope SDK 实现 TTS 文本转语音
✅ 了解语音角色和语言参数配置
✅ 理解音频 URL 有时效性需及时处理
✅ 了解 Spring AI 当前 TTS 支持现状

下一步学习：S19-audio-transaction → 语音识别（ASR）
```


---

# S19-audio-transaction：语音识别（ASR）

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | 如何实现语音识别（ASR），将音频文件转录为文字 |
| **适用场景** | 语音转文字、会议记录、字幕生成、语音搜索、语音指令输入 |
| **前置知识** | S15 OpenAI 兼容接口、Spring Resource 资源加载 |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────┐
│  1. 引入 OpenAI Starter（含 TranscriptionModel 配置）     │
│  2. 配置 base-url 指向支持 ASR 的平台（如 SiliconFlow）   │
│  3. 注入 TranscriptionModel Bean                         │
│  4. 加载音频资源（classpath / 文件系统 / URL）            │
│  5. 构建 AudioTranscriptionPrompt（音频 + 选项）          │
│  6. 调用 transcriptionModel.call() 获取转录文本           │
└─────────────────────────────────────────────────────────┘
```

## 三、核心知识点（⭐ 重点）

### ⭐ 1. 使用自动注册的 TranscriptionModel

```java
@Autowired
private TranscriptionModel transcriptionModel;

AudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
    .model("TeleAI/TeleSpeechASR")
    .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
    .build();

AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(resource, options);
AudioTranscriptionResponse response = transcriptionModel.call(prompt);
String text = response.getResult().getOutput();  // 转录文本
```

### ⭐ 2. 手动构建模型实例（使用不同 ASR 模型）

```java
OpenAiAudioApi openAiAudioApi = OpenAiAudioApi.builder()
    .apiKey(apiKey)
    .baseUrl("https://api.siliconflow.cn")
    .build();

TranscriptionModel model = new OpenAiAudioTranscriptionModel(openAiAudioApi,
    OpenAiAudioTranscriptionOptions.builder()
        .model("FunAudioLLM/SenseVoiceSmall")  // 另一个 ASR 模型
        .build());
```

适用场景：同一应用中对比不同 ASR 模型的识别效果。

### ⭐ 3. 配置

```yaml
spring:
  ai:
    openai:
      base-url: https://api.siliconflow.cn
      api-key: ${SILICON_API_KEY}
      audio:
        transcription:
          options:
            model: TeleAI/TeleSpeechASR
```

### 4. 音频资源加载

```java
// 从 classpath 加载
@Value("classpath:/test.mp3")
private Resource resource;

// 从文件系统加载
Resource fileResource = new FileSystemResource("/path/to/audio.wav");

// 从 URL 加载
Resource urlResource = new UrlResource("https://example.com/audio.mp3");
```

### 5. 响应格式选项

| 格式 | 说明 | 适用场景 |
|------|------|----------|
| `TranscriptResponseFormat.TEXT` | 纯文本 | 只需文字内容 |
| `TranscriptResponseFormat.JSON` | JSON（含时间戳等） | 需要字级时间戳 |
| `TranscriptResponseFormat.SRT` | SRT 字幕格式 | 视频字幕生成 |
| `TranscriptResponseFormat.VTT` | WebVTT 格式 | Web 视频字幕 |

### 6. 核心类

| 类 | 作用 |
|---|---|
| `TranscriptionModel` | 语音识别统一抽象接口 |
| `OpenAiAudioTranscriptionModel` | OpenAI 兼容实现 |
| `OpenAiAudioApi` | 底层 HTTP API 封装 |
| `AudioTranscriptionPrompt` | 封装音频资源 + 选项 |
| `AudioTranscriptionResponse` | 转录结果 |

## 四、完整示例代码

```java
@RestController
public class AsrController {

    private final TranscriptionModel transcriptionModel;

    @Autowired
    public AsrController(TranscriptionModel transcriptionModel) {
        this.transcriptionModel = transcriptionModel;
    }

    // 转录 classpath 下的音频
    @GetMapping("/asr")
    public String transcribe(@Value("classpath:/test.mp3") Resource resource) {
        AudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
            .model("TeleAI/TeleSpeechASR")
            .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
            .build();

        AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(resource, options);
        AudioTranscriptionResponse response = transcriptionModel.call(prompt);
        return response.getResult().getOutput();
    }

    // 转录上传的音频文件
    @PostMapping("/asr/upload")
    public String transcribeUpload(@RequestParam("file") MultipartFile file) throws Exception {
        Resource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
        
        AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(resource,
            OpenAiAudioTranscriptionOptions.builder()
                .model("TeleAI/TeleSpeechASR")
                .build());
        
        return transcriptionModel.call(prompt).getResult().getOutput();
    }
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **DashScope SDK** | 阿里原生 SDK 调用 Paraformer 等模型 | 需要阿里特有功能 |
| **Whisper 本地部署** | 开源模型本地推理 | 离线/隐私场景 |
| **Azure Speech SDK** | 微软语音服务 | 实时流式识别 |
| **浏览器 Web Speech API** | 前端原生语音识别 | 简单语音输入 |

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| 音频格式不支持 | 模型只支持特定格式（mp3/wav/flac） | 查阅文档确认支持格式 |
| 文件名丢失 | ByteArrayResource 默认无文件名 | 重写 getFilename() |
| 识别结果不准 | 音频质量差或模型选择不当 | 尝试不同 ASR 模型对比 |
| 大文件超时 | 长音频处理耗时 | 增加超时配置或分段处理 |

## 七、本节小结

```
核心收获：
✅ 掌握 TranscriptionModel 统一接口（Spring AI 标准）
✅ 掌握 AudioTranscriptionPrompt 构建
✅ 了解多种响应格式（TEXT/JSON/SRT/VTT）
✅ 掌握文件上传 + 转录的完整链路

下一步学习：S20-manual-tool-execute → 手动控制工具执行
```


---

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


---

