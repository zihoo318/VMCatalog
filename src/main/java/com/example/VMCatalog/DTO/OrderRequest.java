package com.example.VMCatalog.DTO;

public record OrderRequest(
        String orderId,
        TemplateType template,
        String hostname,
        String name ) {}