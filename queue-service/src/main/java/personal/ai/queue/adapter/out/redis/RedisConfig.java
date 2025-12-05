package personal.ai.queue.adapter.out.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis Configuration
 * String Key, String Value 기반 RedisTemplate 설정
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key Serializer: String
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value Serializer: String (Hash도 String으로 저장)
        template.setValueSerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public org.springframework.data.redis.core.script.RedisScript<Long> addToActiveQueueScript() {
        org.springframework.core.io.ClassPathResource scriptSource = new org.springframework.core.io.ClassPathResource(
                "scripts/add_to_active_queue.lua");
        return org.springframework.data.redis.core.script.RedisScript.of(scriptSource, Long.class);
    }

    @Bean
    public org.springframework.data.redis.core.script.RedisScript<Long> removeExpiredTokensScript() {
        org.springframework.core.io.ClassPathResource scriptSource = new org.springframework.core.io.ClassPathResource(
                "scripts/remove_expired_tokens.lua");
        return org.springframework.data.redis.core.script.RedisScript.of(scriptSource, Long.class);
    }

    @Bean
    public org.springframework.data.redis.core.script.RedisScript<Long> updateTokenExpirationScript() {
        org.springframework.core.io.ClassPathResource scriptSource = new org.springframework.core.io.ClassPathResource(
                "scripts/update_token_expiration.lua");
        return org.springframework.data.redis.core.script.RedisScript.of(scriptSource, Long.class);
    }

    @Bean
    public org.springframework.data.redis.core.script.RedisScript<Long> removeFromActiveQueueScript() {
        org.springframework.core.io.ClassPathResource scriptSource = new org.springframework.core.io.ClassPathResource(
                "scripts/remove_from_active_queue.lua");
        return org.springframework.data.redis.core.script.RedisScript.of(scriptSource, Long.class);
    }

    @Bean
    public org.springframework.data.redis.core.script.RedisScript<String> moveToActiveQueueScript() {
        org.springframework.core.io.ClassPathResource scriptSource = new org.springframework.core.io.ClassPathResource(
                "scripts/move_to_active_queue.lua");
        return org.springframework.data.redis.core.script.RedisScript.of(scriptSource, String.class);
    }

    @Bean
    public org.springframework.data.redis.core.script.RedisScript<Long> activateTokenScript() {
        org.springframework.core.io.ClassPathResource scriptSource = new org.springframework.core.io.ClassPathResource(
                "scripts/activate_token.lua");
        return org.springframework.data.redis.core.script.RedisScript.of(scriptSource, Long.class);
    }
}
