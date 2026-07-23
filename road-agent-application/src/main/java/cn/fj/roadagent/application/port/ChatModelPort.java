package cn.fj.roadagent.application.port;

import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.model.ModelStreamListener;

/**
 * 对模型厂商保持中立的接口。核心业务不能直接依赖DeepSeek的HTTP格式。
 */
public interface ChatModelPort {

    /** 一次性返回文本，供原交通接口兼容使用。 */
    ModelResponse generate(ModelRequest request);

    /** 让模型生成JSON，并转换成我方指定的Java对象。 */
    <T> T generateStructured(ModelRequest request, Class<T> resultType);

    /** 流式返回文本，供对话界面逐步显示。 */
    void stream(ModelRequest request, ModelStreamListener listener);
}
