package com.koala.koalaback.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * 앱인토스 mTLS 설정.
 * <p>
 * 토스 콘솔에서 발급한 PEM 인증서/키를 그대로 사용한다:
 *   - cert-path : client-cert.pem (인증서, 체인 포함 가능)
 *   - key-path  : client-key.pem  (개인키, PKCS#8 형식)
 * <p>
 * 둘 다 설정돼 있으면 mTLS 적용, 없으면(로컬/개발) 일반 RestTemplate.
 */
@Configuration
public class TossMtlsConfig {

    @Value("${apps-in-toss.cert-path:}")
    private String certPath;   // client-cert.pem

    @Value("${apps-in-toss.key-path:}")
    private String keyPath;    // client-key.pem

    @Bean(name = "tossRestTemplate")
    public RestTemplate tossRestTemplate() throws Exception {
        // 인증서/키 미설정 → 일반 RestTemplate (로컬/개발용)
        if (certPath == null || certPath.isBlank() || keyPath == null || keyPath.isBlank()) {
            return new RestTemplate();
        }

        // 1) PEM 인증서(체인) 로드
        List<X509Certificate> chain = loadCertificateChain(certPath);

        // 2) PEM 개인키(PKCS#8) 로드
        PrivateKey privateKey = loadPrivateKey(keyPath);

        // 3) 메모리 KeyStore에 [개인키 + 인증서 체인] 등록 (파일 저장 X)
        char[] memPass = "toss".toCharArray(); // 메모리 전용이라 아무 값이나 가능
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);             // 빈 키스토어 초기화
        keyStore.setKeyEntry("toss", privateKey, memPass, chain.toArray(new Certificate[0]));

        // 4) KeyStore → KeyManager ("내 신분증" 역할)
        KeyManagerFactory kmf =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, memPass);

        // 5) SSLContext (서버 검증은 기본 신뢰저장소 사용 → 두 번째 인자 null)
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        // 6) JDK HttpClient에 SSL 적용 후 RestTemplate에 연결
        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();
        return new RestTemplate(new JdkClientHttpRequestFactory(httpClient));
    }

    /** PEM 파일에서 X.509 인증서(체인) 읽기 */
    private List<X509Certificate> loadCertificateChain(String path) throws Exception {
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            List<X509Certificate> list = cf.generateCertificates(in).stream()
                    .map(c -> (X509Certificate) c)
                    .toList();
            if (list.isEmpty()) {
                throw new IllegalStateException("인증서를 찾을 수 없습니다: " + path);
            }
            return list;
        }
    }

    /** PEM(PKCS#8) 파일에서 개인키 읽기 */
    private PrivateKey loadPrivateKey(String path) throws Exception {
        String pem = Files.readString(Path.of(path));

        // PKCS#1(RSA)/SEC1(EC) 형식은 JDK 단독으로 못 읽으므로 변환 안내
        if (pem.contains("BEGIN RSA PRIVATE KEY") || pem.contains("BEGIN EC PRIVATE KEY")) {
            throw new IllegalStateException(
                    "개인키가 PKCS#8 형식이 아닙니다. 아래 명령으로 변환 후 key-path에 지정하세요:\n" +
                    "  openssl pkcs8 -topk8 -nocrypt -in " + path + " -out client-key-pkcs8.pem");
        }

        String base64 = pem
                .replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);

        // 키 알고리즘(RSA/EC)을 미리 알 수 없으므로 순차 시도
        for (String alg : new String[]{"RSA", "EC"}) {
            try {
                return KeyFactory.getInstance(alg).generatePrivate(spec);
            } catch (Exception ignore) {
                // 다음 알고리즘 시도
            }
        }
        throw new IllegalStateException("지원하지 않는 개인키 형식입니다: " + path);
    }
}
