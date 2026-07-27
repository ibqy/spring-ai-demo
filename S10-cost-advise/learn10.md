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
