#!/usr/bin/env node

/**
 * Java 코드 자동 검사 스크립트
 *
 * 검사 항목:
 * 1. 디버그 코드 (System.out.println)
 * 2. Entity 외부 노출 (Controller에서 Entity 반환)
 * 3. 트랜잭션 내 외부 I/O
 * 4. 하드코딩된 비밀번호/시크릿
 * 5. N+1 패턴 가능성
 * 6. Redis 원자성 미보장
 */

const fs = require('fs');
const path = require('path');

const filePath = process.argv[2];

// Java 파일이 아니면 스킵
if (!filePath || !filePath.endsWith('.java')) {
    process.exit(0);
}

// 파일이 존재하지 않으면 스킵
if (!fs.existsSync(filePath)) {
    process.exit(0);
}

const content = fs.readFileSync(filePath, 'utf-8');
const lines = content.split('\n');
const fileName = path.basename(filePath);
const warnings = [];
const errors = [];

// ============================================
// 검사 함수들
// ============================================

function checkDebugCode() {
    lines.forEach((line, index) => {
        const lineNum = index + 1;

        // System.out.println
        if (line.includes('System.out.print')) {
            warnings.push({
                line: lineNum,
                type: 'DEBUG_CODE',
                message: 'System.out.println 발견 - Logger 사용 권장',
                code: line.trim()
            });
        }

        // System.err.println
        if (line.includes('System.err.print')) {
            warnings.push({
                line: lineNum,
                type: 'DEBUG_CODE',
                message: 'System.err.println 발견 - Logger 사용 권장',
                code: line.trim()
            });
        }

        // e.printStackTrace()
        if (line.includes('.printStackTrace()')) {
            warnings.push({
                line: lineNum,
                type: 'DEBUG_CODE',
                message: 'printStackTrace() 발견 - Logger로 예외 로깅 권장',
                code: line.trim()
            });
        }
    });
}

function checkEntityExposure() {
    // Controller 파일인 경우만 검사
    if (!fileName.includes('Controller')) return;

    // import 구문에서 Entity 패키지 확인
    const entityImports = [];
    lines.forEach((line) => {
        if (line.includes('import') && line.includes('.domain.model.')) {
            const match = line.match(/import\s+[\w.]+\.(\w+);/);
            if (match) {
                entityImports.push(match[1]);
            }
        }
    });

    // 반환 타입에서 Entity 직접 반환 확인
    lines.forEach((line, index) => {
        const lineNum = index + 1;
        entityImports.forEach(entity => {
            // ResponseEntity<Entity> 또는 List<Entity> 패턴
            const pattern = new RegExp(`(ResponseEntity<${entity}>|List<${entity}>|${entity}\\s+\\w+\\()`);
            if (pattern.test(line) && !line.includes('Response') && !line.includes('Dto')) {
                errors.push({
                    line: lineNum,
                    type: 'ENTITY_EXPOSURE',
                    message: `Entity(${entity}) 외부 노출 - DTO로 변환 필요`,
                    code: line.trim()
                });
            }
        });
    });
}

function checkTransactionScope() {
    // Service 파일인 경우만 검사
    if (!fileName.includes('Service')) return;

    let inTransaction = false;
    let transactionStart = 0;

    lines.forEach((line, index) => {
        const lineNum = index + 1;

        // @Transactional 시작
        if (line.includes('@Transactional') && !line.includes('readOnly')) {
            inTransaction = true;
            transactionStart = lineNum;
        }

        // 트랜잭션 내 외부 I/O 검사
        if (inTransaction) {
            // Kafka 발행
            if (line.includes('kafkaTemplate.send') || line.includes('KafkaTemplate')) {
                errors.push({
                    line: lineNum,
                    type: 'TX_EXTERNAL_IO',
                    message: '트랜잭션 내 Kafka 발행 - AFTER_COMMIT 사용 권장',
                    code: line.trim()
                });
            }

            // 외부 API 호출
            if (line.includes('restClient.') || line.includes('RestClient') ||
                line.includes('webClient.') || line.includes('restTemplate.')) {
                warnings.push({
                    line: lineNum,
                    type: 'TX_EXTERNAL_IO',
                    message: '트랜잭션 내 외부 API 호출 - 트랜잭션 범위 최소화 권장',
                    code: line.trim()
                });
            }
        }

        // 메서드 종료 (간단한 휴리스틱)
        if (inTransaction && line.trim() === '}' && !line.includes('if') && !line.includes('for')) {
            inTransaction = false;
        }
    });
}

function checkHardcodedSecrets() {
    const secretPatterns = [
        { pattern: /password\s*=\s*["'][^"']+["']/i, type: 'PASSWORD' },
        { pattern: /secret\s*=\s*["'][^"']+["']/i, type: 'SECRET' },
        { pattern: /api[_-]?key\s*=\s*["'][^"']+["']/i, type: 'API_KEY' },
        { pattern: /token\s*=\s*["'][A-Za-z0-9+/=]{20,}["']/i, type: 'TOKEN' },
    ];

    lines.forEach((line, index) => {
        const lineNum = index + 1;

        // 주석이면 스킵
        if (line.trim().startsWith('//') || line.trim().startsWith('*')) return;

        secretPatterns.forEach(({ pattern, type }) => {
            if (pattern.test(line)) {
                errors.push({
                    line: lineNum,
                    type: 'HARDCODED_SECRET',
                    message: `하드코딩된 ${type} 발견 - 환경변수 또는 Vault 사용 권장`,
                    code: line.trim().substring(0, 50) + '...'
                });
            }
        });
    });
}

function checkRedisAtomicity() {
    // Redis 관련 파일인 경우만 검사
    if (!content.includes('RedisTemplate') && !content.includes('StringRedisTemplate')) return;

    lines.forEach((line, index) => {
        const lineNum = index + 1;

        // get 후 set 패턴 (Check-Then-Act)
        if (line.includes('.opsForValue().get(') || line.includes('.opsForHash().get(')) {
            // 다음 몇 줄 내에 set이 있는지 확인
            for (let i = index + 1; i < Math.min(index + 5, lines.length); i++) {
                if (lines[i].includes('.set(') || lines[i].includes('.put(')) {
                    warnings.push({
                        line: lineNum,
                        type: 'REDIS_ATOMICITY',
                        message: 'GET 후 SET 패턴 - Race Condition 가능성, SETNX 또는 Lua Script 권장',
                        code: line.trim()
                    });
                    break;
                }
            }
        }
    });
}

function checkNPlusOne() {
    // Repository 파일인 경우만 검사
    if (!fileName.includes('Repository')) return;

    lines.forEach((line, index) => {
        const lineNum = index + 1;

        // @Query에서 JOIN FETCH 없이 연관 조회
        if (line.includes('@Query') && line.includes('SELECT') &&
            !line.includes('JOIN FETCH') && !line.includes('join fetch')) {

            // 다음 줄에서 연관 관계 확인
            const nextLines = lines.slice(index, index + 3).join(' ');
            if (nextLines.includes('List<') && !nextLines.includes('Projection')) {
                warnings.push({
                    line: lineNum,
                    type: 'N_PLUS_ONE',
                    message: 'N+1 가능성 - JOIN FETCH 또는 @EntityGraph 고려',
                    code: line.trim()
                });
            }
        }
    });
}

function checkLombokMisuse() {
    // record 타입에 @Data 사용
    if (content.includes('@Data') && content.includes('record ')) {
        const dataLine = lines.findIndex(l => l.includes('@Data'));
        if (dataLine >= 0) {
            errors.push({
                line: dataLine + 1,
                type: 'LOMBOK_MISUSE',
                message: 'record에 @Data 사용 금지 - record는 이미 불변',
                code: lines[dataLine].trim()
            });
        }
    }

    // Entity에 @Setter 사용
    if (content.includes('@Entity') && content.includes('@Setter')) {
        const setterLine = lines.findIndex(l => l.includes('@Setter'));
        if (setterLine >= 0) {
            warnings.push({
                line: setterLine + 1,
                type: 'LOMBOK_MISUSE',
                message: 'Entity에 @Setter 지양 - 불변 객체 권장',
                code: lines[setterLine].trim()
            });
        }
    }
}

// ============================================
// 검사 실행
// ============================================

checkDebugCode();
checkEntityExposure();
checkTransactionScope();
checkHardcodedSecrets();
checkRedisAtomicity();
checkNPlusOne();
checkLombokMisuse();

// ============================================
// 결과 출력
// ============================================

if (errors.length > 0 || warnings.length > 0) {
    console.log('\n' + '='.repeat(60));
    console.log(`📋 Code Check: ${fileName}`);
    console.log('='.repeat(60));

    if (errors.length > 0) {
        console.log('\n❌ ERRORS (수정 필요):');
        errors.forEach(err => {
            console.log(`\n  [Line ${err.line}] ${err.type}`);
            console.log(`  └─ ${err.message}`);
            console.log(`     ${err.code}`);
        });
    }

    if (warnings.length > 0) {
        console.log('\n⚠️  WARNINGS (검토 권장):');
        warnings.forEach(warn => {
            console.log(`\n  [Line ${warn.line}] ${warn.type}`);
            console.log(`  └─ ${warn.message}`);
            console.log(`     ${warn.code}`);
        });
    }

    console.log('\n' + '='.repeat(60));
    console.log(`총 ${errors.length}개 에러, ${warnings.length}개 경고`);
    console.log('='.repeat(60) + '\n');
}

// 에러가 있어도 종료 코드는 0 (Hook이 실패하지 않도록)
process.exit(0);
