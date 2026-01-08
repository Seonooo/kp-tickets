package personal.ai.core.booking.adapter.out.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis Cache Configuration
 *
 * JDK Serialization → JSON Serialization 변경
 * - Seat 도메인 객체가 Serializable 구현 불필요
 * - 사람이 읽을 수 있는 JSON 형식으로 저장
 * - 디버깅 및 모니터링 용이
 * - CustomCacheErrorHandler로 silent failure 감지
 */
@Configuration
// @EnableCaching은 CoreServiceApplication에서 선언됨 (중복 제거)
public class RedisCacheConfig implements CachingConfigurer {

        @Value("${spring.cache.redis.time-to-live:1000}")
        private long ttlMillis;

        @Bean
        public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
                // ObjectMapper 설정: Record 타입 지원
                ObjectMapper objectMapper = new ObjectMapper();

                // Record 전용 설정: 필드만 직렬화 (getter 메서드 무시)
                // isOccupied() 같은 메서드가 "occupied" 필드로 직렬화되는 것을 방지
                objectMapper.setVisibility(
                                objectMapper.getSerializationConfig()
                                                .getDefaultVisibilityChecker()
                                                .withFieldVisibility(
                                                                com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
                                                .withGetterVisibility(
                                                                com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE)
                                                .withIsGetterVisibility(
                                                                com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE));

                // 타입 검증기: personal.ai 패키지와 java.util 컬렉션만 허용
                BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                                .allowIfSubType("personal.ai") // 도메인 모델 허용
                                .allowIfSubType("java.util") // List, ArrayList 등 허용
                                .build();

                // RecordSupportingTypeResolver 생성 (record 타입 지원)
                RecordSupportingTypeResolver typeResolver = new RecordSupportingTypeResolver(
                                ObjectMapper.DefaultTyping.NON_FINAL,
                                ptv);

                // ObjectMapper에 커스텀 TypeResolver 적용
                objectMapper.setDefaultTyping(typeResolver);

                GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

                RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMillis(ttlMillis)) // application.yml의 TTL 설정 사용
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(
                                                RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                                .disableCachingNullValues(); // null 값은 캐싱하지 않음

                return RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(config)
                                .build();
        }

        /**
         * 캐시 작업 실패 감지용 에러 핸들러
         *
         * 기본 동작은 silent failure이므로 명시적으로 로깅
         */
        @Override
        public CacheErrorHandler errorHandler() {
                return new CustomCacheErrorHandler();
        }
}
