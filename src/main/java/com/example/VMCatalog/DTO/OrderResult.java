package com.example.VMCatalog.DTO;

public record OrderResult(
        String orderId,
        boolean created,
        String serverId,
        String serverName,
        Object ipInfo
) {}