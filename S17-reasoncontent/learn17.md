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
