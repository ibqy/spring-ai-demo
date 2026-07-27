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
