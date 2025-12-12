package com.example.VMCatalog;

import com.example.VMCatalog.DTO.OrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {
    private final TerraformService tf;

    @PostMapping("/web")
    public ResponseEntity<?> create(@RequestBody OrderRequest dto) throws Exception {
        return ResponseEntity.ok(tf.applyByTemplate(dto));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<?> delete(@PathVariable String orderId) throws Exception {
        tf.destroy(orderId);
        return ResponseEntity.noContent().build();
    }

}