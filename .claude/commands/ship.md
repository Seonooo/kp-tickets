# /ship

작업을 마무리하고 PR을 자동으로 생성합니다.
코드 리뷰와 보안 검토를 수행한 뒤 요약 보고서를 전달합니다.

---

## Usage

```
/ship                    # 현재 브랜치 기준 전체 자동 처리
/ship --draft            # Draft PR로 생성
/ship --skip-review      # 리뷰 생략, PR만 생성
```

---

## Process

### Step 1: 사전 확인

```bash
git status                    # 미커밋 변경사항 확인
git log main..HEAD --oneline  # 이번 작업 커밋 확인
```

미커밋 변경사항이 있으면 커밋 후 진행합니다.

### Step 2: doc-updater 실행

`.claude/agents/doc-updater.md`를 참조하여 관련 문서를 업데이트합니다.

### Step 3: pr-reporter 실행

`.claude/agents/pr-reporter.md`를 참조하여:
1. PR 자동 생성
2. 코드 리뷰 (code-reviewer agent)
3. 보안 검토 (security-reviewer agent)
4. 요약 보고서 출력

---

## Output

사용자에게는 요약 보고서만 전달됩니다.
상세 리뷰 내용은 PR 코멘트에서 확인할 수 있습니다.
