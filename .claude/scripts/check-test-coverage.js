#!/usr/bin/env node

/**
 * 테스트 존재 여부 검사 스크립트
 *
 * 검사 항목:
 * 1. Service 클래스에 대응하는 테스트 존재 여부
 * 2. Repository 클래스에 대응하는 테스트 존재 여부
 * 3. UseCase 클래스에 대응하는 테스트 존재 여부
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

const fileName = path.basename(filePath, '.java');

// 테스트가 필요한 파일 패턴
const needsTest = [
    'Service',
    'UseCase',
    'Adapter',
    'Repository'
];

// 테스트 파일 자체는 스킵
if (fileName.includes('Test') || filePath.includes('/test/')) {
    process.exit(0);
}

// 테스트가 필요한 파일인지 확인
const requiresTest = needsTest.some(pattern => fileName.includes(pattern));

if (!requiresTest) {
    process.exit(0);
}

// 테스트 파일 경로 추정
const testPaths = [
    filePath.replace('/main/', '/test/').replace('.java', 'Test.java'),
    filePath.replace('\\main\\', '\\test\\').replace('.java', 'Test.java'),
];

const testExists = testPaths.some(testPath => fs.existsSync(testPath));

if (!testExists) {
    console.log('\n' + '='.repeat(60));
    console.log(`🧪 Test Coverage Check: ${fileName}`);
    console.log('='.repeat(60));
    console.log('\n⚠️  WARNING: 테스트 파일이 없습니다!');
    console.log(`\n  📁 대상 파일: ${filePath}`);
    console.log(`  📝 예상 테스트: ${fileName}Test.java`);
    console.log('\n  💡 TDD 권장: /tdd 명령어로 테스트 먼저 작성');
    console.log('='.repeat(60) + '\n');
}

process.exit(0);
