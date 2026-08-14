package com.koala.koalaback.infra.fcm;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FcmService {
    public void send(String token, String title, String body, String path) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("[FCM] Firebase 미초기화 — 푸시 발송 건너뜀");
            return;
        }

        try {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putData("path", path != null ? path : "/")
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("[FCM] 발송 성공: messageId={}", response);
        } catch (FirebaseMessagingException e) {
            log.error("[FCM] 발송 실패: token={}, error={}", token, e.getMessage());
        }
    }
}
