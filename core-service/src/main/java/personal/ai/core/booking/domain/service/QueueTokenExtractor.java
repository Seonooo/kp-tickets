package personal.ai.core.booking.domain.service;

import personal.ai.core.booking.domain.exception.QueueTokenInvalidException;

/**
 * Queue Token Extractor
 * 토큰에서 정보 추출 유틸리티
 */
public final class QueueTokenExtractor {

    private static final int EXPECTED_TOKEN_PARTS = 3;
    private static final int CONCERT_ID_INDEX = 0;
    private static final String TOKEN_DELIMITER = ":";

    private QueueTokenExtractor() {
        // Utility class
    }

    /**
     * 토큰에서 concertId 추출
     * 토큰 형식: {concertId}:{userId}:{counter}
     *
     * @param queueToken 대기열 토큰
     * @return concertId
     * @throws QueueTokenInvalidException 토큰이 null/blank이거나 형식이 올바르지 않은 경우
     */
    public static String extractConcertId(String queueToken) {
        validateToken(queueToken);
        String[] parts = queueToken.split(TOKEN_DELIMITER);
        validateTokenFormat(parts, queueToken);
        return parts[CONCERT_ID_INDEX];
    }

    private static void validateToken(String queueToken) {
        if (queueToken == null || queueToken.isBlank()) {
            throw new QueueTokenInvalidException();
        }
    }

    private static void validateTokenFormat(String[] parts, String queueToken) {
        if (parts.length < EXPECTED_TOKEN_PARTS) {
            throw new QueueTokenInvalidException(
                    "Invalid token format. Expected " + EXPECTED_TOKEN_PARTS + " parts, got " + parts.length);
        }

        if (parts[CONCERT_ID_INDEX].isBlank()) {
            throw new QueueTokenInvalidException("ConcertId cannot be blank in token");
        }
    }
}
