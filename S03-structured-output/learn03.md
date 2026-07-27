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
