package personal.ai.core.booking.adapter.out.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import personal.ai.core.booking.domain.model.Seat;
import personal.ai.core.booking.domain.model.SeatGrade;
import personal.ai.core.booking.domain.model.SeatStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RedisCacheConfig} 직렬화 라운드트립 검증.
 *
 * <p>실제 Bean 구성에 사용되는 {@code buildCacheValueSerializer()}를 그대로 사용하여
 * {@code List<Seat>} (record + BigDecimal + enum 혼합 컬렉션)이 손실 없이
 * 직렬화/역직렬화되는지 확인한다.
 *
 * <p>리스크 대응:
 * <ul>
 *   <li>record 타입 정보 누락 시 역직렬화 실패</li>
 *   <li>PolymorphicTypeValidator가 {@code personal.ai} 패키지를 거부하는 회귀</li>
 *   <li>파생 접근자({@code isReserved()})가 JSON 필드로 새어 나가는 회귀</li>
 * </ul>
 */
@DisplayName("RedisCacheConfig 직렬화 검증")
class RedisCacheConfigSerializationTest {

    private final GenericJackson2JsonRedisSerializer serializer =
            RedisCacheConfig.buildCacheValueSerializer();

    private Seat seat(long id, String number, SeatStatus status) {
        return new Seat(id, 100L, number, SeatGrade.VIP, BigDecimal.valueOf(100000), status);
    }

    @Test
    @DisplayName("List<Seat> 라운드트립 - 값 손실 없이 복원된다")
    void listOfSeats_roundTrip_preservesValues() {
        // given
        List<Seat> original = List.of(
                seat(1L, "A1", SeatStatus.AVAILABLE),
                seat(2L, "A2", SeatStatus.RESERVED),
                seat(3L, "A3", SeatStatus.OCCUPIED)
        );

        // when
        byte[] bytes = serializer.serialize(original);
        Object restored = serializer.deserialize(bytes);

        // then
        assertThat(restored).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Seat> restoredList = (List<Seat>) restored;
        assertThat(restoredList).containsExactlyElementsOf(original);
    }

    @Test
    @DisplayName("빈 List<Seat> 라운드트립 - 빈 리스트로 복원된다")
    void emptyList_roundTrip_returnsEmptyList() {
        List<Seat> original = List.of();

        byte[] bytes = serializer.serialize(original);
        Object restored = serializer.deserialize(bytes);

        assertThat(restored).isInstanceOf(List.class);
        assertThat((List<?>) restored).isEmpty();
    }

    @Test
    @DisplayName("직렬화된 JSON에 파생 접근자(isReserved, isAvailable, isOccupied)가 노출되지 않는다")
    void serializedJson_doesNotLeakDerivedAccessors() {
        Seat seat = seat(1L, "A1", SeatStatus.RESERVED);

        byte[] bytes = serializer.serialize(seat);
        String json = new String(bytes);

        // record 컴포넌트는 포함되어야 한다 (status는 NON_FINAL 타입 wrapping됨)
        assertThat(json).contains("\"seatNumber\":\"A1\"");
        assertThat(json).contains("RESERVED");
        // 파생 접근자가 필드처럼 새어 나오면 안 된다
        assertThat(json).doesNotContain("\"reserved\"");
        assertThat(json).doesNotContain("\"available\"");
        assertThat(json).doesNotContain("\"occupied\"");
    }

    @Test
    @DisplayName("Seat 단건 라운드트립 - record 타입 정보(@class)가 보존된다")
    void singleSeat_roundTrip_preservesRecordType() {
        Seat original = seat(42L, "Z9", SeatStatus.AVAILABLE);

        byte[] bytes = serializer.serialize(original);
        Object restored = serializer.deserialize(bytes);

        assertThat(restored)
                .isInstanceOf(Seat.class)
                .isEqualTo(original);
    }
}
