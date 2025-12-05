package personal.ai.core.booking.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import personal.ai.core.booking.domain.model.Concert;

/**
 * Concert JPA Entity
 * 콘서트 테이블 매핑
 */
@Entity
@Table(name = "concerts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConcertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 도메인 모델로 변환
     */
    public Concert toDomain() {
        return new Concert(id, name, description);
    }
}
