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
                GenericJackson2JsonRedisSerializer serializer = buildCacheValueSerializer();

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

        /**
         * 캐시 값 직렬화기 생성 (테스트 가능하도록 분리)
         *
         * <p>record 타입 지원 및 도메인 패키지 화이트리스트를 포함한 JSON 직렬화기를 반환한다.
         * Bean 메서드와 단위 테스트가 공유하여 설정 드리프트를 방지한다.
         */
        static GenericJackson2JsonRedisSerializer buildCacheValueSerializer() {
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

                // 타입 검증기: 도메인 패키지 + 컬렉션 + JDK 스칼라/날짜 타입 허용.
                //
                // NON_FINAL 기반 RecordSupportingTypeResolver가 record 뿐 아니라 필드 수준의
                // Long/BigDecimal/enum/LocalDateTime에도 @class 타입 힌트를 붙인다. 따라서
                // PTV 역시 해당 표준 패키지를 허용해야 역직렬화가 통과한다.
                // (로컬 Redis 캐시 전용이며, 이미 java.util/personal.ai 를 허용한 상태라
                // 추가 공격 표면은 제한적.)
                BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                                .allowIfSubType("personal.ai") // 도메인 모델
                                .allowIfSubType("java.util") // List, ArrayList 등
                                .allowIfSubType("java.lang") // Long, Integer, Boolean 등 wrapper
                                .allowIfSubType("java.math") // BigDecimal, BigInteger
                                .allowIfSubType("java.time") // LocalDateTime 등
                                .build();

                // RecordSupportingTypeResolver 생성 (record 타입 지원)
                RecordSupportingTypeResolver typeResolver = new RecordSupportingTypeResolver(
                                ObjectMapper.DefaultTyping.NON_FINAL,
                                ptv);

                // ObjectMapper에 커스텀 TypeResolver 적용
                objectMapper.setDefaultTyping(typeResolver);

                return new GenericJackson2JsonRedisSerializer(objectMapper);
        }
}
