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

@Component
@RequiredArgsConstructor
public class ApplicationStartedNotifier {
    private final AdminAlertNotifier adminAlertNotifier;
    private final Environment environment;

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

    private String buildVersion() {
        BuildProperties build = buildPropertiesProvider.getIfAvailable();
        if (build == null || build.getTime() == null) return "빌드 정보 없음";

        return "빌드 " + BUILD_TIME_FORMAT.format(ZonedDateTime.ofInstant(
                build.getTime(), ZoneId.of("Asia/Seoul")));
    }
}
