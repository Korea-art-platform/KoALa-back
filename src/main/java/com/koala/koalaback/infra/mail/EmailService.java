package com.koala.koalaback.infra.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;

    @Async
    public void sendOrderConfirmEmail(OrderConfirmData data) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(data.toEmail());
            helper.setSubject("[KoALa] 주문이 완료되었습니다 — " + data.orderNo());
            helper.setText(buildOrderConfirmTemplate(data), true);

            mailSender.send(message);
            log.info("주문 완료 이메일 발송 성공: orderNo={}, email={}", data.orderNo(), data.toEmail());
        } catch (MessagingException e) {
            log.error("주문 완료 이메일 발송 실패: orderNo={}, error={}", data.orderNo(), e.getMessage());
        }
    }

    public record OrderConfirmData(
            String toEmail,
            String ordererName,
            String orderNo,
            List<ItemData> items,
            BigDecimal productAmount,
            BigDecimal shippingAmount,
            BigDecimal totalAmount
    ) {
        public record ItemData(String skuName, int quantity, BigDecimal lineAmount) {}
    }

    private String buildOrderConfirmTemplate(OrderConfirmData data) {
        NumberFormat fmt = NumberFormat.getNumberInstance(Locale.KOREA);

        StringBuilder itemRows = new StringBuilder();
        for (OrderConfirmData.ItemData item : data.items()) {
            itemRows.append("""
                    <tr>
                      <td style="padding: 12px 0; border-bottom: 1px solid #F3F4F6; font-size: 14px; color: #111111;">
                        %s
                      </td>
                      <td style="padding: 12px 0; border-bottom: 1px solid #F3F4F6; font-size: 14px; color: #6B7280; text-align: center;">
                        %d
                      </td>
                      <td style="padding: 12px 0; border-bottom: 1px solid #F3F4F6; font-size: 14px; color: #111111; text-align: right; font-weight: bold;">
                        ₩%s
                      </td>
                    </tr>
                    """.formatted(
                    item.skuName(),
                    item.quantity(),
                    fmt.format(item.lineAmount())
            ));
        }

        String shippingRow = data.shippingAmount().compareTo(BigDecimal.ZERO) == 0
                ? "<span style=\"color: #10B981; font-weight: bold;\">무료</span>"
                : "₩" + fmt.format(data.shippingAmount());

        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 40px 20px; background-color: #FAFAFA;">
                  <div style="text-align: center; margin-bottom: 40px;">
                    <h1 style="font-size: 28px; font-weight: bold; color: #000000; letter-spacing: -1px;">KoALa</h1>
                    <p style="color: #6B7280; font-size: 14px;">Korean Art Laboratory</p>
                  </div>

                  <div style="background: #FFFFFF; border-radius: 16px; padding: 40px; border: 1px solid #E5E7EB; margin-bottom: 20px;">
                    <div style="text-align: center; margin-bottom: 32px;">
                      <div style="display: inline-block; background: #F0FDF4; border-radius: 50%%; width: 64px; height: 64px; line-height: 64px; font-size: 32px; margin-bottom: 16px;">✓</div>
                      <h2 style="font-size: 22px; font-weight: bold; color: #111111; margin: 0 0 8px;">주문이 완료되었습니다!</h2>
                      <p style="color: #6B7280; font-size: 14px; margin: 0;">%s님, 소중한 작품을 선택해 주셔서 감사합니다.</p>
                    </div>

                    <div style="background: #F9FAFB; border-radius: 12px; padding: 16px 20px; margin-bottom: 28px;">
                      <p style="font-size: 12px; color: #9CA3AF; text-transform: uppercase; letter-spacing: 1px; margin: 0 0 4px;">주문번호</p>
                      <p style="font-size: 18px; font-weight: bold; color: #111111; margin: 0; font-family: monospace;">%s</p>
                    </div>

                    <table style="width: 100%%; border-collapse: collapse; margin-bottom: 24px;">
                      <thead>
                        <tr>
                          <th style="text-align: left; font-size: 12px; color: #9CA3AF; padding-bottom: 12px; border-bottom: 2px solid #E5E7EB; text-transform: uppercase; letter-spacing: 1px;">상품명</th>
                          <th style="text-align: center; font-size: 12px; color: #9CA3AF; padding-bottom: 12px; border-bottom: 2px solid #E5E7EB; text-transform: uppercase; letter-spacing: 1px;">수량</th>
                          <th style="text-align: right; font-size: 12px; color: #9CA3AF; padding-bottom: 12px; border-bottom: 2px solid #E5E7EB; text-transform: uppercase; letter-spacing: 1px;">금액</th>
                        </tr>
                      </thead>
                      <tbody>
                        %s
                      </tbody>
                    </table>

                    <div style="border-top: 1px solid #E5E7EB; padding-top: 16px; space-y: 8px;">
                      <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
                        <span style="font-size: 14px; color: #6B7280;">상품 금액</span>
                        <span style="font-size: 14px; color: #111111;">₩%s</span>
                      </div>
                      <div style="display: flex; justify-content: space-between; margin-bottom: 16px;">
                        <span style="font-size: 14px; color: #6B7280;">배송비</span>
                        <span style="font-size: 14px;">%s</span>
                      </div>
                      <div style="display: flex; justify-content: space-between; background: #F9FAFB; border-radius: 10px; padding: 14px 16px;">
                        <span style="font-size: 16px; font-weight: bold; color: #111111;">총 결제금액</span>
                        <span style="font-size: 20px; font-weight: bold; color: #000000;">₩%s</span>
                      </div>
                    </div>
                  </div>

                  <div style="background: #FFFFFF; border-radius: 16px; padding: 24px 40px; border: 1px solid #E5E7EB; margin-bottom: 20px;">
                    <p style="font-size: 13px; color: #6B7280; margin: 0 0 8px;">배송 관련 안내는 마이페이지 &gt; 주문내역에서 확인하실 수 있습니다.</p>
                    <p style="font-size: 13px; color: #6B7280; margin: 0;">문의사항은 고객센터로 연락 주세요.</p>
                  </div>

                  <p style="color: #9CA3AF; font-size: 12px; text-align: center; margin-top: 24px;">
                    © 2024 KoALa. All rights reserved.
                  </p>
                </div>
                """.formatted(
                data.ordererName(),
                data.orderNo(),
                itemRows.toString(),
                fmt.format(data.productAmount()),
                shippingRow,
                fmt.format(data.totalAmount())
        );
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("[KoALa] 비밀번호 재설정 인증 코드");
            helper.setText(buildEmailTemplate(token), true);

            mailSender.send(message);
            log.info("비밀번호 재설정 이메일 발송 성공: {}", toEmail);
        } catch (MessagingException e) {
            log.error("이메일 발송 실패: {}", e.getMessage());
        }
    }

    private String buildEmailTemplate(String token) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 40px 20px; background-color: #FAFAFA;">
                    <div style="text-align: center; margin-bottom: 40px;">
                        <h1 style="font-size: 28px; font-weight: bold; color: #000000; letter-spacing: -1px;">KoALa</h1>
                        <p style="color: #6B7280; font-size: 14px;">Korean Art Laboratory</p>
                    </div>
                    <div style="background: #FFFFFF; border-radius: 16px; padding: 40px; border: 1px solid #E5E7EB;">
                        <h2 style="font-size: 20px; font-weight: bold; color: #111111; margin-bottom: 16px;">비밀번호 재설정</h2>
                        <p style="color: #6B7280; font-size: 14px; line-height: 1.6; margin-bottom: 32px;">
                            아래 인증 코드를 입력하여 비밀번호를 재설정하세요.<br>
                            인증 코드는 <strong>5분간</strong> 유효합니다.
                        </p>
                        <div style="background: #F3F4F6; border-radius: 12px; padding: 24px; text-align: center; margin-bottom: 32px;">
                            <span style="font-size: 32px; font-weight: bold; letter-spacing: 4px; color: #000000; font-family: monospace;">%s</span>
                        </div>
                        <p style="color: #9CA3AF; font-size: 12px; text-align: center;">
                            본인이 요청하지 않은 경우 이 이메일을 무시하세요.
                        </p>
                    </div>
                    <p style="color: #9CA3AF; font-size: 12px; text-align: center; margin-top: 24px;">
                        © 2024 KoALa. All rights reserved.
                    </p>
                </div>
                """.formatted(token);
    }
}
