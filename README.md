# VMCatalog
오프라인 환경에서도 즉시 VM을 생성·부팅하고, 첫 부팅 시 cloud-init으로 내부 설정을 자동 적용하는 셀프서비스 카탈로그 웹앱입니다. OpenStack과 Terraform을 사용하며, 역할별 템플릿(Web/DB)을 제공합니다.

## 핵심 개념
- 카탈로그 항목(템플릿): Web, DB 두 종류의 역할 정의. 각 템플릿은 기본 리소스 스펙과 cloud-init 템플릿 경로를 갖습니다.
- 주문(Order): 사용자가 템플릿을 선택하고 hostname(+ username, password)만 입력하면 VM 1대를 온디맨드로 생성합니다.

## 동작 흐름
1) 프론트: 템플릿(Web/DB) 선택, hostname, username, password 제출
2) 작업 디렉터리 `${APP_WORKDIR}/{orderId}` 생성 후 `infra/terraform` 통째로 복사
3) 템플릿별 기본값(네트워크/이미지/플레이버/키, cloud-init 템플릿 경로) 로드
4) cloud-init 템플릿 렌더링 → `cloudinit/{web|db}.yaml` 저장, `terraform.tfvars` 생성
5) Terraform 실행: VM 생성, 상태 BUILD → ACTIVE
6) `terraform output -json` 결과를 `OrderResult`로 응답
7) `DELETE /api/orders/{orderId}` → 해당 작업 디렉터리에서 `terraform destroy -auto-approve`

## 필요 환경
- Java 17, Gradle (wrapper 사용 가능)
- Terraform CLI (PATH에 등록)
- OpenStack 자격 정보: OS_AUTH_URL, OS_USERNAME, OS_PASSWORD, OS_PROJECT_NAME, OS_USER_DOMAIN_NAME, OS_PROJECT_DOMAIN_NAME, OS_REGION_NAME, OS_INTERFACE
- 작업/모듈 경로: `APP_WORKDIR`(주문별 TF 작업 디렉터리 루트), `APP_TF_MODULE_DIR`(기본값: `infra/terraform`)

## 프로젝트 구조 (요약)
- src/main/java/com/example/VMCatalog : Spring Boot 앱, Terraform/Cloud-init 서비스, DTO, 컨트롤러
- src/main/resources/static/index.html : 카탈로그 & 빌더 단일 페이지
- infra/terraform : OpenStack VM 프로비저닝 모듈 및 cloud-init 템플릿

## 실행 방법(요약)
- .env.sample을 참고해서 .env.secret 파일 작성
- 백엔드 실행
- 웹에서 API 호출로 주문 생성 → VM 상태/콘솔/로그 확인
  - cloud-init 로그: /var/log/cloud-init.log, /var/log/cloud-init-output.log
