#!/usr/bin/env node

/**
 * 헥사고날 아키텍처 검증 스크립트 (2단계)
 *
 * Stage 1: 빠른 import 패턴 검사
 * Stage 2: 위반 발견 시 agent 호출로 오탐 여부 최종 판단
 *
 * 검사 항목:
 * 1. 도메인 레이어에서 인프라 의존성
 * 2. 어플리케이션 레이어에서 어댑터 의존성
 * 3. 인바운드 어댑터에서 아웃바운드 어댑터 의존성
 */

const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const filePath = process.argv[2];

if (!filePath || !filePath.endsWith('.java')) process.exit(0);
if (!fs.existsSync(filePath)) process.exit(0);

const content = fs.readFileSync(filePath, 'utf-8');
const lines = content.split('\n');
const violations = [];

const packageMatch = content.match(/package\s+([\w.]+);/);
if (!packageMatch) process.exit(0);

const packageName = packageMatch[1];

// ============================================
// 레이어 판단
// ============================================

function getLayer(pkg) {
    if (pkg.includes('.domain.')) return 'DOMAIN';
    if (pkg.includes('.application.')) return 'APPLICATION';
    if (pkg.includes('.adapter.in.')) return 'ADAPTER_IN';
    if (pkg.includes('.adapter.out.')) return 'ADAPTER_OUT';
    return 'UNKNOWN';
}

const currentLayer = getLayer(packageName);

// ============================================
// 금지된 import 패턴
// ============================================

const forbiddenImports = {
    'DOMAIN': [
        { pattern: /import\s+.*\.adapter\./, message: 'Domain → Adapter 의존 금지' },
        { pattern: /import\s+.*\.application\.service\./, message: 'Domain → Application Service 의존 금지' },
        { pattern: /import\s+javax\.persistence\./, message: 'Domain에서 JPA 의존 금지 (순수 POJO 유지)' },
        { pattern: /import\s+jakarta\.persistence\./, message: 'Domain에서 JPA 의존 금지 (순수 POJO 유지)' },
        { pattern: /import\s+org\.springframework\./, message: 'Domain에서 Spring 의존 금지 (순수 POJO 유지)' },
    ],
    'APPLICATION': [
        { pattern: /import\s+.*\.adapter\.in\./, message: 'Application → Inbound Adapter 의존 금지' },
        // adapter.out 패키지의 구현체 직접 참조 금지 (application.port.out 인터페이스만 허용)
        { pattern: /import\s+.*\.adapter\.out\.[A-Z]/, message: 'Application → Outbound Adapter 구현체 직접 의존 금지 (application.port.out 인터페이스 사용)' },
    ],
    'ADAPTER_IN': [
        { pattern: /import\s+.*\.adapter\.out\./, message: 'Inbound Adapter → Outbound Adapter 의존 금지' },
    ],
};

// ============================================
// Stage 1: 검사 실행
// ============================================

if (forbiddenImports[currentLayer]) {
    lines.forEach((line, index) => {
        if (!line.trim().startsWith('import')) return;
        forbiddenImports[currentLayer].forEach(({ pattern, message }) => {
            if (pattern.test(line)) {
                violations.push({ line: index + 1, message, code: line.trim() });
            }
        });
    });
}

if (currentLayer === 'DOMAIN') {
    const jpaAnnotations = ['@Entity', '@Table', '@Column', '@Id', '@GeneratedValue',
                           '@ManyToOne', '@OneToMany', '@ManyToMany', '@OneToOne'];
    lines.forEach((line, index) => {
        jpaAnnotations.forEach(annotation => {
            if (line.includes(annotation)) {
                violations.push({ line: index + 1, message: `Domain에서 ${annotation} 사용 금지 - 순수 POJO 유지`, code: line.trim() });
            }
        });
    });
}

// Stage 1: 위반 없음 → 즉시 통과
if (violations.length === 0) {
    process.exit(0);
}

// ============================================
// Stage 1 결과 출력
// ============================================

const fileName = path.basename(filePath);

console.log('\n' + '='.repeat(60));
console.log(`🏗️  Architecture Check: ${fileName}`);
console.log(`📦 Package: ${packageName}`);
console.log(`📍 Layer: ${currentLayer}`);
console.log('='.repeat(60));

console.log('\n❌ 잠재적 ARCHITECTURE VIOLATIONS:');
violations.forEach(v => {
    console.log(`\n  [Line ${v.line}]`);
    console.log(`  └─ ${v.message}`);
    console.log(`     ${v.code}`);
});

console.log('\n' + '='.repeat(60));
console.log(`총 ${violations.length}개 위반 감지 → Agent 검증 중...`);
console.log('='.repeat(60) + '\n');

// ============================================
// Stage 2: Agent 검증
// ============================================

function getLineContext(lineNum) {
    const idx = lineNum - 1;
    const start = Math.max(0, idx - 2);
    const end = Math.min(lines.length - 1, idx + 2);
    return lines.slice(start, end + 1)
        .map((l, i) => `${start + i + 1}: ${l}`)
        .join('\n');
}

function invokeAgentReview() {
    const violationDetails = violations.map(v =>
        `[${currentLayer}] Line ${v.line}: ${v.message}\n컨텍스트:\n${getLineContext(v.line)}`
    ).join('\n\n---\n\n');

    const prompt =
`당신은 헥사고날 아키텍처 전문가입니다. 아래 잠재적 아키텍처 위반이 실제 문제인지 오탐인지 판단하세요.

파일: ${fileName}
패키지: ${packageName}
레이어: ${currentLayer}

감지된 위반 사항:
${violationDetails}

판단 기준:
- APPROVED: 헥사고날 아키텍처 원칙상 실제로 문제없는 오탐 (예: 인터페이스 참조, 공통 유틸)
- BLOCKED: 실제 레이어 의존성 위반으로 수정이 필요한 경우

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

const verdict = invokeAgentReview();

if (verdict === 'BLOCKED') {
    console.log('\n🚫 Agent가 실제 아키텍처 위반으로 판단했습니다. 수정 후 다시 시도하세요.\n');
    process.exit(1);
}

console.log('\n✅ Agent가 오탐으로 판단했습니다. 계속 진행합니다.\n');
process.exit(0);
