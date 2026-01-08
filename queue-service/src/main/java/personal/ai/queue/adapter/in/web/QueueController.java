package personal.ai.queue.adapter.in.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import personal.ai.common.dto.ApiResponse;
import personal.ai.queue.adapter.in.web.dto.*;
import personal.ai.queue.adapter.in.web.service.QueuePollingService;
import personal.ai.queue.application.port.in.*;
import personal.ai.queue.domain.model.QueuePosition;
import personal.ai.queue.domain.model.QueueToken;

/**
 * Queue REST Controller
 * 대기열 API 엔드포인트
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
@org.springframework.validation.annotation.Validated
public class QueueController {

        private final EnterQueueUseCase enterQueueUseCase;
        private final GetQueueStatusUseCase getQueueStatusUseCase;
        private final ActivateTokenUseCase activateTokenUseCase;
        private final ExtendTokenUseCase extendTokenUseCase;
        private final ValidateTokenUseCase validateTokenUseCase;
        private final RemoveFromQueueUseCase removeFromQueueUseCase;
        private final QueuePollingService queuePollingService;

        /**
         * 대기열 진입
         * POST /api/v1/queue/enter
         */
        @PostMapping("/enter")
        public ResponseEntity<ApiResponse<QueuePositionResponse>> enterQueue(
                        @Valid @RequestBody EnterQueueRequest request) {

                log.info("Enter queue request: concertId={}, userId={}",
                                request.concertId(), request.userId());

                EnterQueueUseCase.EnterQueueCommand command = new EnterQueueUseCase.EnterQueueCommand(
                                request.concertId(),
                                request.userId());

                QueuePosition position = enterQueueUseCase.enter(command);
                QueuePositionResponse response = QueuePositionResponse.from(position);

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(ApiResponse.success("대기열에 진입했습니다.", response));
        }

        /**
         * 대기열 상태 조회
         * GET /api/v1/queue/status?concertId={concertId}&userId={userId}
         */
        @GetMapping("/status")
        public ResponseEntity<ApiResponse<QueueTokenResponse>> getQueueStatus(
                        @RequestParam @jakarta.validation.constraints.NotBlank String concertId,
                        @RequestParam @jakarta.validation.constraints.NotBlank String userId) {

                log.debug("Get queue status: concertId={}, userId={}", concertId, userId);

                GetQueueStatusUseCase.GetQueueStatusQuery query = new GetQueueStatusUseCase.GetQueueStatusQuery(
                                concertId, userId);

                QueueToken token = getQueueStatusUseCase.getStatus(query);
                QueueTokenResponse response = QueueTokenResponse.from(token);

                return ResponseEntity.ok(
                                ApiResponse.success("대기열 상태 조회 완료", response));
        }

        /**
         * 토큰 활성화 (READY -> ACTIVE)
         * 예매 페이지 최초 접속 시 호출
         * POST /api/v1/queue/activate
         */
        @PostMapping("/activate")
        public ResponseEntity<ApiResponse<QueueTokenResponse>> activateToken(
                        @RequestParam @jakarta.validation.constraints.NotBlank String concertId,
                        @RequestParam @jakarta.validation.constraints.NotBlank String userId) {

                log.info("Activate token: concertId={}, userId={}", concertId, userId);

                ActivateTokenUseCase.ActivateTokenCommand command = new ActivateTokenUseCase.ActivateTokenCommand(
                                concertId, userId);

                QueueToken token = activateTokenUseCase.activate(command);
                QueueTokenResponse response = QueueTokenResponse.from(token);

                return ResponseEntity.ok(
                                ApiResponse.success("토큰이 활성화되었습니다.", response));
        }

        /**
         * 토큰 연장 (최대 2회)
         * POST /api/v1/queue/extend
         */
        @PostMapping("/extend")
        public ResponseEntity<ApiResponse<QueueTokenResponse>> extendToken(
                        @Valid @RequestBody ExtendTokenRequest request) {

                log.info("Extend token: concertId={}, userId={}",
                                request.concertId(), request.userId());

                ExtendTokenUseCase.ExtendTokenCommand command = new ExtendTokenUseCase.ExtendTokenCommand(
                                request.concertId(),
                                request.userId());

                QueueToken token = extendTokenUseCase.extend(command);
                QueueTokenResponse response = QueueTokenResponse.from(token);

                return ResponseEntity.ok(
                                ApiResponse.success("토큰 유효 시간이 연장되었습니다.", response));
        }

        /**
         * 토큰 검증
         * POST /api/v1/queue/validate
         * 예매/결제 API 호출 시 사용
         */
        @PostMapping("/validate")
        public ResponseEntity<ApiResponse<Void>> validateToken(
                        @Valid @RequestBody ValidateTokenRequest request) {

                log.debug("Validate token: concertId={}, userId={}",
                                request.concertId(), request.userId());

                ValidateTokenUseCase.ValidateTokenQuery query = new ValidateTokenUseCase.ValidateTokenQuery(
                                request.concertId(),
                                request.userId(),
                                request.token());

                validateTokenUseCase.validate(query);

                return ResponseEntity.ok(
                                ApiResponse.success("유효한 토큰입니다.", null));
        }

        /**
         * 대기열 상태 실시간 구독 (SSE)
         * GET /api/v1/queue/subscribe?concertId={concertId}&userId={userId}
         *
         * 클라이언트가 이 엔드포인트에 연결하면 상태 변경을 실시간으로 수신
         * - WAITING -> READY: 예매 페이지 진입 가능 알림
         * - 순번 변경: 현재 대기 순번 업데이트
         */
        @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter subscribeQueueStatus(
                        @RequestParam @jakarta.validation.constraints.NotBlank String concertId,
                        @RequestParam @jakarta.validation.constraints.NotBlank String userId) {

                log.info("SSE subscription request: concertId={}, userId={}", concertId, userId);

                return queuePollingService.subscribe(concertId, userId);
        }

        /**
         * 대기열에서 제거 (Phase 4: Queue 순환 테스트용)
         * DELETE /api/v1/queue/remove?concertId={concertId}&userId={userId}
         *
         * K6 순환 테스트에서 Active Queue 사용 완료 후 호출
         */
        @DeleteMapping("/remove")
        public ResponseEntity<ApiResponse<Void>> removeFromQueue(
                        @RequestParam @jakarta.validation.constraints.NotBlank String concertId,
                        @RequestParam @jakarta.validation.constraints.NotBlank String userId) {

                log.info("Remove from queue: concertId={}, userId={}", concertId, userId);

                RemoveFromQueueUseCase.RemoveFromQueueCommand command =
                        new RemoveFromQueueUseCase.RemoveFromQueueCommand(concertId, userId);

                removeFromQueueUseCase.removeFromQueue(command);

                return ResponseEntity.ok(
                                ApiResponse.success("대기열에서 제거되었습니다.", null));
        }
}
