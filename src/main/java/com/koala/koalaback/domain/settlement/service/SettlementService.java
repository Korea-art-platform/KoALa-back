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

/**
 * 작가 정산.
 *
 * <h3>확정 전과 후가 다르게 동작한다</h3>
 * <ul>
 *   <li><b>확정 전</b> — 조회할 때마다 주문에서 계산한다. 반품이 더 들어오면 숫자가 바뀐다.</li>
 *   <li><b>확정 후</b> — 저장된 스냅샷을 그대로 보여준다. 무슨 일이 있어도 바뀌지 않는다.</li>
 * </ul>
 *
 * <p>이렇게 나눈 이유는, 다시 계산하면 <b>이미 지급한 달의 금액이 나중에 달라지기</b> 때문이다.
 * 장부와 실제 송금액이 어긋나면 맞추는 데 드는 비용이 훨씬 크다.
 *
 * <h3>반올림</h3>
 * <p>수수료를 원 단위로 반올림하고, 지급액은 <b>빼서</b> 구한다.
 * 둘 다 따로 반올림하면 수수료 + 지급액 ≠ 순매출이 되어 1원씩 새는 장부가 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    private final SettlementAggregationRepository aggregationRepository;
    private final ArtistSettlementRepository settlementRepository;
    private final ArtistRepository artistRepository;

    /** 한 달치 정산 — 확정됐으면 스냅샷, 아니면 지금 계산한 값 */
    public SettlementDto.PeriodSummaryResponse getPeriod(String periodYm) {
        YearMonth period = parsePeriod(periodYm);

        List<SettlementDto.ArtistSettlementResponse> items =
                settlementRepository.existsByPeriodYm(periodYm)
                        ? loadConfirmed(periodYm)
                        : calculate(period);

        return summarize(periodYm, settlementRepository.existsByPeriodYm(periodYm), items);
    }

    /**
     * 정산 확정 — 지금 계산된 값을 굳힌다.
     *
     * <p>이미 확정된 달은 다시 확정할 수 없다. 다시 확정하면 지급 상태가 초기화되어
     * 같은 달을 두 번 지급하게 된다.
     *
     * <p>지급액이 0 이하인 작가는 행을 만들지 않는다. 매출이 없었거나 반품이 매출보다
     * 많았던 경우인데, 후자는 <b>다음 달로 넘겨 차감해야 할 빚</b>이라 자동 처리 대상이 아니다.
     * 그런 달은 로그에 남겨 사람이 보게 한다.
     */
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

    /** 지급 완료 표시 — 실제 송금은 밖에서 이뤄지고, 여기는 기록만 남긴다 */
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

    // ---------------------------------------------------------------- 계산

    /**
     * 기간의 작가별 정산액을 계산한다.
     *
     * <p>매출과 반품을 각각 집계한 뒤 작가 단위로 합친다. <b>반품만 있고 매출이 없는 작가</b>도
     * 결과에 들어가야 한다 — 지난달 판매분이 이번 달에 반품된 경우가 그렇다.
     * 매출 쪽만 순회하면 그 작가가 통째로 빠진다.
     */
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
            if (artist == null) continue;   // 작가가 지워진 옛 데이터

            result.add(build(artistId, artist.getName(), period.toString(),
                    gross.getOrDefault(artistId, BigDecimal.ZERO),
                    refund.getOrDefault(artistId, BigDecimal.ZERO),
                    rateOf(artist),
                    null, null, null));
        }
        return result;
    }

    /** 수수료율이 비어 있는 옛 행을 기본값으로 메운다 — null 이면 계산 전체가 터진다 */
    private BigDecimal rateOf(Artist artist) {
        return artist.getCommissionRate() != null
                ? artist.getCommissionRate()
                : Artist.DEFAULT_COMMISSION_RATE;
    }

    /**
     * 금액 계산 — 정산 로직의 전부가 여기 모여 있다.
     *
     * <p>{@code payout = net - commission} 으로 구한다. 곱셈을 두 번 하면 반올림이 두 번
     * 일어나 합계가 어긋난다.
     */
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
