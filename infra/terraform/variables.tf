##############################################
# variables.tf
# - main.tf에서 참조하는 입력값(변수)(리소스)을 정의
# - Terraform 모듈이 외부에서 받아야 하는 입력값의 스키마
# - 타입/설명/기본값(필요시)을 지정
#
# 아래와 같은 내용들로 변수를 채움 (지금은 4개 모두(파일 읽는 user_data 제외) tarraform.tfvars에 있음)
# 1. CLI 인자: -var, -var-file
# 2. 환경변수: TF_VAR_<변수명>
# 3. *.auto.tfvars / terraform-web.tfvars 파일(자동 로드)
# 4. 변수 기본값: variables.tf 안에서 default = ...를 지정
##############################################

# 사용할 VM의 이름
variable "name" {
  type        = string
  description = "name (웹에서 사용자가 입력한 VM의 이름)"
}

# 사용할 이미지 ID
variable "image_id" {
  type        = string
  description = "Glance image UUID (cloud-init 포함 이미지 권장)"
}

# 사용할 플레이버 ID
variable "flavor_id" {
  type        = string
  description = "Flavor ID to use (e.g., 1 for m1.tiny)"
}

# 붙일 네트워크 ID
variable "network_id" {
  type        = string
  description = "UUID of the Neutron network (e.g., private-net)"
}

# cloud-init user_data (문자열)
# terraform.tfvars에서 file("./cloudinit-templates/web.yaml")을 읽어 주입
variable "user_data_path" {
  type = string
}
