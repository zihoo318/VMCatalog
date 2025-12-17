package com.example.VMCatalog.Openstack;

import org.openstack4j.core.transport.Config;

import java.lang.reflect.Method;

/**
 * withEndpointType 메서드가 있는 패키지 찾아서 호출하기 위한 유틸.
 * +) 여기서 인터페이스란?
 * 인터페이스 : OpenStack이 서비스 URL을 여러 개 제공할 때 붙이는 접근 경로의 종류(public/internal/admin)
 * OpenStack은 인증(Keystone)을 하면 “서비스 카탈로그”라는 걸 같이 제공
 *             서비스들의 API 주소(URL) 가 들어있는데, 보통 같은 서비스라도 주소를 2~3개 넣어줌
 * public: 외부(인터넷/외부망)에서 접근하는 주소
 * internal: 클라우드 내부망(관리망/서비스망)에서 접근하는 주소
 * admin: 관리자용으로 분리한 주소(환경에 따라 없거나 제한)
 * -> 인터페이스는 어떤 네트워크 경로로 들어갈 건지 선택하는 라벨
 */
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
