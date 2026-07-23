package cn.fj.roadagent.interfaces.rest.agent;

import cn.fj.roadagent.application.agent.AgentMessageCommand;
import cn.fj.roadagent.application.agent.ConverseWithAgentUseCase;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executor;

/** 把Agent内部事件转换成浏览器可接收的SSE事件。 */
@RestController
@RequestMapping("/api/v1/conversations")
public final class AgentController {
    private final ConverseWithAgentUseCase agentUseCase;    // Agent对话用例创建（实现application层的interface）
    private final Executor executor;    // 线程池

    // 构造函数
    public AgentController(
            ConverseWithAgentUseCase agentUseCase,
            @Qualifier("agentExecutor") Executor executor   // 专门用于Agent的线程池
            // 由于agent会不断产生事件，需要一个专用线程池
            // 从而，用户能在前端页面持续地知道大模型的思考情况
    ) {
        this.agentUseCase = agentUseCase;
        this.executor = executor;
    }

    /*
    TrafficController：     请求 → 等 → 一次性返回JSON
    AgentController：       请求 → 建立流 → 事件1 → 事件2 → 事件3 → ... → 结束
     */
    @PostMapping(
            value = "/{conversationId}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE    // 说明Agent返回的是一个持续的事件流
    )

    public SseEmitter stream(
            @PathVariable String conversationId,    // 会话id（ @PathVariable用于从URL路径中提取变量）
            @Valid @RequestBody AgentMessageRequest request,    // 输入的文本信息
            HttpServletRequest servletRequest
    ) {

        // 构造command
        String traceId = servletRequest.getAttribute(TraceIdFilter.ATTRIBUTE_NAME).toString();
        AgentMessageCommand command = new AgentMessageCommand(
                conversationId, request.message(), traceId
        );

        // 市级行政区可能需要数百个矩形切片，流式连接最长保留300秒。
        // 预留修改点：后续如果查询市级的交通情况，模型会继续追问更具体行政区域的交通情况
        SseEmitter emitter = new SseEmitter(300_000L);

        // 异步执行
        executor.execute(() -> {
            try {
                // 传入回调，每产生一个事件就推送给浏览器
                agentUseCase.handle(command, event -> send(emitter, event.name(), event.data()));
                emitter.complete(); //全部结束
            } catch (SseSendException exception) {
                emitter.completeWithError(exception.getCause());    // 发送失败异常
            } catch (RuntimeException exception) {  // 超时异常
                emitter.completeWithError(exception);
            }
        });
        return emitter;  // 直接建立Agent专用线程池，不等待agentUseCase执行结束
    }

    private void send(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));     //
        } catch (IOException exception) {
            throw new SseSendException(exception);
        }
    }

    // 内部私有类SseSendException
    private static final class SseSendException extends RuntimeException {
        private SseSendException(IOException cause) {
            super(cause);
        }
    }
}
