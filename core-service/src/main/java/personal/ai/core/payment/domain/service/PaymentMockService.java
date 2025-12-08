package personal.ai.core.payment.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * Payment Mock Service
 * 실제 PG 연동 없이 결제를 시뮬레이션
 *
 * Business Logic:
 * - 100% 성공 (Happy path 테스트 중심)
 * - 500ms ~ 1s 랜덤 딜레이 (네트워크 지연 시뮬레이션)
 */
@Slf4j
@Service
public class PaymentMockService {

    private static final double SUCCESS_RATE = 1.0; // Happy path: 100% 성공
    private static final int MIN_DELAY_MS = 500;
    private static final int MAX_DELAY_MS = 1000;

    private final Random random = new Random();

    /**
     * Mock 결제 처리
     *
     * @param userId        사용자 ID
     * @param reservationId 예약 ID
     * @param amount        결제 금액
     * @return 결제 성공 여부
     */
    public boolean processPayment(Long userId, Long reservationId, Long amount) {
        log.info("Processing mock payment: userId={}, reservationId={}, amount={}",
                userId, reservationId, amount);

        // 네트워크 지연 시뮬레이션 (500ms ~ 1s)
        try {
            int delay = MIN_DELAY_MS + random.nextInt(MAX_DELAY_MS - MIN_DELAY_MS + 1);
            Thread.sleep(delay);
            log.debug("Mock payment delay: {}ms", delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Mock payment delay interrupted", e);
        }

        // 80% 성공, 20% 실패
        boolean success = random.nextDouble() < SUCCESS_RATE;

        if (success) {
            log.info("Mock payment succeeded: userId={}, reservationId={}", userId, reservationId);
        } else {
            log.warn("Mock payment failed: userId={}, reservationId={}", userId, reservationId);
        }

        return success;
    }
}
