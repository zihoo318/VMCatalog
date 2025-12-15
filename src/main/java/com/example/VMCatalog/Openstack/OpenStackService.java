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
import java.util.List;

@Service
@RequiredArgsConstructor
public class OpenStackService {

    @Value("${OS_AUTH_URL:}")
    private String authUrl;
    @Value("${OS_USERNAME:}")
    private String username;
    @Value("${OS_PASSWORD:}")
    private String password;
    @Value("${OS_PROJECT_NAME:}")
    private String projectName;
    @Value("${OS_USER_DOMAIN_NAME:}")
    private String userDomainName;
    @Value("${OS_PROJECT_DOMAIN_NAME:}")
    private String projectDomainName;
    @Value("${OS_REGION_NAME:}")
    private String regionName;
    @Value("${OS_INTERFACE:internal}")
    private String iface; // internal|public|admin

    private OSClient.OSClientV3 os;

    @PostConstruct
    public void init() {
        System.out.println("crl=" + authUrl
                + ", user=" + username
                + ", project=" + projectName
                + ", userDomain=" + userDomainName
                + ", projectDomain=" + projectDomainName
                + ", region=" + regionName
                + ", iface=" + iface);

        if (authUrl == null || authUrl.isBlank()) {
            throw new IllegalStateException("OS_AUTH_URL empty. Check spring.config.import or .env.secret");
        }

        Identifier userDomain    = Identifier.byName(userDomainName);
        Identifier projectDomain = Identifier.byName(projectDomainName);

        // Config 생성
        var cfg = Config.newConfig();
        // 엔드포인트 인터페이스 적용
        cfg = Os4jEndpointTypeUtil.applyEndpointInterface(cfg, iface);

        this.os = OSFactory.builderV3()
                .endpoint(authUrl)
                .credentials(username, password, userDomain)
                .scopeToProject(projectDomain, Identifier.byId(projectName))
                .withConfig(cfg)
                .authenticate();

        if (regionName != null && !regionName.isBlank()) {
            this.os.useRegion(regionName);
        }
    }

    public List<InstanceDto> listInstances() {
        List<? extends Server> servers = os.compute().servers().list();
        List<InstanceDto> out = new ArrayList<>(servers.size());
        for (Server s : servers) out.add(toDto(s));
        return out;
    }

    private InstanceDto toDto(Server s) {
        String console = null;
        try {
            VNCConsole v = os.compute().servers().getVNCConsole(s.getId(), VNCConsole.Type.NOVNC);
            if (v != null) console = v.getURL();
        } catch (Exception ignore) {}

        return new InstanceDto(
                s.getId(),
                s.getName(),
                s.getStatus() != null ? s.getStatus().name() : "UNKNOWN",
                console
        );
    }
}

