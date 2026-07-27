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
