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
