package cn.fj.roadagent.application.agent;

import java.time.Instant;

public record ConversationMessage(String role, String content, Instant createdAt) {
    // 形参：角色，内容，时间戳
    // 作用
    /*
    ConversationMessage：Agent上下文记忆里的对话记录卡
    存入用户消息，存入AI回答
    比如：一个会话的上下文
    [0] ConversationMessage { role: "user",      content: "福州五四路堵不堵？", createdAt: 10:30:00 }
    [1] ConversationMessage { role: "assistant", content: "五四路当前畅通...",   createdAt: 10:30:05 }
    [2] ConversationMessage { role: "user",      content: "附近呢？",           createdAt: 10:31:00 }
    [3] ConversationMessage { role: "assistant", content: "...",               createdAt: 10:31:08 }
     */
}
