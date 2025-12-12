package com.example.VMCatalog;

import com.example.VMCatalog.DTO.OrderResult;
import com.example.VMCatalog.DTO.OrderRequest;
import com.example.VMCatalog.DTO.TemplateType;
import com.example.VMCatalog.DTO.TemplateDefaults;
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

    // === 템플릿별 기본값 (application.yml에서 주입) ===
    // WEB
    @Value("${app.templates.web.networkId}") private String webNetworkId;
    @Value("${app.templates.web.imageId}")   private String webImageId;
    @Value("${app.templates.web.flavorId}")  private String webFlavorId;
    @Value("${app.templates.web.keyName}")   private String webKeyName;
    @Value("${app.templates.web.tpl:classpath:/infra/terraform/cloudinit-templates/web.yaml.tpl}")
    private String webTplPath;

    // DB
    @Value("${app.templates.db.networkId}") private String dbNetworkId;
    @Value("${app.templates.db.imageId}")   private String dbImageId;
    @Value("${app.templates.db.flavorId}")  private String dbFlavorId;
    @Value("${app.templates.db.keyName}")   private String dbKeyName;
    @Value("${app.templates.db.tpl:classpath:/infra/terraform/cloudinit-templates/db.yaml.tpl}")
    private String dbTplPath;

    private final CloudInitRenderer renderer;      // 기존 사용
    private final ResourceLoader resourceLoader;   // 리소스 로딩

    public OrderResult applyByTemplate(OrderRequest req) throws Exception {
        final String orderId  = req.orderId();
        final TemplateType tt = req.template();
        final String hostname = req.hostname();
        final String name     = (req.name() != null && !req.name().isBlank())
                ? req.name()
                : (tt.name().toLowerCase() + "-" + hostname);

        // 주문 작업 폴더 준비
        Path orderDir     = Path.of(workRoot, orderId);
        Path cloudinitDir = orderDir.resolve("cloudinit");
        Files.createDirectories(cloudinitDir);

        // 모듈 복사 (idempotent: REPLACE_EXISTING)
        copyTree(Path.of(tfModuleDir), orderDir);

        // 템플릿별 기본값/템플릿 경로 선택
        TemplateDefaults defaults = (tt == TemplateType.WEB)
                ? new TemplateDefaults(webNetworkId, webImageId, webFlavorId, webKeyName, webTplPath, "web.yaml")
                : new TemplateDefaults(dbNetworkId,  dbImageId,  dbFlavorId,  dbKeyName,  dbTplPath,  "db.yaml");

        // cloud-init 렌더링
        Resource tplRes = resourceLoader.getResource(defaults.tplPath());
        String tpl = new String(tplRes.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        // CloudInitRenderer 사용: 템플릿별 전용 렌더 메서드 호출
        String renderedYaml = renderer.render(tpl, hostname);   // DB용 메서드(간단히 추가 필요)

        Files.writeString(cloudinitDir.resolve(defaults.cloudInitFileName()), renderedYaml);

        // terraform.tfvars 작성
        String tfvars = """
                name       = "%s"
                network_id = "%s"
                image_id   = "%s"
                flavor_id  = "%s"
                key_name   = "%s"
                user_data  = file("./cloudinit/%s")
                """.formatted(
                name,
                defaults.networkId(),
                defaults.imageId(),
                defaults.flavorId(),
                defaults.keyName(),
                defaults.cloudInitFileName()
        );
        Files.writeString(orderDir.resolve("terraform.tfvars"), tfvars);

        // Terraform 실행
        runTf(orderDir, "terraform", "init");
        runTf(orderDir, "terraform", "apply", "-auto-approve");

        // 출력 파싱 (web/db 모듈의 출력 키가 다를 수 있어 안전하게 처리)
        String outJson = runTf(orderDir, "terraform", "output", "-json");
        Map<String, Object> outputs = new ObjectMapper().readValue(outJson, Map.class);

        String serverId   = getOutputValue(outputs, "server_id", "web_server_id", "db_server_id");
        String serverName = getOutputValue(outputs, "server_name", "web_server_name", "db_server_name");
        Object ipInfo     = getOutputObject(outputs, "ip_info", "web_ip_info", "db_ip_info");

        return new OrderResult(orderId, true, serverId, serverName, ipInfo);
    }

    /** 기존 삭제 로직 그대로 사용 */
    public void destroy(String orderId) throws Exception {
        Path orderDir = Path.of(workRoot, orderId);
        runTf(orderDir, "terraform", "destroy", "-auto-approve");
    }

    // ==== 유틸 ====
    private static void copyTree(Path src, Path dst) throws IOException {
        Files.walk(src).forEach(p -> {
            try {
                Path to = dst.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(to);
                else Files.copy(p, to, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static String runTf(Path dir, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder log = new StringBuilder();
        try (var br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) log.append(line).append(System.lineSeparator());
        }
        int exit = p.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("Terraform failed: " + String.join(" ", cmd) + "\n" + log);
        }
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
