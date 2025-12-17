package com.example.VMCatalog.Openstack;

import com.example.VMCatalog.DTO.InstanceDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.openstack4j.api.OSClient;
import org.openstack4j.core.transport.Config;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.compute.VNCConsole;
import org.openstack4j.openstack.OSFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OpenStackService {

    @Value("${OS_AUTH_URL:}")
    private String authUrl;
    @Value("${OS_USERNAME:}")
    private String username;
    @Value("${OS_PASSWORD:}")
    private String password;
    @Value("${OS_PROJECT_ID:}")
    private String projectId;
    @Value("${OS_USER_DOMAIN_NAME:}")
    private String userDomainName;
    @Value("${OS_PROJECT_DOMAIN_NAME:}")
    private String projectDomainName;
    @Value("${OS_INTERFACE:internal}")
    private String iface; // internal|public|admin

    // OpenStack Keystone(v3) 인증으로 OSClientV3를 생성
    private OSClient.OSClientV3 client() {
        Identifier userDomain = Identifier.byName(userDomainName);
        Identifier projectDomain = Identifier.byName(projectDomainName);

        Identifier project = (projectId != null && !projectId.isBlank())
                ? Identifier.byId(projectId)
                : null;

        String natHost = java.net.URI.create(authUrl).getHost();
        // 서비스 엔드포인트(URL)의 호스트를 NAT 환경에 맞게 해석/치환
        // (내부 주소 때문에 API 호출이 죽는 문제(호스트 접근성 문제) 피하기 위해서)
        var cfg = Config.newConfig().withEndpointNATResolution(natHost);
        // internal/public/admin 인터페이스 선택 적용
        cfg = Os4jEndpointTypeUtil.applyEndpointInterface(cfg, iface);

        OSClient.OSClientV3 c = OSFactory.builderV3()
                .endpoint(authUrl)
                .credentials(username, password, userDomain)
                .withConfig(cfg)
                .authenticate(); // 클라이언트 만들기

        if (project != null) {
            c = OSFactory.builderV3()
                    .endpoint(authUrl)
                    .credentials(username, password, userDomain)
                    .scopeToProject(project, projectDomain)
                    .withConfig(cfg)
                    .authenticate();
        }
        return c;
    }

    public List<InstanceDto> listInstances() {
        List<? extends Server> servers = client().compute().servers().list();
        List<InstanceDto> out = new ArrayList<>(servers.size());
        for (Server s : servers) out.add(toDto(s));
        return out;
    }

    private InstanceDto toDto(Server s) {
        String console = null;
        try {
            VNCConsole v = client().compute().servers().getVNCConsole(s.getId(), VNCConsole.Type.NOVNC);
            if (v != null) console = v.getURL();
        } catch (Exception ignore) {}

        String ip = firstIp(s);
        String flavor = flavorLabel(s);
        String image = imageLabel(s);

        return new InstanceDto(
                s.getId(),
                s.getName(),
                s.getStatus() != null ? s.getStatus().name() : "UNKNOWN",
                console,
                ip,
                flavor,
                image
        );
    }

    // 주소 맵에서 첫 IP를 뽑아 단일 문자열로 반환
    private String firstIp(Server s) {
        if (s == null || s.getAddresses() == null) return null;
        Map<String, List<? extends org.openstack4j.model.compute.Address>> addrs = s.getAddresses().getAddresses();
        if (addrs == null || addrs.isEmpty()) return null;
        return addrs.values().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(a -> a.getAddr())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    // flavor id/name 반환
    private String flavorLabel(Server s) {
        if (s == null) return null;
        if (s.getFlavor() != null) {
            String id = s.getFlavor().getId();
            String name = s.getFlavor().getName();
            if (name != null && !name.isBlank() && id != null && !id.isBlank()) return name + " (" + id + ")";
            if (name != null && !name.isBlank()) return name;
            return id;
        }
        return s.getFlavorId();
    }

    // image id/name 반환
    private String imageLabel(Server s) {
        if (s == null) return null;
        if (s.getImage() != null) {
            String id = s.getImage().getId();
            String name = s.getImage().getName();
            if (name != null && !name.isBlank() && id != null && !id.isBlank()) return name + " (" + id + ")";
            if (name != null && !name.isBlank()) return name;
            return id;
        }
        return s.getImageId();
    }
}
