package com.example.VMCatalog.DTO;

public class OrderMeta {
    public String orderId;
    public String serverId;
    public String template;
    public String name;
    public String createdAt;

    public OrderMeta() {}
    public OrderMeta(String orderId, String serverId, String template, String name, String createdAt) {
        this.orderId = orderId;
        this.serverId = serverId;
        this.template = template;
        this.name = name;
        this.createdAt = createdAt;
    }
}
