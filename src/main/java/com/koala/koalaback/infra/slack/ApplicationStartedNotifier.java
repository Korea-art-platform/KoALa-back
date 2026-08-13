package com.koala.koalaback.infra.slack;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 서버가 기동을 마치면 슬랙에 알린다.
 *
 * <h3>왜 필요한가</h3>
 * <p>지금까지 장애를 알아채는 방법은 <b>사람이 사이트에 들어가 보는 것</b>뿐이었다.
 * 실제로 두 번의 장애 모두 그렇게 발견됐다.
 *
 * <p>기동 알림은 죽는 순간을 잡지는 못한다. 대신 <b>다시 살아나는 순간</b>을 잡는다.
 * 배포하지 않았는데 이 알림이 오면 그 사이에 서버가 죽었다는 뜻이고,
 * 빌드 시각이 과거로 돌아가 있으면 롤백이 걸렸다는 뜻이다.
 * 죽은 것을 직접 감지하려면 외부에서 찔러 봐야 한다 — 자기 자신은 자기가 죽은 것을 알릴 수 없다.
 *
 * <h3>{@code ApplicationReadyEvent} 인 이유</h3>
 * <p>이 이벤트는 컨텍스트 초기화와 마이그레이션이 <b>전부 끝난 뒤</b>에 발생한다.
 * 그래서 이 알림이 왔다는 것 자체가 "Flyway 가 통과했다"는 신호이기도 하다.
 * 기동 중 실패하면 이벤트가 발생하지 않아 알림도 오지 않는다 — 그 경우는 배포 워크플로가 알린다.
 */
@Component
@RequiredArgsConstructor
public class ApplicationStartedNotifier {

    private final AdminAlertNotifier adminAlertNotifier;
    private final Environment environment;
    /** buildInfo() 가 없는 환경(IDE 직접 실행 등)에서는 빈이 없다 */
    private final ObjectProvider<BuildProperties> buildPropertiesProvider;

    private static final DateTimeFormatter BUILD_TIME_FORMAT =
            DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        adminAlertNotifier.notifyApplicationStarted(activeProfile(), buildVersion());
    }

    private String activeProfile() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length > 0 ? String.join(",", profiles) : "default";
    }

    /** "빌드 시각" 이 버전 문자열보다 낫다 — 버전은 SNAPSHOT 으로 고정돼 롤백을 구분하지 못한다 */
    private String buildVersion() {
        BuildProperties build = buildPropertiesProvider.getIfAvailable();
        if (build == null || build.getTime() == null) return "빌드 정보 없음";

        return "빌드 " + BUILD_TIME_FORMAT.format(ZonedDateTime.ofInstant(
                build.getTime(), ZoneId.of("Asia/Seoul")));
    }
}
