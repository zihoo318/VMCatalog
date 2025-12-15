package com.example.VMCatalog.Openstack;

import org.openstack4j.core.transport.Config;

import java.lang.reflect.Method;

final class Os4jEndpointTypeUtil {
    /**
     * iface: "internal" | "public" | "admin" (대소문자 무관)
     * Config.withEndpointType(...) 의 파라미터 타입이 버전마다 달라서,
     * 존재하는 enum을 리플렉션으로 찾아 호출합니다.
     */
    static Config applyEndpointInterface(Config cfg, String iface) {
        String target = normalize(iface); // "PUBLIC" | "INTERNAL" | "ADMIN"

        // 후보 enum 클래스들 (버전별로 다름)
        String[] candidates = new String[] {
                "org.openstack4j.core.transport.ClientConstants$EndpointType",
                "org.openstack4j.core.transport.EndpointType",
                "org.openstack4j.core.transport.ClientConstants$Facing",
                "org.openstack4j.core.transport.Facing"
        };

        for (String cn : candidates) {
            try {
                Class<?> enumClass = Class.forName(cn);
                @SuppressWarnings("unchecked")
                Object enumConst = Enum.valueOf((Class<Enum>) enumClass, target);

                // withEndpointType(그 enum) 메서드를 찾아 호출
                Method m = Config.class.getMethod("withEndpointType", enumClass);

                Object ret = m.invoke(cfg, enumConst);
                return (Config) ret;
            } catch (Throwable ignore) {
                // 해당 enum/시그니처가 없으면 다음 후보로
            }
        }
        // 아무 것도 못 찾으면 그대로 반환(디폴트 인터페이스 사용)
        return cfg;
    }

    private static String normalize(String s) {
        if (s == null) return "INTERNAL";
        s = s.trim().toLowerCase();
        return switch (s) {
            case "public"  -> "PUBLIC";
            case "admin"   -> "ADMIN";
            default        -> "INTERNAL";
        };
    }
}
