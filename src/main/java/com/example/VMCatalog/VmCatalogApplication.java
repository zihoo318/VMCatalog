package com.example.VMCatalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@SpringBootApplication
public class VmCatalogApplication {

	public static void main(String[] args) throws IOException {
        //preloadDotenv();
		SpringApplication.run(VmCatalogApplication.class, args);
	}

    // 상단 메뉴 실행(Run) -> 구성 편집(Edit Configurations…) -> 환경변수 설정 파일 지정 전에 쓰던 메서드
    private static void preloadDotenv() throws IOException {
        Path env = Paths.get(System.getProperty("user.dir"), ".env.secret");
        if (!Files.exists(env)) {
            System.out.println("[.env] not found: " + env.toAbsolutePath());
            return;
        }
        List<String> lines = Files.readAllLines(env, StandardCharsets.UTF_8);
        int put = 0;
        for (String raw : lines) {
            String s = raw.trim();
            if (s.isEmpty() || s.startsWith("#")) continue;
            int eq = s.indexOf('=');
            if (eq <= 0) continue;
            String k = s.substring(0, eq).trim();
            String v = s.substring(eq + 1).trim();
            if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                v = v.substring(1, v.length() - 1);
            }
            System.setProperty(k, v);
            put++;
        }
        System.out.println("[.env] preloaded " + put + " entries, OS_AUTH_URL=" + System.getProperty("OS_AUTH_URL"));
    }
}
