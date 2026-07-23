package cn.fj.roadagent.application.port;

import cn.fj.roadagent.application.agent.ConversationMessage;

import java.util.List;

/** 当前使用进程内实现，未来可替换Redis或PostgreSQL实现。 */
public interface ConversationMemoryPort {
    List<ConversationMessage> load(String conversationId);

    void append(String conversationId, ConversationMessage message);
}
