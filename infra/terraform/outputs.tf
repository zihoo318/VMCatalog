# Terraform 실행 결과 중, 바깥에 보여줄 값들을 선언하는 파일
# 무엇을 노출할지 정의해두면 terraform apply 후에 terraform output 명령으로 그 값을 꺼내 쓸 수 있음


# 실행 후 출력값 정의: 생성된 VM의 네트워크 정보 보기 출력
output "web_ip_info" {
  value = openstack_compute_instance_v2.web.network
  description = "VM이 받은 프라이빗 IP 목록"
}

output "web_server_id" {
  value       = openstack_compute_instance_v2.web.id
  description = "Nova server ID"
}

output "web_server_name" {
  value       = openstack_compute_instance_v2.web.name
}
