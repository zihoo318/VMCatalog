package com.example.VMCatalog;

import com.example.VMCatalog.DTO.OrderRequest;
import com.example.VMCatalog.DTO.OrderResult;
import com.example.VMCatalog.Openstack.OpenStackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {
    private final TerraformService tf;

    @PostMapping
    public ResponseEntity<OrderResult> create(@RequestBody OrderRequest dto) throws Exception {
        OrderResult ok = tf.applyByTemplate(dto);
        return ResponseEntity.ok(ok);
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> delete(@PathVariable String orderId) throws Exception {
        tf.destroy(orderId);
        return ResponseEntity.noContent().build();
    }
}