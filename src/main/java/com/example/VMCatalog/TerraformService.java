package com.example.VMCatalog;

import com.example.VMCatalog.DTO.OrderRequest;
import com.example.VMCatalog.DTO.OrderResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TerraformService {

    @Value("${app.workDir}")     private String workRoot;     // 주문별 작업폴더 루트
    @Value("${app.tfModuleDir}") private String tfModuleDir;  // 리포 내 모듈 경로

    private final CloudInitRenderer renderer;
    private final ResourceLoader resourceLoader;

    public OrderResult applyWebVm(OrderRequest req) throws Exception {
        Path orderDir     = Path.of(workRoot, req.orderId());
        Path cloudinitDir = orderDir.resolve("cloudinit");
        Files.createDirectories(cloudinitDir);

        copyTree(Path.of(tfModuleDir), orderDir);

        var res = resourceLoader.getResource("classpath:/infra/terraform/cloudinit-templates/web.yaml.tpl");
        String tpl = new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String webYaml = renderer.renderWeb(tpl, req.hostname());
        Files.writeString(cloudinitDir.resolve("web.yaml"), webYaml);

        String tfvars = """
      name       = "%s"
      network_id = "%s"
      image_id   = "%s"
      flavor_id  = "%s"
      key_name   = "%s"
      user_data  = file("./cloudinit/web.yaml")
      """.formatted(req.name(), req.networkId(), req.imageId(), req.flavorId(), req.keyName());
        Files.writeString(orderDir.resolve("terraform.tfvars"), tfvars);

        // apply가 실패하면 runTf가 예외를 던지므로 "생성 실패"로 간주
        runTf(orderDir, "terraform", "init");
        runTf(orderDir, "terraform", "apply", "-auto-approve");

        // 여기까지 오면 "생성 성공"
        String outJson = runTf(orderDir, "terraform", "output", "-json");
        Map<String,Object> outputs = new ObjectMapper().readValue(outJson, Map.class);

        String serverId   = (String) ((Map) outputs.get("web_server_id")).get("value");
        String serverName = (String) ((Map) outputs.get("web_server_name")).get("value");
        Object ipInfo     = ((Map) outputs.get("web_ip_info")).get("value");

        return new OrderResult(req.orderId(), true, serverId, serverName, ipInfo);
    }

    public void destroy(String orderId) throws Exception {
        Path orderDir = Path.of(workRoot, orderId);
        runTf(orderDir, "terraform", "destroy", "-auto-approve");
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
}