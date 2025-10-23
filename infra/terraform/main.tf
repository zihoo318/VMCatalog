##############################################
# main.tf
# - Terraform이 어떤 프로바이더(= OpenStack)를 쓰는지 선언
# - 실제로 만들 리소스(= VM 한 대)를 정의
# - provider "openstack" 블록은 보통 비워두고 OS_* 환경변수로 인증함
##############################################

terraform { # 필요한 프로바이더 선언/버전 고정 등 전역 설정
  required_providers {
    # OpenStack용 공식 프로바이더
    openstack = {
      source  = "terraform-provider-openstack/openstack"
      version = ">= 1.55.0"
    }
  }
}

# OpenStack 프로바이더 설정: OS_* 환경변수(Keystone V3)로 대체
# OS_AUTH_URL, OS_USERNAME, OS_PASSWORD, OS_PROJECT_NAME, OS_USER_DOMAIN_NAME, OS_PROJECT_DOMAIN_NAME, OS_REGION_NAME
provider "openstack" {}

# 실제로 만들 리소스(여기선 VM)
# openstack_compute_instance_v2 타입(= Nova 인스턴스), 로컬명 "web"
resource "openstack_compute_instance_v2" "web" {
  # 콘솔/포털(오픈스택 콘솔과 API)에서 보이는 인스턴스 이름
  name = "web-01"

  # 이미지/플레이버/키는 variables.tf에서 받아옴 (아래 var.*)
  image_id     = var.image_id   # 부팅에 쓸 Glance 이미지의 UUID
  flavor_id    = var.flavor_id  # 스펙을 정하는 Flavor의 ID
  key_pair     = var.key_name   # 주입할 Nova 키페어 이름

  # Config-Drive(ISO)를 붙여서 cloud-init이 메타데이터/user_data (=템플릿)를 확실히 읽도록 보장
  config_drive = true

  # 붙일 Neutron 네트워크의 UUID
  network {
    uuid = var.network_id
  }

  # cloud-init 스크립트 본문: 파일 내용 그대로 전달(variables.tf에서 string으로 받음)
  user_data = var.user_data
}

