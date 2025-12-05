package personal.ai.core.booking.adapter.out.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis Configuration
 * Redis Keyspace Notification 설정
 */
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final RedisExpirationListener redisExpirationListener;

    /**
     * Redis Message Listener Container 설정
     * Keyspace Notification에서 expired 이벤트를 구독
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // __keyevent@*__:expired 패턴 구독 (모든 DB의 expired 이벤트)
        // 특정 DB만 구독하려면: __keyevent@0__:expired
        container.addMessageListener(redisExpirationListener, new PatternTopic("__keyevent@*__:expired"));

        return container;
    }

    @Bean
    public org.springframework.data.redis.core.script.RedisScript<Long> releaseLockScript() {
        org.springframework.core.io.ClassPathResource scriptSource = new org.springframework.core.io.ClassPathResource(
                "scripts/release_lock.lua");
        return org.springframework.data.redis.core.script.RedisScript.of(scriptSource, Long.class);
    }
}
