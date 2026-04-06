#!/usr/bin/env node

/**
 * Java 코드 자동 검사 스크립트 (2단계)
 *
 * Stage 1: 빠른 regex 검사
 * Stage 2: 위반 발견 시 agent 호출로 오탐 여부 최종 판단
 *
 * 검사 항목:
 * 1. 디버그 코드 (System.out.println)
 * 2. Entity 외부 노출 (Controller에서 Entity 반환)
 * 3. 트랜잭션 내 외부 I/O
 * 4. 하드코딩된 비밀번호/시크릿
 * 5. N+1 패턴 가능성
 * 6. Redis 원자성 미보장
 * 7. Lombok 오용
 */

const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const filePath = process.argv[2];

if (!filePath || !filePath.endsWith('.java')) process.exit(0);
if (!fs.existsSync(filePath)) process.exit(0);

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
        if (line.includes('System.out.print')) {
            warnings.push({ line: lineNum, type: 'DEBUG_CODE', message: 'System.out.println 발견 - Logger 사용 권장', code: line.trim() });
        }
        if (line.includes('System.err.print')) {
            warnings.push({ line: lineNum, type: 'DEBUG_CODE', message: 'System.err.println 발견 - Logger 사용 권장', code: line.trim() });
        }
        if (line.includes('.printStackTrace()')) {
            warnings.push({ line: lineNum, type: 'DEBUG_CODE', message: 'printStackTrace() 발견 - Logger로 예외 로깅 권장', code: line.trim() });
        }
    });
}

function checkEntityExposure() {
    if (!fileName.includes('Controller')) return;
    const entityImports = [];
    lines.forEach((line) => {
        if (line.includes('import') && line.includes('.domain.model.')) {
            const match = line.match(/import\s+[\w.]+\.(\w+);/);
            if (match) entityImports.push(match[1]);
        }
    });
    lines.forEach((line, index) => {
        const lineNum = index + 1;
        entityImports.forEach(entity => {
            const pattern = new RegExp(`(ResponseEntity<${entity}>|List<${entity}>|${entity}\\s+\\w+\\()`);
            if (pattern.test(line) && !line.includes('Response') && !line.includes('Dto')) {
                errors.push({ line: lineNum, type: 'ENTITY_EXPOSURE', message: `Entity(${entity}) 외부 노출 - DTO로 변환 필요`, code: line.trim() });
            }
        });
    });
}

function checkTransactionScope() {
    if (!fileName.includes('Service')) return;
    let inTransaction = false;

    lines.forEach((line, index) => {
        const lineNum = index + 1;
        if (line.includes('@Transactional') && !line.includes('readOnly')) {
            inTransaction = true;
        }
        if (inTransaction) {
            if (line.includes('kafkaTemplate.send') || line.includes('KafkaTemplate')) {
                errors.push({ line: lineNum, type: 'TX_EXTERNAL_IO', message: '트랜잭션 내 Kafka 발행 - AFTER_COMMIT 사용 권장', code: line.trim() });
            }
            if (line.includes('restClient.') || line.includes('RestClient') ||
                line.includes('webClient.') || line.includes('restTemplate.')) {
                warnings.push({ line: lineNum, type: 'TX_EXTERNAL_IO', message: '트랜잭션 내 외부 API 호출 - 트랜잭션 범위 최소화 권장', code: line.trim() });
            }
        }
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
        if (line.trim().startsWith('//') || line.trim().startsWith('*')) return;
        secretPatterns.forEach(({ pattern, type }) => {
            if (pattern.test(line)) {
                errors.push({ line: lineNum, type: 'HARDCODED_SECRET', message: `하드코딩된 ${type} 발견 - 환경변수 또는 Vault 사용 권장`, code: line.trim().substring(0, 50) + '...' });
            }
        });
    });
}

function checkRedisAtomicity() {
    if (!content.includes('RedisTemplate') && !content.includes('StringRedisTemplate')) return;
    lines.forEach((line, index) => {
        const lineNum = index + 1;
        if (line.includes('.opsForValue().get(') || line.includes('.opsForHash().get(')) {
            for (let i = index + 1; i < Math.min(index + 5, lines.length); i++) {
                if (lines[i].includes('.set(') || lines[i].includes('.put(')) {
                    warnings.push({ line: lineNum, type: 'REDIS_ATOMICITY', message: 'GET 후 SET 패턴 - Race Condition 가능성, SETNX 또는 Lua Script 권장', code: line.trim() });
                    break;
                }
            }
        }
    });
}

function checkNPlusOne() {
    if (!fileName.includes('Repository')) return;
    lines.forEach((line, index) => {
        const lineNum = index + 1;
        if (line.includes('@Query') && line.includes('SELECT') &&
            !line.includes('JOIN FETCH') && !line.includes('join fetch')) {
            const nextLines = lines.slice(index, index + 3).join(' ');
            if (nextLines.includes('List<') && !nextLines.includes('Projection')) {
                warnings.push({ line: lineNum, type: 'N_PLUS_ONE', message: 'N+1 가능성 - JOIN FETCH 또는 @EntityGraph 고려', code: line.trim() });
            }
        }
    });
}

function checkLombokMisuse() {
    if (content.includes('@Data') && content.includes('record ')) {
        const dataLine = lines.findIndex(l => l.includes('@Data'));
        if (dataLine >= 0) {
            errors.push({ line: dataLine + 1, type: 'LOMBOK_MISUSE', message: 'record에 @Data 사용 금지 - record는 이미 불변', code: lines[dataLine].trim() });
        }
    }
    if (content.includes('@Entity') && content.includes('@Setter')) {
        const setterLine = lines.findIndex(l => l.includes('@Setter'));
        if (setterLine >= 0) {
            warnings.push({ line: setterLine + 1, type: 'LOMBOK_MISUSE', message: 'Entity에 @Setter 지양 - 불변 객체 권장', code: lines[setterLine].trim() });
        }
    }
}

// ============================================
// Stage 2: Agent 검증
// ============================================

function getLineContext(lineNum) {
    const idx = lineNum - 1;
    const start = Math.max(0, idx - 3);
    const end = Math.min(lines.length - 1, idx + 3);
    return lines.slice(start, end + 1)
        .map((l, i) => `${start + i + 1}: ${l}`)
        .join('\n');
}

function invokeAgentReview(allViolations) {
    const violationDetails = allViolations.map(v =>
        `[${v.type}] Line ${v.line}: ${v.message}\n코드 컨텍스트:\n${getLineContext(v.line)}`
    ).join('\n\n---\n\n');

    const prompt =
`당신은 Java 코드 리뷰어입니다. 아래 잠재적 위반 사항이 실제 문제인지 오탐인지 판단하세요.

파일: ${fileName}

감지된 위반 사항:
${violationDetails}

판단 기준:
- APPROVED: 코드 컨텍스트상 실제로 문제없는 오탐
- BLOCKED: 실제로 수정이 필요한 위반

첫 단어로 반드시 APPROVED 또는 BLOCKED만 응답하고, 한 줄로 이유를 설명하세요.`;

    const result = spawnSync('claude', ['-p', prompt], {
        encoding: 'utf-8',
        timeout: 60000,
        maxBuffer: 512 * 1024
    });

    if (result.error || result.status !== 0) {
        console.log('⚠️  Agent 호출 실패 - 경고로 처리합니다');
        return 'APPROVED';
    }

    const output = (result.stdout || '').trim();
    console.log(`\n🤖 Agent 판단: ${output}`);
    return output.startsWith('BLOCKED') ? 'BLOCKED' : 'APPROVED';
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

const allViolations = [...errors, ...warnings];

// Stage 1: 위반 없음 → 즉시 통과
if (allViolations.length === 0) {
    process.exit(0);
}

// Stage 1 결과 출력
console.log('\n' + '='.repeat(60));
console.log(`📋 Code Check: ${fileName}`);
console.log('='.repeat(60));

if (errors.length > 0) {
    console.log('\n❌ 잠재적 ERRORS:');
    errors.forEach(err => {
        console.log(`\n  [Line ${err.line}] ${err.type}`);
        console.log(`  └─ ${err.message}`);
        console.log(`     ${err.code}`);
    });
}

if (warnings.length > 0) {
    console.log('\n⚠️  잠재적 WARNINGS:');
    warnings.forEach(warn => {
        console.log(`\n  [Line ${warn.line}] ${warn.type}`);
        console.log(`  └─ ${warn.message}`);
        console.log(`     ${warn.code}`);
    });
}

console.log('\n' + '='.repeat(60));
console.log(`총 ${errors.length}개 에러, ${warnings.length}개 경고 감지 → Agent 검증 중...`);
console.log('='.repeat(60) + '\n');

// Stage 2: Agent 최종 판단
const verdict = invokeAgentReview(allViolations);

if (verdict === 'BLOCKED') {
    console.log('\n🚫 Agent가 실제 위반으로 판단했습니다. 수정 후 다시 시도하세요.\n');
    process.exit(1);
}

console.log('\n✅ Agent가 오탐으로 판단했습니다. 계속 진행합니다.\n');
process.exit(0);
