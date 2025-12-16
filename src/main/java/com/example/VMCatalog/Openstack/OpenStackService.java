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
    @Value("${OS_PROJECT_ID:}")
    private String projectId;
    @Value("${OS_USER_DOMAIN_NAME:}")
    private String userDomainName;
    @Value("${OS_PROJECT_DOMAIN_NAME:}")
    private String projectDomainName;
    @Value("${OS_INTERFACE:internal}")
    private String iface; // internal|public|admin

    private OSClient.OSClientV3 client() {
        Identifier userDomain = Identifier.byName(userDomainName);
        Identifier projectDomain = Identifier.byName(projectDomainName);

        Identifier project = (projectId != null && !projectId.isBlank())
                ? Identifier.byId(projectId)
                : null;

        String natHost = java.net.URI.create(authUrl).getHost();
        var cfg = Config.newConfig().withEndpointNATResolution(natHost);
        // internal/public/admin 인터페이스 선택 적용
        cfg = Os4jEndpointTypeUtil.applyEndpointInterface(cfg, iface);

        OSClient.OSClientV3 c = OSFactory.builderV3()
                .endpoint(authUrl)
                .credentials(username, password, userDomain)
                .withConfig(cfg)
                .authenticate();

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

        return new InstanceDto(
                s.getId(),
                s.getName(),
                s.getStatus() != null ? s.getStatus().name() : "UNKNOWN",
                console
        );
    }
}

