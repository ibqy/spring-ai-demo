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
