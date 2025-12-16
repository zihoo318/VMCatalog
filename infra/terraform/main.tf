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

# OpenStack 프로바이더 설정: 비워두고, OS_* 환경변수로 인증
# OS_AUTH_URL, OS_USERNAME, OS_PASSWORD, OS_PROJECT_NAME, OS_USER_DOMAIN_NAME, OS_PROJECT_DOMAIN_NAME, OS_REGION_NAME
provider "openstack" {}

# 실제로 만들 리소스(VM)
# openstack_compute_instance_v2 타입(= Nova 인스턴스)
resource "openstack_compute_instance_v2" "vm" {
  name         = var.name
  image_id     = var.image_id
  flavor_id    = var.flavor_id
  config_drive = true
  network { uuid = var.network_id }
  user_data = file(var.user_data_path)
}

