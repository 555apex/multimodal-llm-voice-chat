package cn.fj.roadagent.application.model;

/** 模型能理解的一条标准消息，role只允许system、user或assistant。 */
public record ModelMessage(String role, String content) {
    // 形参：角色，内容
    // 作用
    /*
    ModelMessage：大模型理解的精简版对话卡片，取消ConversationMessage的时间戳参数，模型不需要知道
     */
    public ModelMessage {
        role = role == null ? "user" : role.trim().toLowerCase();   // role默认为user，将role转小写
        content = content == null ? "" : content.trim();
        if (!role.matches("system|user|assistant")) {   // role仅允许为三类角色
            throw new IllegalArgumentException("不支持的模型消息角色：" + role);
        }
    }
}
