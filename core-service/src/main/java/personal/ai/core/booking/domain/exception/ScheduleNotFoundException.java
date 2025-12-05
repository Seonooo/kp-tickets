package personal.ai.core.booking.domain.exception;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;

/**
 * Schedule Not Found Exception
 * 콘서트 일정을 찾을 수 없을 때 발생하는 예외
 */
public class ScheduleNotFoundException extends BusinessException {
    public ScheduleNotFoundException(Long scheduleId) {
        super(ErrorCode.SCHEDULE_NOT_FOUND,
                String.format("Concert schedule not found: scheduleId=%d", scheduleId));
    }
}
