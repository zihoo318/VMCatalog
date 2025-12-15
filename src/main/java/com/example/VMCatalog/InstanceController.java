package com.example.VMCatalog;

import com.example.VMCatalog.DTO.InstanceDto;
import com.example.VMCatalog.Openstack.OpenStackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/instances")
public class InstanceController {
    private final OpenStackService os; // VM 목록 조회용 서비스

    // 모든 VM 목록 조회
    @GetMapping
    public ResponseEntity<List<InstanceDto>> listInstances() {
        return ResponseEntity.ok(os.listInstances());
    }
}