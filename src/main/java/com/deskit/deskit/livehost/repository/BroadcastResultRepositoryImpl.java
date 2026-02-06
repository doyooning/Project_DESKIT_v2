package com.deskit.deskit.livehost.repository;

import com.deskit.deskit.livehost.dto.response.StatisticsResponse;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;

import java.math.BigDecimal;
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
public class BroadcastResultRepositoryImpl implements BroadcastResultRepositoryCustom {

    private final DSLContext dsl;

    private final org.jooq.Table<Record> broadcastTable = table(name("broadcast")).as("b");
    private final org.jooq.Table<Record> resultTable = table(name("broadcast_result")).as("br");

    private final Field<Long> broadcastId = field(name("b", "broadcast_id"), Long.class);
    private final Field<Long> sellerId = field(name("b", "seller_id"), Long.class);
    private final Field<String> broadcastTitle = field(name("b", "broadcast_title"), String.class);
    private final Field<LocalDateTime> startedAt = field(name("b", "started_at"), LocalDateTime.class);
    private final Field<LocalDateTime> endedAt = field(name("b", "ended_at"), LocalDateTime.class);

    private final Field<BigDecimal> totalSales = field(name("br", "total_sales"), BigDecimal.class);
    private final Field<Integer> totalViews = field(name("br", "total_views"), Integer.class);

    @Override
    public List<StatisticsResponse.ChartData> getSalesChart(Long sellerId, String periodType) {
        Field<String> dateExpr = getDateExpression(periodType);
        Field<BigDecimal> totalSalesSum = sum(totalSales);

        Map<String, BigDecimal> totals = dsl.select(dateExpr, totalSalesSum)
                .from(resultTable)
                .join(broadcastTable).on(field(name("br", "broadcast_id"), Long.class).eq(broadcastId))
                .where(
                        sellerIdEq(sellerId),
                        endedAt.isNotNull(),
                        getChartPeriodCondition(periodType, startedAt)
                )
                .groupBy(dateExpr)
                .orderBy(dateExpr.asc())
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        record -> record.get(dateExpr),
                        record -> record.get(totalSalesSum) != null ? record.get(totalSalesSum) : BigDecimal.ZERO,
                        (existing, replacement) -> replacement
                ));

        return buildChartData(periodType, totals);
    }

    @Override
    public List<StatisticsResponse.ChartData> getArpuChart(Long sellerId, String periodType) {
        Field<String> dateExpr = getDateExpression(periodType);

        Field<BigDecimal> viewSum = sum(totalViews).cast(BigDecimal.class);
        Field<BigDecimal> arpu = sum(totalSales).div(nullif(viewSum, BigDecimal.ZERO));

        Map<String, BigDecimal> totals = dsl.select(dateExpr, arpu)
                .from(resultTable)
                .join(broadcastTable).on(field(name("br", "broadcast_id"), Long.class).eq(broadcastId))
                .where(
                        sellerIdEq(sellerId),
                        endedAt.isNotNull(),
                        getChartPeriodCondition(periodType, startedAt)
                )
                .groupBy(dateExpr)
                .orderBy(dateExpr.asc())
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        record -> record.get(dateExpr),
                        record -> record.get(arpu) != null ? record.get(arpu) : BigDecimal.ZERO,
                        (existing, replacement) -> replacement
                ));

        return buildChartData(periodType, totals);
    }

    @Override
    public List<StatisticsResponse.BroadcastRank> getRanking(Long sellerId, String periodType, String sortField, boolean isDesc, int limit) {
        return dsl.select(broadcastId, broadcastTitle, totalSales, totalViews)
                .from(resultTable)
                .join(broadcastTable).on(field(name("br", "broadcast_id"), Long.class).eq(broadcastId))
                .where(
                        sellerIdEq(sellerId),
                        endedAt.isNotNull(),
                        getRankingPeriodCondition(periodType, startedAt)
                )
                .orderBy(getOrderSpecifier(sortField, isDesc))
                .limit(limit)
                .fetch(this::mapRank);
    }

    private Condition sellerIdEq(Long sellerIdValue) {
        return sellerIdValue != null ? sellerId.eq(sellerIdValue) : trueCondition();
    }

    private Field<String> getDateExpression(String periodType) {
        String format = "DAILY".equalsIgnoreCase(periodType) ? "%Y-%m-%d" :
                "MONTHLY".equalsIgnoreCase(periodType) ? "%Y-%m" : "%Y";
        return field("DATE_FORMAT({0}, {1})", String.class, startedAt, inline(format));
    }

    private List<StatisticsResponse.ChartData> buildChartData(String periodType, Map<String, BigDecimal> totals) {
        if ("DAILY".equalsIgnoreCase(periodType)) {
            LocalDate startDate = LocalDate.now().minusDays(6);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            return startDate.datesUntil(startDate.plusDays(7))
                    .map(date -> new StatisticsResponse.ChartData(
                            date.format(formatter),
                            totals.getOrDefault(date.format(formatter), BigDecimal.ZERO)
                    ))
                    .collect(Collectors.toList());
        }

        if ("MONTHLY".equalsIgnoreCase(periodType)) {
            YearMonth startMonth = YearMonth.now().minusMonths(11);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
            return startMonth.atDay(1)
                    .datesUntil(startMonth.plusMonths(12).atDay(1), java.time.Period.ofMonths(1))
                    .map(date -> {
                        String label = date.format(formatter);
                        return new StatisticsResponse.ChartData(label, totals.getOrDefault(label, BigDecimal.ZERO));
                    })
                    .collect(Collectors.toList());
        }

        Year startYear = Year.now().minusYears(4);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy");
        return startYear.atDay(1)
                .datesUntil(startYear.plusYears(5).atDay(1), java.time.Period.ofYears(1))
                .map(date -> {
                    String label = date.format(formatter);
                    return new StatisticsResponse.ChartData(label, totals.getOrDefault(label, BigDecimal.ZERO));
                })
                .collect(Collectors.toList());
    }

    private org.jooq.SortField<?> getOrderSpecifier(String sortField, boolean isDesc) {
        if ("SALES".equalsIgnoreCase(sortField)) {
            return isDesc ? totalSales.desc() : totalSales.asc();
        }
        return isDesc ? totalViews.desc() : totalViews.asc();
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

    private StatisticsResponse.BroadcastRank mapRank(Record record) {
        return StatisticsResponse.BroadcastRank.builder()
                .broadcastId(record.get(broadcastId))
                .title(record.get(broadcastTitle))
                .totalSales(record.get(totalSales))
                .totalViews(record.get(totalViews, Integer.class) != null ? record.get(totalViews) : 0)
                .build();
    }
}
