package com.example.VMCatalog;

import com.example.VMCatalog.DTO.OrderResult;
import com.example.VMCatalog.DTO.OrderRequest;
import com.example.VMCatalog.DTO.TemplateType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TerraformService {

    // 공통 경로
    @Value("${app.workDir}")     private String workRoot;    // 주문별 작업폴더 루트
    @Value("${app.tfModuleDir}") private String tfModuleDir; // 리포 내 Terraform 모듈 경로

    // 공통 기본 스펙(역할 공용)
    @Value("${app.defaults.networkId}") private String defaultNetworkId;
    @Value("${app.defaults.imageId}")   private String defaultImageId;
    @Value("${app.defaults.flavorId}")  private String defaultFlavorId;

    // 역할별 cloud-init 템플릿 경로(미설정 시 classpath 기본값 사용)
    @Value("${app.templates.web.tpl:classpath:/cloudinit-templates/web.yaml}")
    private String webTplPath;
    @Value("${app.templates.db.tpl:classpath:/cloudinit-templates/db.yaml}")
    private String dbTplPath;

    private final CloudInitRenderer renderer;      // 기존 사용
    private final ResourceLoader resourceLoader;   // 리소스 로딩

    /**
     * name == hostname 정책. 프론트에서 template, name만 받는다.
     * 공통 스펙(app.defaults.*)을 사용하고, 역할에 따라 cloud-init 템플릿만 바꿔서 생성.
     */
    public OrderResult applyByTemplate(OrderRequest req) throws Exception {
        if (req == null || req.template() == null) {
            throw new IllegalArgumentException("template는 필수입니다.");
        }
        if (req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("name은 필수입니다.");
        }

        // name == hostname
        final String name = sanitize(req.name());
        final String hostname = name;

        // 주문ID 생성(작업폴더 키)
        final String orderId = "ord-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .format(java.time.LocalDateTime.now())
                + "-" + java.util.UUID.randomUUID().toString().substring(0, 6);

        // 작업 폴더 준비 + 모듈 복사
        final Path orderDir     = Path.of(workRoot, orderId);
        final Path cloudinitDir = orderDir.resolve("cloudinit");
        Files.createDirectories(cloudinitDir);
        copyTree(Path.of(tfModuleDir), orderDir);

        // 역할별 cloud-init 템플릿 선택
        final boolean isWeb = (req.template() == TemplateType.WEB);
        final String tplPath = isWeb ? webTplPath : dbTplPath;
        final String cloudinitFileName = isWeb ? "web.yaml" : "db.yaml";

        // cloud-init 렌더링(name == hostname)
        Resource tplRes = resourceLoader.getResource(tplPath);
        String tpl = new String(tplRes.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String renderedYaml = renderer.render(tpl, hostname);
        Files.writeString(cloudinitDir.resolve(cloudinitFileName), renderedYaml);

        // terraform.tfvars 작성(공통 스펙 사용)
        String tfvars = """
                name       = "%s"
                network_id = "%s"
                image_id   = "%s"
                flavor_id  = "%s"
                user_data  = file("./cloudinit/%s")
                """.formatted(
                name, defaultNetworkId, defaultImageId, defaultFlavorId, cloudinitFileName
        );
        Files.writeString(orderDir.resolve("terraform.tfvars"), tfvars);

        // Terraform 실행
        runTf(orderDir, "terraform", "init");
        runTf(orderDir, "terraform", "apply", "-auto-approve");

        // 출력 파싱(공용 키 우선)
        String outJson = runTf(orderDir, "terraform", "output", "-json");
        Map<String,Object> outputs = new ObjectMapper().readValue(outJson, Map.class);

        String serverId   = getOutputValue(outputs, "server_id", "web_server_id", "db_server_id");
        String serverName = getOutputValue(outputs, "server_name", "web_server_name", "db_server_name");
        Object ipInfo     = getOutputObject(outputs, "ip_info", "web_ip_info", "db_ip_info");

        return new OrderResult(
                orderId,        // 생성된 주문ID(작업폴더 키)
                true,           // created
                serverId,       // Nova 인스턴스 ID
                serverName,     // Nova 인스턴스 이름
                ipInfo          // IP 정보(모듈 output 그대로: List/Map 등)
        );
    }

    public void destroy(String orderId) throws Exception {
        Path orderDir = Path.of(workRoot, orderId);
        runTf(orderDir, "terraform", "destroy", "-auto-approve");
    }

    // ==== 유틸 ====
    private static String sanitize(String s) {
        // 이름을 안전하게 쓰기 위해 정리(normalize)
        String t = s.trim().toLowerCase().replaceAll("[^a-z0-9-]", "-");
        t = t.replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        return t.isEmpty() ? "vm" : t;
    }

    private static void copyTree(Path src, Path dst) throws IOException {
        Files.walk(src).forEach(p -> {
            try {
                Path to = dst.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(to);
                else Files.copy(p, to, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) { throw new UncheckedIOException(e); }
        });
    }

    private static String runTf(Path dir, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder log = new StringBuilder();
        try (var br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line; while ((line = br.readLine()) != null) log.append(line).append(System.lineSeparator());
        }
        int exit = p.waitFor();
        if (exit != 0) throw new IllegalStateException("Terraform failed: " + String.join(" ", cmd) + "\n" + log);
        return log.toString();
    }

    // Terraform output 파싱 보조
    @SuppressWarnings("unchecked")
    private static String getOutputValue(Map<String, Object> outputs, String... keys) {
        for (String k : keys) {
            Object node = outputs.get(k);
            if (node instanceof Map<?, ?> map && map.containsKey("value")) {
                Object v = map.get("value");
                if (v != null) return String.valueOf(v);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Object getOutputObject(Map<String, Object> outputs, String... keys) {
        for (String k : keys) {
            Object node = outputs.get(k);
            if (node instanceof Map<?, ?> map && map.containsKey("value")) {
                return map.get("value");
            }
        }
        return null;
    }
}
