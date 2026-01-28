#!/usr/bin/env node

/**
 * 커밋 메시지 형식 검사 스크립트
 *
 * Conventional Commits 형식:
 * type(scope): description
 *
 * 허용되는 type:
 * - feat: 새로운 기능
 * - fix: 버그 수정
 * - docs: 문서 변경
 * - style: 코드 스타일 변경 (포맷팅 등)
 * - refactor: 리팩토링
 * - test: 테스트 추가/수정
 * - chore: 빌드, 설정 변경
 * - perf: 성능 개선
 */

const { execSync } = require('child_process');

// 마지막 커밋 메시지 가져오기
let commitMessage;
try {
    commitMessage = execSync('git log -1 --pretty=%B', { encoding: 'utf-8' }).trim();
} catch (e) {
    // git이 없거나 커밋이 없는 경우
    process.exit(0);
}

const validTypes = ['feat', 'fix', 'docs', 'style', 'refactor', 'test', 'chore', 'perf'];
const pattern = new RegExp(`^(${validTypes.join('|')})(\\([\\w-]+\\))?:\\s.+`);

// Merge 커밋은 스킵
if (commitMessage.startsWith('Merge ')) {
    process.exit(0);
}

// Co-Authored-By만 있는 라인은 제외
const firstLine = commitMessage.split('\n')[0];

if (!pattern.test(firstLine)) {
    console.log('\n' + '='.repeat(60));
    console.log('📝 Commit Message Check');
    console.log('='.repeat(60));
    console.log('\n⚠️  WARNING: 커밋 메시지 형식이 올바르지 않습니다.');
    console.log(`\n  현재: "${firstLine}"`);
    console.log('\n  📋 올바른 형식:');
    console.log('     type(scope): description');
    console.log('\n  📌 허용되는 type:');
    validTypes.forEach(type => {
        const descriptions = {
            'feat': '새로운 기능',
            'fix': '버그 수정',
            'docs': '문서 변경',
            'style': '코드 스타일',
            'refactor': '리팩토링',
            'test': '테스트',
            'chore': '빌드/설정',
            'perf': '성능 개선'
        };
        console.log(`     - ${type}: ${descriptions[type]}`);
    });
    console.log('\n  📝 예시:');
    console.log('     feat(queue): 대기열 진입 API 추가');
    console.log('     fix(booking): 좌석 중복 예약 버그 수정');
    console.log('     docs: README 업데이트');
    console.log('='.repeat(60) + '\n');
}

process.exit(0);
