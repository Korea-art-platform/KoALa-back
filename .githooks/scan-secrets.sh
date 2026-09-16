#!/bin/sh
#
# 시크릿 검사 — 커밋 훅과 CI 가 같이 쓰는 하나의 규칙.
#
#   sh .githooks/scan-secrets.sh --staged    스테이징된 변경만 본다 (커밋 훅)
#   sh .githooks/scan-secrets.sh --tracked   저장소에 들어 있는 파일 전체를 본다 (CI)
#
# 규칙을 여기 한 곳에만 두어, 로컬은 통과하는데 CI 만 막는 상황을 없앤다.

set -u

mode="${1:---staged}"
fail=0

RED=''; YEL=''; RST=''
if [ -t 2 ]; then RED="$(printf '\033[31m')"; YEL="$(printf '\033[33m')"; RST="$(printf '\033[0m')"; fi

note() { printf '%s\n' "$*" >&2; }

# ── 내용 규칙 ───────────────────────────────────────────────────────────────
AWS_KEY='A(KIA|SIA)[0-9A-Z]{16}'
PRIVATE_KEY='-----BEGIN[ A-Z]*PRIVATE KEY-----'
PAYMENT_KEY='(test|live)_(sk|ak)_[0-9A-Za-z]{10,}'
GOOGLE_KEY='AIza[0-9A-Za-z_-]{35}'
SLACK_TOKEN='xox[baprs]-[0-9A-Za-z-]{10,}'
SLACK_HOOK='hooks\.slack\.com/services/[0-9A-Za-z/+_-]{20,}'
JWT='eyJ[0-9A-Za-z_-]{10,}\.[0-9A-Za-z_-]{10,}\.[0-9A-Za-z_-]{10,}'

ALL_PATTERNS="${AWS_KEY}|${PRIVATE_KEY}|${PAYMENT_KEY}|${GOOGLE_KEY}|${SLACK_TOKEN}|${SLACK_HOOK}|${JWT}"

# 어느 규칙에 걸렸는지 이름을 붙인다. 걸린 파일에만 부르므로 비용이 없다.
label_hits() {
    input="$1"
    hit=""
    for pair in "${AWS_KEY}::AWS 액세스 키" \
                "${PRIVATE_KEY}::개인키" \
                "${PAYMENT_KEY}::결제 시크릿 키" \
                "${GOOGLE_KEY}::구글 API 키" \
                "${SLACK_TOKEN}::슬랙 토큰" \
                "${SLACK_HOOK}::슬랙 웹훅 URL" \
                "${JWT}::JWT 토큰"; do
        rx="${pair%%::*}"
        name="${pair##*::}"
        if printf '%s\n' "$input" | grep -Eq -e "$rx"; then
            hit="${hit}${hit:+, }${name}"
        fi
    done
    printf '%s' "$hit"
}

# ── 검사에서 제외할 경로 ────────────────────────────────────────────────────
# 템플릿(*.example)은 자리표시자만 들어 있다. 이 스크립트 자체는 패턴을 담고 있다.
is_allowlisted() {
    case "$1" in
        *.example) return 0 ;;
        .githooks/*) return 0 ;;
    esac
    return 1
}

# ── 파일 이름 자체가 금지된 것들 ────────────────────────────────────────────
check_forbidden_name() {
    base="${1##*/}"
    case "$base" in *.example) return 0 ;; esac
    case "$base" in
        .env|.env.*) return 1 ;;
        application-local.yml|application-local.yaml) return 1 ;;
        application-prod.yml|application-prod.yaml) return 1 ;;
        application.properties|application-*.properties) return 1 ;;
        keystore.properties) return 1 ;;
        firebase-service-account.json) return 1 ;;
        *.jks|*.keystore) return 1 ;;
        *.pem|*.p12|*.pfx) return 1 ;;
        id_rsa|id_dsa|id_ecdsa|id_ed25519) return 1 ;;
    esac
    return 0
}

case "$mode" in
    --staged)  files="$(git diff --cached --name-only --diff-filter=ACM)" ;;
    --tracked) files="$(git ls-files)" ;;
    *) note "쓰는 법: $0 [--staged|--tracked]"; exit 2 ;;
esac

for f in $files; do
    if ! check_forbidden_name "$f"; then
        note "${RED}[차단] 커밋하면 안 되는 파일입니다:${RST} $f"
        fail=1
    fi
done

if [ "$mode" = "--staged" ]; then
    for f in $files; do
        is_allowlisted "$f" && continue
        # 바이너리는 건너뛴다 (numstat 이 "-  -" 로 나온다)
        if git diff --cached --numstat -- "$f" | awk '$1=="-" && $2=="-" {found=1} END {exit !found}'; then
            continue
        fi
        added="$(git diff --cached -U0 -- "$f" | sed -n 's/^+//p' | grep -v '^++')"
        [ -n "$added" ] || continue
        hit="$(label_hits "$added")"
        if [ -n "$hit" ]; then
            note "${RED}[차단] 시크릿으로 보이는 값이 있습니다:${RST} $f  (${hit})"
            fail=1
        fi
    done
else
    # 파일 하나씩 돌리면 수천 번 프로세스를 띄운다. 한 번에 훑어 후보만 추린다.
    candidates="$(git ls-files -z | xargs -0 grep -I -l -E -e "$ALL_PATTERNS" -- 2>/dev/null || true)"
    for f in $candidates; do
        is_allowlisted "$f" && continue
        hit="$(label_hits "$(cat "$f")")"
        if [ -n "$hit" ]; then
            note "${RED}[차단] 시크릿으로 보이는 값이 있습니다:${RST} $f  (${hit})"
            fail=1
        fi
    done
fi

if [ "$fail" -ne 0 ]; then
    note ""
    note "${YEL}시크릿 검사에서 막혔습니다.${RST}"
    note "  1) 값을 코드에서 빼고 환경변수나 GitHub Secrets 로 옮기세요."
    note "  2) 이미 스테이징했다면:  git restore --staged <파일>"
    note "  3) 실제 시크릿이 아니라 자리표시자라면 파일 이름을 *.example 로 두세요."
    exit 1
fi

exit 0
