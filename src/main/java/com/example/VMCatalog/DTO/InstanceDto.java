package com.example.VMCatalog.DTO;

import java.util.List;

public record InstanceDto(
        String id,
        String name,
        String status,                 // BUILD, ACTIVE, ERROR 등
        String consoleUrl,             // noVNC URL (가용 시)
        String ip,
        String flavor,
        String image
) {}
