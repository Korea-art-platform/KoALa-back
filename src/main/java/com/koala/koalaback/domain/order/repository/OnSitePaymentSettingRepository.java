package com.koala.koalaback.domain.order.repository;

import com.koala.koalaback.domain.order.entity.OnSitePaymentSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnSitePaymentSettingRepository extends JpaRepository<OnSitePaymentSetting, Long> {
}
