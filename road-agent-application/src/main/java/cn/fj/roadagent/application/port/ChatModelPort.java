package cn.fj.roadagent.application.port;

import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelResponse;

/**
 * 对模型厂商保持中立的接口。核心业务不能直接依赖DeepSeek的HTTP格式。
 */
@FunctionalInterface
public interface ChatModelPort {

    ModelResponse generate(ModelRequest request);
}
