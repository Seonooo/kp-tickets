package personal.ai.core.adapter.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import personal.ai.common.health.HealthCheckService;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import org.springframework.data.redis.core.RedisCallback;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Health Check Controller 단위 테스트
 * agent.md Testing Strategy - BDD Style (Given-When-Then)
 * @WebMvcTest를 사용하여 컨트롤러 계층만 테스트
 */
@WebMvcTest(HealthCheckController.class)
@DisplayName("Core Service Health Check API 단위 테스트")
class HealthCheckControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DataSource dataSource;

    @MockBean
    private HealthCheckService healthCheckService;

    @Test
    @DisplayName("헬스 체크 API는 정상 응답을 반환한다")
    void healthCheckReturnsSuccess() throws Exception {
        // Given: DB, Redis, Kafka가 정상 동작 중
        given(healthCheckService.checkDatabase(any(DataSource.class))).willReturn("UP");
        given(healthCheckService.checkRedis()).willReturn("UP");
        given(healthCheckService.checkKafka()).willReturn("UP");

        // When: 헬스 체크 엔드포인트를 호출하면
        mockMvc.perform(get("/api/v1/health")
                        .contentType(MediaType.APPLICATION_JSON))
                // Then: 200 OK와 함께 정상 응답을 반환한다
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.result").value("success"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.database").value("UP"))
                .andExpect(jsonPath("$.data.redis").value("UP"))
                .andExpect(jsonPath("$.data.kafka").value("UP"));
    }

    @Test
    @DisplayName("헬스 체크 응답은 ApiResponse 포맷을 따른다")
    void healthCheckFollowsApiResponseFormat() throws Exception {
        // Given: agent.md API Design Guidelines
        given(healthCheckService.checkDatabase(any(DataSource.class))).willReturn("UP");
        given(healthCheckService.checkRedis()).willReturn("UP");
        given(healthCheckService.checkKafka()).willReturn("UP");

        // When: 헬스 체크를 호출하면
        mockMvc.perform(get("/api/v1/health"))
                // Then: ApiResponse<T> 포맷을 따른다
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").isString())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data").isMap());
    }
}
