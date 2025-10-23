package com.example.VMCatalog.DTO;

public record OrderRequest(
        String orderId, String name, String hostname,/* String sshPublicKey,*/
        String networkId, String imageId, String flavorId, String keyName) {}