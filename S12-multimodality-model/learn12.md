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
