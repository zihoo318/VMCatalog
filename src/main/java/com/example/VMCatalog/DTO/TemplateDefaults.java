package com.example.VMCatalog.DTO;

public record TemplateDefaults(
        String networkId,
        String imageId,
        String flavorId,
        String keyName,
        String tplPath,
        String cloudInitFileName
) {}