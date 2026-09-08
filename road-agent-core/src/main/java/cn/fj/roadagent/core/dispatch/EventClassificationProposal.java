package cn.fj.roadagent.core.dispatch;

/** 模型只能返回的受控分类结构。 */
public record EventClassificationProposal(String eventType, double confidence, String evidence) { }
