# S11-image-model：文生图模型

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | 如何通过 Spring AI 调用图像生成模型，根据文本描述生成图片 |
| **适用场景** | AI 绘画、海报生成、产品图设计、创意内容生产 |
| **前置知识** | S01 Spring AI 基础、HTTP 响应流处理 |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────┐
│  1. 引入模型 Starter（含 ImageModel 自动配置）            │
│  2. 注入 ImageModel Bean                                 │
│  3. 构建 ImagePrompt（文本描述 + 图像参数）               │
│  4. 调用 imgModel.call() 获取 ImageResponse              │
│  5. 从响应中提取图片 URL                                 │
│  6. 下载图片 / 直接返回 URL / 转为流输出                  │
└─────────────────────────────────────────────────────────┘
```

## 三、核心知识点（⭐ 重点）

### ⭐ 1. ImageModel 接口

```java
private final ImageModel imgModel;  // 自动注入（如 ZhiPuAiImageModel）
```

> Spring AI 的图像生成与对话模型类似，通过统一的 `ImageModel` 接口抽象不同厂商实现。

### ⭐ 2. 构建图像生成请求

```java
ImageResponse response = imgModel.call(new ImagePrompt(msg,
    ImageOptionsBuilder.builder()
        .height(1024)
        .width(1024)
        .model("CogView-3-Flash")     // 图像模型名
        .responseFormat("png")         // 返回格式
        .style("natural")              // vivid=生动 / natural=自然
        .build()));
```

| 参数 | 说明 | 常用值 |
|------|------|--------|
| `width/height` | 图片尺寸 | 1024x1024、1792x1024 |
| `model` | 图像模型 | CogView-3-Flash、dall-e-3 |
| `style` | 风格 | vivid（生动）/ natural（自然） |
| `responseFormat` | 输出格式 | png / url |

### ⭐ 3. 获取生成结果

```java
Image img = response.getResult().getOutput();
String url = img.getUrl();  // 远程图片 URL（有时效性）
```

### 4. 将远程图片转为流返回前端

```java
@GetMapping(value = "/img/gen", produces = "application/octet-stream")
public void genImg(String msg, HttpServletResponse res) throws Exception {
    ImageResponse response = imgModel.call(new ImagePrompt(msg, options));
    String imgUrl = response.getResult().getOutput().getUrl();
    
    BufferedImage image = ImageIO.read(new URL(imgUrl));
    res.setContentType("image/png");
    ImageIO.write(image, "png", res.getOutputStream());
}
```

### 5. 核心类

| 类 | 作用 |
|---|---|
| `ImageModel` | 图像生成统一抽象接口 |
| `ImagePrompt` | 封装提示词 + 图像选项 |
| `ImageOptionsBuilder` | 配置尺寸、模型、风格等参数 |
| `ImageResponse` | 响应结果，包含图片 URL 或 Base64 |
| `Image` | 单张生成图片（url / b64Json） |

## 四、完整示例代码

```java
@RestController
public class ImageController {

    private final ImageModel imgModel;

    @Autowired
    public ImageController(ImageModel imgModel) {
        this.imgModel = imgModel;
    }

    // 返回图片 URL
    @GetMapping("/img/url")
    public String genImgUrl(String msg) {
        ImageResponse response = imgModel.call(new ImagePrompt(msg,
            ImageOptionsBuilder.builder()
                .height(1024).width(1024)
                .model("CogView-3-Flash")
                .style("natural")
                .build()));
        return response.getResult().getOutput().getUrl();
    }

    // 直接输出图片流
    @GetMapping(value = "/img/gen", produces = "application/octet-stream")
    public void genImg(String msg, HttpServletResponse res) throws Exception {
        ImageResponse response = imgModel.call(new ImagePrompt(msg,
            ImageOptionsBuilder.builder()
                .height(1024).width(1024)
                .model("CogView-3-Flash")
                .build()));
        
        BufferedImage image = ImageIO.read(new URL(response.getResult().getOutput().getUrl()));
        res.setContentType("image/png");
        ImageIO.write(image, "png", res.getOutputStream());
    }
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **OpenAI DALL-E Starter** | `spring-ai-starter-model-openai` 含 ImageModel | 使用 DALL-E 系列 |
| **Stability AI** | Spring AI 有对应 Starter | 使用 Stable Diffusion |
| **直接调 SDK** | 使用厂商原生 SDK（如智谱 SDK） | 需要厂商特有参数 |
| **ComfyUI/SD WebUI API** | 本地部署开源模型 + HTTP 调用 | 私有化部署、成本敏感 |

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| 图片 URL 过期无法访问 | 生成的 URL 有时效性（通常几分钟） | 及时下载或转存到 OSS |
| ImageModel Bean 不存在 | Starter 未包含图像模型配置 | 确认 Starter 支持 ImageModel |
| 生成速度慢 | 图像生成本身耗时较长 | 改为异步 + 轮询结果 |
| 图片尺寸不支持 | 模型只支持特定尺寸 | 查阅模型文档确认支持的尺寸 |

## 七、本节小结

```
核心收获：
✅ 掌握 ImageModel 统一接口及 ImagePrompt 构建
✅ 掌握图像参数配置（尺寸/风格/模型）
✅ 掌握图片 URL 获取与流式输出
✅ 了解生成 URL 有时效性，需及时处理

下一步学习：S12-multimodality-model → 让模型"看图说话"
```
