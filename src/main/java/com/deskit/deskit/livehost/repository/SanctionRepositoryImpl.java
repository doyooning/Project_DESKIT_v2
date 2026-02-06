package com.deskit.deskit.livehost.repository;

import com.deskit.deskit.livehost.common.enums.BroadcastStatus;
import com.deskit.deskit.livehost.common.enums.SanctionType;
import com.deskit.deskit.livehost.dto.response.SanctionStatisticsResponse;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.*;

@RequiredArgsConstructor
public class SanctionRepositoryImpl implements SanctionRepositoryCustom {

    private final DSLContext dsl;

    private final org.jooq.Table<Record> broadcastTable = table(name("broadcast")).as("b");
    private final org.jooq.Table<Record> sanctionTable = table(name("sanction")).as("sc");
    private final org.jooq.Table<Record> sellerTable = table(name("seller")).as("s");
    private final org.jooq.Table<Record> memberTable = table(name("member")).as("m");

    private final Field<String> broadcastStatus = field(name("b", "status"), String.class);
    private final Field<LocalDateTime> broadcastEndedAt = field(name("b", "ended_at"), LocalDateTime.class);
    private final Field<Long> sellerId = field(name("s", "seller_id"), Long.class);
    private final Field<String> sellerName = field(name("s", "name"), String.class);
    private final Field<String> sellerLoginId = field(name("s", "login_id"), String.class);

    private final Field<Long> sanctionId = field(name("sc", "sanction_id"), Long.class);
    private final Field<Long> sanctionMemberId = field(name("sc", "member_id"), Long.class);
    private final Field<String> sanctionStatus = field(name("sc", "status"), String.class);
    private final Field<LocalDateTime> sanctionCreatedAt = field(name("sc", "created_at"), LocalDateTime.class);

    private final Field<String> memberName = field(name("m", "name"), String.class);

    @Override
    public List<SanctionStatisticsResponse.ChartData> getSellerForceStopChart(String periodType) {
        Field<String> dateExpr = getDateExpression(periodType, broadcastEndedAt);

        Map<String, Long> totals = dsl.select(dateExpr, count())
                .from(broadcastTable)
                .where(
                        broadcastStatus.eq(BroadcastStatus.STOPPED.name()),
                        getChartPeriodCondition(periodType, broadcastEndedAt)
                )
                .groupBy(dateExpr)
                .orderBy(dateExpr.asc())
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        record -> record.get(dateExpr),
                        record -> record.get(count(), Long.class) != null ? record.get(count(), Long.class) : 0L,
                        (existing, replacement) -> replacement
                ));

        return buildChartData(periodType, totals);
    }

    @Override
    public List<SanctionStatisticsResponse.ChartData> getViewerSanctionChart(String periodType) {
        Field<String> dateExpr = getDateExpression(periodType, sanctionCreatedAt);

        Map<String, Long> totals = dsl.select(dateExpr, count())
                .from(sanctionTable)
                .where(
                        sanctionStatus.in(SanctionType.MUTE.name(), SanctionType.OUT.name()),
                        getChartPeriodCondition(periodType, sanctionCreatedAt)
                )
                .groupBy(dateExpr)
                .orderBy(dateExpr.asc())
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        record -> record.get(dateExpr),
                        record -> record.get(count(), Long.class) != null ? record.get(count(), Long.class) : 0L,
                        (existing, replacement) -> replacement
                ));

        return buildChartData(periodType, totals);
    }

    @Override
    public List<SanctionStatisticsResponse.SellerRank> getSellerForceStopRanking(String periodType, int limit) {
        return dsl.select(sellerId, sellerName, sellerLoginId, count())
                .from(broadcastTable)
                .join(sellerTable).on(field(name("b", "seller_id"), Long.class).eq(sellerId))
                .where(
                        broadcastStatus.eq(BroadcastStatus.STOPPED.name()),
                        getRankingPeriodCondition(periodType, broadcastEndedAt)
                )
                .groupBy(sellerId, sellerName, sellerLoginId)
                .orderBy(count().desc())
                .limit(limit)
                .fetch(this::mapSellerRank);
    }

    @Override
    public List<SanctionStatisticsResponse.ViewerRank> getViewerSanctionRanking(String periodType, int limit) {
        return dsl.select(sanctionMemberId, memberName, count())
                .from(sanctionTable)
                .leftJoin(memberTable).on(field(name("m", "member_id"), Long.class).eq(sanctionMemberId))
                .where(
                        getRankingPeriodCondition(periodType, sanctionCreatedAt)
                )
                .groupBy(sanctionMemberId, memberName)
                .orderBy(count().desc())
                .limit(limit)
                .fetch(this::mapViewerRank);
    }

    @Override
    public SanctionTypeResult findLatestSanction(Long broadcastIdValue, Long memberIdValue) {
        Record record = dsl.select(sanctionId, sanctionStatus)
                .from(sanctionTable)
                .where(
                        field(name("sc", "broadcast_id"), Long.class).eq(broadcastIdValue),
                        sanctionMemberId.eq(memberIdValue)
                )
                .orderBy(sanctionCreatedAt.desc())
                .limit(1)
                .fetchOne();

        if (record == null) {
            return null;
        }
        return new SanctionTypeResult(
                record.get(sanctionId),
                record.get(sanctionStatus)
        );
    }

    private Field<String> getDateExpression(String periodType, Field<LocalDateTime> datePath) {
        String format = "DAILY".equalsIgnoreCase(periodType) ? "%Y-%m-%d" :
                "MONTHLY".equalsIgnoreCase(periodType) ? "%Y-%m" : "%Y";
        return field("DATE_FORMAT({0}, {1})", String.class, datePath, inline(format));
    }

    private List<SanctionStatisticsResponse.ChartData> buildChartData(String periodType, Map<String, Long> totals) {
        if ("DAILY".equalsIgnoreCase(periodType)) {
            LocalDate startDate = LocalDate.now().minusDays(6);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            return startDate.datesUntil(startDate.plusDays(7))
                    .map(date -> {
                        String label = date.format(formatter);
                        return new SanctionStatisticsResponse.ChartData(label, totals.getOrDefault(label, 0L));
                    })
                    .collect(Collectors.toList());
        }

        if ("MONTHLY".equalsIgnoreCase(periodType)) {
            YearMonth startMonth = YearMonth.now().minusMonths(11);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
            return startMonth.atDay(1)
                    .datesUntil(startMonth.plusMonths(12).atDay(1), java.time.Period.ofMonths(1))
                    .map(date -> {
                        String label = date.format(formatter);
                        return new SanctionStatisticsResponse.ChartData(label, totals.getOrDefault(label, 0L));
                    })
                    .collect(Collectors.toList());
        }

        Year startYear = Year.now().minusYears(4);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy");
        return startYear.atDay(1)
                .datesUntil(startYear.plusYears(5).atDay(1), java.time.Period.ofYears(1))
                .map(date -> {
                    String label = date.format(formatter);
                    return new SanctionStatisticsResponse.ChartData(label, totals.getOrDefault(label, 0L));
                })
                .collect(Collectors.toList());
    }

    private Condition getChartPeriodCondition(String type, Field<LocalDateTime> path) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate;

        if ("DAILY".equalsIgnoreCase(type)) {
            startDate = now.minusDays(6).with(LocalTime.MIN);
        } else if ("MONTHLY".equalsIgnoreCase(type)) {
            startDate = now.minusMonths(11).withDayOfMonth(1).with(LocalTime.MIN);
        } else {
            startDate = now.minusYears(4).withDayOfYear(1).with(LocalTime.MIN);
        }
        return path.ge(startDate);
    }

    private Condition getRankingPeriodCondition(String type, Field<LocalDateTime> path) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate;

        if ("DAILY".equalsIgnoreCase(type)) {
            startDate = now.with(LocalTime.MIN);
        } else if ("MONTHLY".equalsIgnoreCase(type)) {
            startDate = now.withDayOfMonth(1).with(LocalTime.MIN);
        } else {
            startDate = now.with(TemporalAdjusters.firstDayOfYear()).with(LocalTime.MIN);
        }
        return path.ge(startDate);
    }

    private SanctionStatisticsResponse.SellerRank mapSellerRank(Record record) {
        return SanctionStatisticsResponse.SellerRank.builder()
                .sellerId(record.get(sellerId))
                .sellerName(record.get(sellerName))
                .email(record.get(sellerLoginId))
                .sanctionCount(record.get(count(), Long.class))
                .build();
    }

    private SanctionStatisticsResponse.ViewerRank mapViewerRank(Record record) {
        return SanctionStatisticsResponse.ViewerRank.builder()
                .viewerId(record.get(sanctionMemberId) != null ? record.get(sanctionMemberId).toString() : "비회원")
                .name(record.get(memberName) != null ? record.get(memberName) : "비회원")
                .sanctionCount(record.get(count(), Long.class))
                .build();
    }
}
