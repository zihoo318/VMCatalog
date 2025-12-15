package com.example.VMCatalog.DTO;

public record OrderRequest(
        TemplateType template,
        String name
) {}