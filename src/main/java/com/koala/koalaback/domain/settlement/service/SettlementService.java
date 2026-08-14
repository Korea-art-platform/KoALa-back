package com.koala.koalaback.domain.settlement.service;

import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.domain.artist.repository.ArtistRepository;
import com.koala.koalaback.domain.settlement.dto.SettlementDto;
import com.koala.koalaback.domain.settlement.entity.ArtistSettlement;
import com.koala.koalaback.domain.settlement.repository.ArtistSettlementRepository;
import com.koala.koalaback.domain.settlement.repository.SettlementAggregationRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {
    private final SettlementAggregationRepository aggregationRepository;
    private final ArtistSettlementRepository settlementRepository;
    private final ArtistRepository artistRepository;

    public SettlementDto.PeriodSummaryResponse getPeriod(String periodYm) {
        YearMonth period = parsePeriod(periodYm);

        List<SettlementDto.ArtistSettlementResponse> items =
                settlementRepository.existsByPeriodYm(periodYm)
                        ? loadConfirmed(periodYm)
                        : calculate(period);

        return summarize(periodYm, settlementRepository.existsByPeriodYm(periodYm), items);
    }

    @Transactional
    public SettlementDto.PeriodSummaryResponse confirm(String periodYm) {
        YearMonth period = parsePeriod(periodYm);

        if (settlementRepository.existsByPeriodYm(periodYm)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    periodYm + " 정산은 이미 확정되었습니다.");
        }
        if (!period.isBefore(YearMonth.now())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "아직 끝나지 않은 달은 확정할 수 없습니다.");
        }

        List<SettlementDto.ArtistSettlementResponse> calculated = calculate(period);

        for (SettlementDto.ArtistSettlementResponse item : calculated) {
            if (item.payoutAmount().compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("[Settlement] 지급액이 0 이하라 확정에서 제외: artistId={}, period={}, payout={}",
                        item.artistId(), periodYm, item.payoutAmount());
                continue;
            }
            settlementRepository.save(ArtistSettlement.builder()
                    .artistId(item.artistId())
                    .periodYm(periodYm)
                    .grossAmount(item.grossAmount())
                    .refundAmount(item.refundAmount())
                    .commissionRate(item.commissionRate())
                    .commissionAmount(item.commissionAmount())
                    .payoutAmount(item.payoutAmount())
                    .build());
        }

        log.info("[Settlement] {} 정산 확정 — 작가 {}명", periodYm, calculated.size());
        return getPeriod(periodYm);
    }

    @Transactional
    public void markPaid(Long settlementId, String memo) {
        ArtistSettlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        try {
            settlement.markPaid(memo);
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage());
        }
    }

    @Transactional
    public void changeCommissionRate(Long artistId, BigDecimal rate) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTIST_NOT_FOUND));
        try {
            artist.changeCommissionRate(rate);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage());
        }
    }

    List<SettlementDto.ArtistSettlementResponse> calculate(YearMonth period) {
        LocalDateTime start = period.atDay(1).atStartOfDay();
        LocalDateTime end = period.plusMonths(1).atDay(1).atStartOfDay();

        Map<Long, BigDecimal> gross = toMap(aggregationRepository.sumDeliveredByArtist(start, end));
        Map<Long, BigDecimal> refund = toMap(aggregationRepository.sumRefundedByArtist(start, end));

        Set<Long> artistIds = new LinkedHashSet<>(gross.keySet());
        artistIds.addAll(refund.keySet());
        if (artistIds.isEmpty()) return List.of();

        Map<Long, Artist> artists = artistRepository.findAllById(artistIds).stream()
                .collect(Collectors.toMap(Artist::getId, Function.identity()));

        List<SettlementDto.ArtistSettlementResponse> result = new ArrayList<>();
        for (Long artistId : artistIds) {
            Artist artist = artists.get(artistId);
            if (artist == null) continue;

            result.add(build(artistId, artist.getName(), period.toString(),
                    gross.getOrDefault(artistId, BigDecimal.ZERO),
                    refund.getOrDefault(artistId, BigDecimal.ZERO),
                    rateOf(artist),
                    null, null, null));
        }
        return result;
    }

    private BigDecimal rateOf(Artist artist) {
        return artist.getCommissionRate() != null
                ? artist.getCommissionRate()
                : Artist.DEFAULT_COMMISSION_RATE;
    }

    private SettlementDto.ArtistSettlementResponse build(
            Long artistId, String artistName, String periodYm,
            BigDecimal gross, BigDecimal refund, BigDecimal rate,
            Long settlementId, String status, LocalDateTime paidAt) {
        BigDecimal net = gross.subtract(refund).setScale(2, RoundingMode.HALF_UP);
        BigDecimal commission = net.multiply(rate).setScale(0, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal payout = net.subtract(commission);

        return new SettlementDto.ArtistSettlementResponse(
                settlementId, artistId, artistName, periodYm,
                gross.setScale(2, RoundingMode.HALF_UP),
                refund.setScale(2, RoundingMode.HALF_UP),
                net, rate, commission, payout,
                settlementId != null, status, paidAt, null);
    }

    private List<SettlementDto.ArtistSettlementResponse> loadConfirmed(String periodYm) {
        List<ArtistSettlement> saved = settlementRepository.findByPeriodYm(periodYm);
        Map<Long, Artist> artists = artistRepository.findAllById(
                        saved.stream().map(ArtistSettlement::getArtistId).toList()).stream()
                .collect(Collectors.toMap(Artist::getId, Function.identity()));

        return saved.stream()
                .map(s -> new SettlementDto.ArtistSettlementResponse(
                        s.getId(), s.getArtistId(),
                        artists.containsKey(s.getArtistId())
                                ? artists.get(s.getArtistId()).getName() : "(삭제된 작가)",
                        s.getPeriodYm(),
                        s.getGrossAmount(), s.getRefundAmount(),
                        s.getGrossAmount().subtract(s.getRefundAmount()),
                        s.getCommissionRate(), s.getCommissionAmount(), s.getPayoutAmount(),
                        true, s.getStatus(), s.getPaidAt(), s.getMemo()))
                .toList();
    }

    private SettlementDto.PeriodSummaryResponse summarize(
            String periodYm, boolean confirmed,
            List<SettlementDto.ArtistSettlementResponse> items) {
        return new SettlementDto.PeriodSummaryResponse(
                periodYm, confirmed, items.size(),
                sum(items, SettlementDto.ArtistSettlementResponse::grossAmount),
                sum(items, SettlementDto.ArtistSettlementResponse::refundAmount),
                sum(items, SettlementDto.ArtistSettlementResponse::commissionAmount),
                sum(items, SettlementDto.ArtistSettlementResponse::payoutAmount),
                items);
    }

    private BigDecimal sum(List<SettlementDto.ArtistSettlementResponse> items,
                           Function<SettlementDto.ArtistSettlementResponse, BigDecimal> field) {
        return items.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Map<Long, BigDecimal> toMap(
            List<SettlementAggregationRepository.ArtistAmount> rows) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (SettlementAggregationRepository.ArtistAmount row : rows) {
            if (row.getArtistId() == null) continue;
            map.put(row.getArtistId(),
                    row.getAmount() != null ? row.getAmount() : BigDecimal.ZERO);
        }
        return map;
    }

    private YearMonth parsePeriod(String periodYm) {
        try {
            return YearMonth.parse(periodYm);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "정산 월 형식이 올바르지 않습니다. (예: 2026-08)");
        }
    }
}
