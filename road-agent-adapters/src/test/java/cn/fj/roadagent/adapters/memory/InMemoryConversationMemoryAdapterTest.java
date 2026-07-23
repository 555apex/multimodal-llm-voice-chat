package cn.fj.roadagent.adapters.memory;

import cn.fj.roadagent.application.agent.ConversationMessage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryConversationMemoryAdapterTest {

    @Test
    void shouldKeepOnlyConfiguredNumberOfMessages() {
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        InMemoryConversationMemoryAdapter adapter = new InMemoryConversationMemoryAdapter(
                2, Duration.ofMinutes(60), clock
        );
        adapter.append("c1", new ConversationMessage("user", "1", clock.instant()));
        adapter.append("c1", new ConversationMessage("assistant", "2", clock.instant()));
        adapter.append("c1", new ConversationMessage("user", "3", clock.instant()));

        assertEquals(2, adapter.load("c1").size());
        assertEquals("2", adapter.load("c1").get(0).content());
    }
}
