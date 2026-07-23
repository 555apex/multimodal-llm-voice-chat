package cn.fj.roadagent.adapters.memory;

import cn.fj.roadagent.application.agent.ConversationMessage;
import cn.fj.roadagent.application.port.ConversationMemoryPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 开发阶段的短期记忆；重启后丢失，未来可替换为Redis。 */
public final class InMemoryConversationMemoryAdapter implements ConversationMemoryPort {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final int maxMessages;
    private final Duration idleTtl;
    private final Clock clock;

    public InMemoryConversationMemoryAdapter(int maxMessages, Duration idleTtl, Clock clock) {
        this.maxMessages = maxMessages;
        this.idleTtl = idleTtl;
        this.clock = clock;
    }

    @Override
    public List<ConversationMessage> load(String conversationId) {
        Session session = sessions.get(conversationId);
        if (session == null) {
            return List.of();
        }
        synchronized (session) {
            Instant now = clock.instant();
            if (session.lastTouched.plus(idleTtl).isBefore(now)) {
                sessions.remove(conversationId, session);
                return List.of();
            }
            session.lastTouched = now;
            return new ArrayList<>(session.messages);
        }
    }

    @Override
    public void append(String conversationId, ConversationMessage message) {
        Session session = sessions.computeIfAbsent(conversationId, ignored -> new Session(clock.instant()));
        synchronized (session) {
            session.messages.addLast(message);
            while (session.messages.size() > maxMessages) {
                session.messages.removeFirst();
            }
            session.lastTouched = clock.instant();
        }
    }

    private static final class Session {
        private final ArrayDeque<ConversationMessage> messages = new ArrayDeque<>();
        private Instant lastTouched;

        private Session(Instant lastTouched) {
            this.lastTouched = lastTouched;
        }
    }
}
