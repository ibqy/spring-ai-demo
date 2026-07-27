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
