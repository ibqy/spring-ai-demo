package com.git.hui.springai.ali.mvc;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import io.micrometer.common.util.StringUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

/**
 * 基于 ChatModel 实现简单的推理问答对话
 *
 * @author YiHui
 * @date 2026/4/1
 */
@RestController
public class ThinkingController {
    private final ChatModel zhipuModel;

    public ThinkingController(Environment environment) {
        OpenAiApi zhipuApi = OpenAiApi.builder().apiKey(getApiKey(environment, "zhipuai-api-key"))
                .baseUrl("https://open.bigmodel.cn")
                .completionsPath("/api/paas/v4/chat/completions")
                .build();
        zhipuModel = OpenAiChatModel.builder()
                .openAiApi(zhipuApi)
                .defaultOptions(OpenAiChatOptions.builder().model("glm-4.5-flash")
//                        支持思考推理（前提是大模型本身要支持这个能力）
//                        默认是开启推理，可以使用下面的方式关闭/开启
//                        .extraBody(Map.of("thinking", Map.of("type", "enabled")))
                        .build())
                .build();
    }

    private String getApiKey(Environment environment, String key) {
        // 1. 通过 --dash-api-key 启动命令传参
        String val = environment.getProperty(key);
        if (StringUtils.isBlank(val)) {
            // 2. 通过jvm传参 -Ddash-api-key=
            val = System.getProperty(key);
            if (val == null) {
                // 3. 通过环境变量传参
                val = System.getenv(key);
            }
        }
        return val;
    }

    /**
     * 简单的推理问答对话（流式返回思考过程和结果）
     */
    @GetMapping(path = "simpleChat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter simpleChat(String msg) throws Exception {
        SseEmitter sseEmitter = new SseEmitter();
        var res = zhipuModel.stream(new Prompt(msg));
        StringBuilder content = new StringBuilder();
        StringBuilder reason = new StringBuilder();
        res.subscribe(s -> {
            Generation generation = s.getResult();
            var r = generation.getOutput().getMetadata().get("reasoningContent");
            if (r != null) {
                reason.append(r);
                System.out.println("思考:" + r);
                sendMsg(sseEmitter, "思考:" + r);
            }
            var t = generation.getOutput().getText();
            if (t != null) {
                sendMsg(sseEmitter, "输出:" + r);
                content.append(t);
                System.out.println("结果:" + t);
            }
        });
        return sseEmitter;
    }

    public void sendMsg(SseEmitter sseEmitter, String msg) {
        try {
            sseEmitter.send(msg);
        } catch (IOException e) {
            System.out.println("send error");
        }
    }


    @GetMapping(path = "agentChat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux agentChat(String msg) throws GraphRunnerException {
        ReactAgent agent = ReactAgent.builder()
                .name("thinkingAgent")
                .model(zhipuModel)
                .enableLogging(true)
                .build();
        Flux res = agent.stream(msg);
        return res.map(s -> {
            if (s instanceof StreamingOutput<?>) {
                System.out.println("当前类型:" + ((StreamingOutput<?>) s).getOutputType());

                var data = ((StreamingOutput<?>) s).message();
                if (data != null) {
                    String thinking = (String) data.getMetadata().get("reasoningContent");
                    if (thinking != null) {
                        System.out.println("思考:" + thinking);
                        return "思考:" + thinking;
                    } else {
                        String content = data.getText();
                        System.out.println("结果:" + content);
                        return "输出:" + content;
                    }
                }
            }
            System.out.println(s);
            return s;
        });
    }
}
