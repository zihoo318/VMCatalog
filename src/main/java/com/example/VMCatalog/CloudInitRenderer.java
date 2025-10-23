package com.example.VMCatalog;

import org.springframework.stereotype.Component;

// cloudinit 문자열에 끼워 넣어서 최종 사용자 데이터(user-data) 문자열을 만들어 주는 템플릿 렌더러
@Component
public class CloudInitRenderer {
    public String renderWeb(String template, String hostname) {
        return template.replace("${hostname}", hostname);
    }
}