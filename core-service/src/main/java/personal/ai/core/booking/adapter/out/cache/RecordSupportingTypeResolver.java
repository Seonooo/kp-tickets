package personal.ai.core.booking.adapter.out.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;

/**
 * Record 타입 지원 TypeResolver (최소 구현)
 *
 * 참고: https://techblog.woowahan.com/22767/
 */
public class RecordSupportingTypeResolver extends StdTypeResolverBuilder {

    private ObjectMapper.DefaultTyping _appliesFor;

    public RecordSupportingTypeResolver(ObjectMapper.DefaultTyping defaultTyping,
                                        PolymorphicTypeValidator polymorphicTypeValidator) {
        this._appliesFor = defaultTyping;
        // 부모 클래스 메서드 호출로 초기화
        this.init(JsonTypeInfo.Id.CLASS, null);
        this.inclusion(JsonTypeInfo.As.PROPERTY);
        // polymorphicTypeValidator는 내부적으로 사용됨 (저장 불필요)
    }

    /**
     * 타입 정보 포함 여부 결정 (핵심 메서드)
     *
     * record일 경우 true 반환하여 타입 정보 강제 추가
     */
    public boolean useForType(JavaType t) {
        // record 타입이면 무조건 타입 정보 추가
        if (t.getRawClass().isRecord()) {
            return true;
        }

        // NON_FINAL 로직 (부모 클래스 로직 복제)
        if (_appliesFor == ObjectMapper.DefaultTyping.NON_FINAL) {
            while (t.isArrayType()) {
                t = t.getContentType();
            }
            while (t.isReferenceType()) {
                t = t.getReferencedType();
            }
            // final이 아니고 Object가 아니면 타입 정보 추가
            return !t.isFinal() && !t.getRawClass().equals(Object.class);
        }

        return false;
    }
}
