package cn.fj.roadagent.application.model;

/** 模型每返回一小段文字，就调用一次onDelta。 */
@FunctionalInterface
public interface ModelStreamListener {

    void onDelta(String content);
}
