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
