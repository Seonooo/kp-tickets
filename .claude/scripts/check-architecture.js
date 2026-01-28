#!/usr/bin/env node

/**
 * 헥사고날 아키텍처 검증 스크립트
 *
 * 검사 항목:
 * 1. 도메인 레이어에서 인프라 의존성
 * 2. 어플리케이션 레이어에서 어댑터 의존성
 * 3. 인바운드 어댑터에서 아웃바운드 어댑터 의존성
 */

const fs = require('fs');
const path = require('path');

const filePath = process.argv[2];

// Java 파일이 아니면 스킵
if (!filePath || !filePath.endsWith('.java')) {
    process.exit(0);
}

if (!fs.existsSync(filePath)) {
    process.exit(0);
}

const content = fs.readFileSync(filePath, 'utf-8');
const lines = content.split('\n');
const violations = [];

// 패키지 경로 추출
const packageMatch = content.match(/package\s+([\w.]+);/);
if (!packageMatch) {
    process.exit(0);
}

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
        { pattern: /import\s+.*\.adapter\.out\.(?!.*Port)/, message: 'Application → Outbound Adapter 직접 의존 금지 (Port 통해서만)' },
    ],
    'ADAPTER_IN': [
        { pattern: /import\s+.*\.adapter\.out\./, message: 'Inbound Adapter → Outbound Adapter 의존 금지' },
    ],
};

// ============================================
// 검사 실행
// ============================================

if (forbiddenImports[currentLayer]) {
    lines.forEach((line, index) => {
        if (!line.trim().startsWith('import')) return;

        forbiddenImports[currentLayer].forEach(({ pattern, message }) => {
            if (pattern.test(line)) {
                violations.push({
                    line: index + 1,
                    message: message,
                    code: line.trim()
                });
            }
        });
    });
}

// ============================================
// Domain에서 JPA 어노테이션 검사
// ============================================

if (currentLayer === 'DOMAIN') {
    const jpaAnnotations = ['@Entity', '@Table', '@Column', '@Id', '@GeneratedValue',
                           '@ManyToOne', '@OneToMany', '@ManyToMany', '@OneToOne'];

    lines.forEach((line, index) => {
        jpaAnnotations.forEach(annotation => {
            if (line.includes(annotation)) {
                violations.push({
                    line: index + 1,
                    message: `Domain에서 ${annotation} 사용 금지 - 순수 POJO 유지`,
                    code: line.trim()
                });
            }
        });
    });
}

// ============================================
// 결과 출력
// ============================================

if (violations.length > 0) {
    const fileName = path.basename(filePath);

    console.log('\n' + '='.repeat(60));
    console.log(`🏗️  Architecture Check: ${fileName}`);
    console.log(`📦 Package: ${packageName}`);
    console.log(`📍 Layer: ${currentLayer}`);
    console.log('='.repeat(60));

    console.log('\n❌ ARCHITECTURE VIOLATIONS:');
    violations.forEach(v => {
        console.log(`\n  [Line ${v.line}]`);
        console.log(`  └─ ${v.message}`);
        console.log(`     ${v.code}`);
    });

    console.log('\n' + '-'.repeat(60));
    console.log('📚 Reference: /docs/architecture.md');
    console.log('='.repeat(60) + '\n');
}

process.exit(0);
