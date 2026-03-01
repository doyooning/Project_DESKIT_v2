package com.deskit.deskit.livehost.service;

import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.account.repository.MemberRepository;
import com.deskit.deskit.account.repository.SellerRepository;
import com.deskit.deskit.livehost.common.enums.BroadcastProductStatus;
import com.deskit.deskit.livehost.common.enums.BroadcastStatus;
import com.deskit.deskit.livehost.common.enums.SanctionType;
import com.deskit.deskit.livehost.common.enums.VodStatus;
import com.deskit.deskit.livehost.common.exception.BusinessException;
import com.deskit.deskit.livehost.common.exception.ErrorCode;
import com.deskit.deskit.livehost.dto.request.BroadcastCreateRequest;
import com.deskit.deskit.livehost.dto.request.BroadcastProductRequest;
import com.deskit.deskit.livehost.dto.request.BroadcastSearch;
import com.deskit.deskit.livehost.dto.request.BroadcastUpdateRequest;
import com.deskit.deskit.livehost.dto.request.MediaConfigRequest;
import com.deskit.deskit.livehost.dto.request.OpenViduRecordingWebhook;
import com.deskit.deskit.livehost.dto.request.QcardRequest;
import com.deskit.deskit.livehost.dto.response.BroadcastAllResponse;
import com.deskit.deskit.livehost.dto.response.BroadcastListResponse;
import com.deskit.deskit.livehost.dto.response.BroadcastLikeResponse;
import com.deskit.deskit.livehost.dto.response.BroadcastProductResponse;
import com.deskit.deskit.livehost.dto.response.BroadcastResponse;
import com.deskit.deskit.livehost.dto.response.BroadcastReportResponse;
import com.deskit.deskit.livehost.dto.response.BroadcastResultResponse;
import com.deskit.deskit.livehost.dto.response.BroadcastStatsResponse;
import com.deskit.deskit.livehost.dto.response.MediaConfigResponse;
import com.deskit.deskit.livehost.dto.response.ProductSelectResponse;
import com.deskit.deskit.livehost.dto.response.QcardResponse;
import com.deskit.deskit.livehost.dto.response.ReservationSlotResponse;
import com.deskit.deskit.livehost.dto.response.StatisticsResponse;
import com.deskit.deskit.livehost.entity.Broadcast;
import com.deskit.deskit.livehost.entity.BroadcastProduct;
import com.deskit.deskit.livehost.entity.BroadcastResult;
import com.deskit.deskit.livehost.entity.Qcard;
import com.deskit.deskit.livehost.entity.Vod;
import com.deskit.deskit.livehost.entity.ViewHistory;
import com.deskit.deskit.livehost.repository.BroadcastProductRepository;
import com.deskit.deskit.livehost.repository.BroadcastRepository;
import com.deskit.deskit.livehost.repository.BroadcastRepositoryCustom;
import com.deskit.deskit.livehost.repository.BroadcastResultRepository;
import com.deskit.deskit.livehost.repository.SanctionRepository;
import com.deskit.deskit.livehost.repository.SanctionRepositoryCustom;
import com.deskit.deskit.livehost.repository.ViewHistoryRepository;
import com.deskit.deskit.livehost.repository.VodRepository;
import com.deskit.deskit.livechat.dto.LiveMessageType;
import com.deskit.deskit.livechat.repository.LiveChatRepository;
import com.deskit.deskit.order.enums.OrderStatus;
import com.deskit.deskit.product.entity.Product;
import com.deskit.deskit.product.entity.Product.Status;
import com.deskit.deskit.product.entity.ProductImage;
import com.deskit.deskit.product.entity.ProductImage.ImageType;
import com.deskit.deskit.product.repository.ProductImageRepository;
import com.deskit.deskit.product.repository.ProductRepository;
import com.deskit.deskit.tag.entity.TagCategory;
import com.deskit.deskit.tag.repository.TagCategoryRepository;
import io.openvidu.java.client.OpenViduHttpException;
import io.openvidu.java.client.OpenViduJavaClientException;
import io.openvidu.java.client.Recording;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

@Slf4j
@Service
@RequiredArgsConstructor
public class BroadcastService {

    private static final int RECORDING_RETRY_MAX_ATTEMPTS = 5;
    private static final Duration RECORDING_RETRY_TTL = Duration.ofHours(6);
    private static final Duration RECORDING_RETRY_BASE_DELAY = Duration.ofSeconds(30);
    private static final int RECORDING_START_RETRY_MAX_ATTEMPTS = 10;
    private static final Duration RECORDING_START_RETRY_TTL = Duration.ofMinutes(30);
    private static final Duration RECORDING_START_RETRY_BASE_DELAY = Duration.ofSeconds(5);

    private final BroadcastRepository broadcastRepository;
    private final BroadcastProductRepository broadcastProductRepository;
    private final ProductImageRepository productImageRepository;
    private final com.deskit.deskit.livehost.repository.QcardRepository qcardRepository;
    private final BroadcastResultRepository broadcastResultRepository;
    private final VodRepository vodRepository;

    private final MemberRepository memberRepository;
    private final SellerRepository sellerRepository;
    private final TagCategoryRepository tagCategoryRepository;
    private final ProductRepository productRepository;
    private final SanctionRepository sanctionRepository;
    private final ViewHistoryRepository viewHistoryRepository;
    private final LiveChatRepository liveChatRepository;
    private final VodStatsService vodStatsService;

    private final RedisService redisService;
    private final SseService sseService;
    private final OpenViduService openViduService;
    private final BroadcastScheduleEmailService broadcastScheduleEmailService;
    private final AwsS3Service s3Service;
    private final DSLContext dsl;

    @Value("${openvidu.url}")
    private String openViduUrl;

    @Value("${openvidu.secret}")
    private String openViduSecret;

    @Value("${vod.admin-download-dir:${user.home}/deskit-admin-vod}")
    private String adminVodDownloadDir;

    @Transactional
    public Long createBroadcast(Long sellerId, BroadcastCreateRequest request) {
        String lockKey = "lock:seller:" + sellerId + ":broadcast_create";
        String slotLockKey = "lock:broadcast_slot:" + request.getScheduledAt().toString();
        String dbSlotLockKey = buildDbSlotLockKey(request.getScheduledAt());
        boolean dbSlotLocked = false;

        if (!Boolean.TRUE.equals(redisService.acquireLock(lockKey, 3000))) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        try {
            if (!Boolean.TRUE.equals(redisService.acquireLock(slotLockKey, 3000))) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
            }
            if (!acquireDbSlotLock(dbSlotLockKey, 3)) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
            }
            dbSlotLocked = true;

            long reservedCount = broadcastRepository.countBySellerIdAndStatus(sellerId, BroadcastStatus.RESERVED);
            if (reservedCount >= 7) {
                throw new BusinessException(ErrorCode.RESERVATION_LIMIT_EXCEEDED);
            }

            ensureSlotCapacityForReservation(request.getScheduledAt());

            Seller seller = sellerRepository.findById(sellerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));
            TagCategory category = tagCategoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

            Broadcast broadcast = Broadcast.builder()
                    .seller(seller)
                    .tagCategory(category)
                    .broadcastTitle(request.getTitle())
                    .broadcastNotice(request.getNotice())
                    .scheduledAt(request.getScheduledAt())
                    .broadcastLayout(request.getBroadcastLayout())
                    .broadcastThumbUrl(request.getThumbnailUrl())
                    .broadcastWaitUrl(request.getWaitScreenUrl())
                    .status(BroadcastStatus.RESERVED)
                    .build();

            Broadcast saved = broadcastRepository.save(broadcast);
            saveBroadcastProducts(sellerId, saved, request.getProducts());
            saveQcards(saved, request.getQcards());
            ensureSlotCapacityAfterReservation(request.getScheduledAt());

            log.info("방송 생성 완료: id={}", saved.getBroadcastId());
            return saved.getBroadcastId();
        } finally {
            if (dbSlotLocked) {
                releaseDbSlotLock(dbSlotLockKey);
            }
            redisService.releaseLock(slotLockKey);
            redisService.releaseLock(lockKey);
        }
    }

    @Transactional
    public Long updateBroadcast(Long sellerId, Long broadcastId, BroadcastUpdateRequest request) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

        if (!broadcast.getSeller().getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }

        TagCategory category = tagCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        if (broadcast.getStatus() == BroadcastStatus.RESERVED || broadcast.getStatus() == BroadcastStatus.CANCELED) {
            if (broadcast.getStatus() == BroadcastStatus.CANCELED) {
                broadcast.changeStatus(BroadcastStatus.RESERVED);
            }
            LocalDateTime nextScheduledAt = request.getScheduledAt();
            LocalDateTime currentScheduledAt = broadcast.getScheduledAt();
            if (nextScheduledAt != null && (currentScheduledAt == null || !currentScheduledAt.equals(nextScheduledAt))) {
                String slotLockKey = "lock:broadcast_slot:" + nextScheduledAt.toString();
                String dbSlotLockKey = buildDbSlotLockKey(nextScheduledAt);
                boolean dbSlotLocked = false;
                if (!Boolean.TRUE.equals(redisService.acquireLock(slotLockKey, 3000))) {
                    throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
                }
                try {
                    if (!acquireDbSlotLock(dbSlotLockKey, 3)) {
                        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
                    }
                    dbSlotLocked = true;

                    ensureSlotCapacityForReservation(nextScheduledAt);
                    broadcast.updateBroadcastInfo(
                            category, request.getTitle(), request.getNotice(),
                            request.getScheduledAt(), request.getThumbnailUrl(),
                            request.getWaitScreenUrl(), request.getBroadcastLayout()
                    );
                    updateBroadcastProducts(sellerId, broadcast, request.getProducts());
                    updateQcards(broadcast, request.getQcards());
                    ensureSlotCapacityAfterReservation(nextScheduledAt);
                } finally {
                    if (dbSlotLocked) {
                        releaseDbSlotLock(dbSlotLockKey);
                    }
                    redisService.releaseLock(slotLockKey);
                }
            } else {
                broadcast.updateBroadcastInfo(
                        category, request.getTitle(), request.getNotice(),
                        request.getScheduledAt(), request.getThumbnailUrl(),
                        request.getWaitScreenUrl(), request.getBroadcastLayout()
                );
                updateBroadcastProducts(sellerId, broadcast, request.getProducts());
                updateQcards(broadcast, request.getQcards());
            }
        } else {
            broadcast.updateLiveBroadcastInfo(
                    category, request.getTitle(), request.getNotice(),
                    request.getThumbnailUrl(), request.getWaitScreenUrl()
            );
        }

        sseService.notifyBroadcastUpdate(broadcastId);

        return broadcast.getBroadcastId();
    }

    @Transactional
    public void cancelBroadcast(Long sellerId, Long broadcastId) {
        String lockKey = "lock:broadcast_transition:" + broadcastId;
        if (!Boolean.TRUE.equals(redisService.acquireLock(lockKey, 3000))) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        try {
            Broadcast broadcast = broadcastRepository.findById(broadcastId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

            if (!broadcast.getSeller().getSellerId().equals(sellerId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
            }

            if (broadcast.getStatus() != BroadcastStatus.RESERVED && broadcast.getStatus() != BroadcastStatus.CANCELED) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }

            validateTransition(broadcast.getStatus(), BroadcastStatus.DELETED);
            broadcast.deleteBroadcast();
            log.info("諛⑹넚 痍⑥냼 泥섎━ ?꾨즺: id={}, status={}", broadcastId, broadcast.getStatus());
            sseService.notifyBroadcastUpdate(broadcastId, "BROADCAST_CANCELED", "deleted");
        } finally {
            redisService.releaseLock(lockKey);
        }
    }

    @Transactional
    public void unpinProduct(Long sellerId, Long broadcastId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));
        if (!broadcast.getSeller().getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }
        broadcastProductRepository.resetPinByBroadcastId(broadcastId);
        sseService.notifyBroadcastUpdate(broadcastId, "PRODUCT_UNPINNED", "unpin");
    }

    @Transactional(readOnly = true)
    public List<ProductSelectResponse> getSellerProducts(Long sellerId, String keyword) {
        var productTable = table(name("product")).as("p");
        var productId = field(name("p", "product_id"), Long.class);
        var productName = field(name("p", "product_name"), String.class);
        var price = field(name("p", "price"), Integer.class);
        var stockQty = field(name("p", "stock_qty"), Integer.class);
        var safetyStock = field(name("p", "safety_stock"), Integer.class);
        var sellerField = field(name("p", "seller_id"), Long.class);
        var statusField = field(name("p", "status"), String.class);
        var deletedAt = field(name("p", "deleted_at"), LocalDateTime.class);

        var broadcastTable = table(name("broadcast")).as("b");
        var broadcastId = field(name("b", "broadcast_id"), Long.class);
        var broadcastStatus = field(name("b", "status"), String.class);

        var broadcastProductTable = table(name("broadcast_product")).as("bp");
        var bpProductId = field(name("bp", "product_id"), Long.class);
        var bpQuantity = field(name("bp", "bp_quantity"), Integer.class);
        var bpStatus = field(name("bp", "status"), String.class);
        var bpBroadcastId = field(name("bp", "broadcast_id"), Long.class);

        var productImageTable = table(name("product_image")).as("pi");
        var imageProductId = field(name("pi", "product_id"), Long.class);
        var imageUrl = field(name("pi", "product_image_url"), String.class);
        var imageType = field(name("pi", "image_type"), String.class);
        var imageSlotIndex = field(name("pi", "slot_index"), Integer.class);
        var imageDeletedAt = field(name("pi", "deleted_at"), LocalDateTime.class);

        List<String> statuses = List.of(Status.ON_SALE.name(), Status.READY.name(), Status.LIMITED_SALE.name());
        List<String> reservedStatuses = List.of(
                BroadcastStatus.RESERVED.name(),
                BroadcastStatus.READY.name(),
                BroadcastStatus.ON_AIR.name(),
                BroadcastStatus.ENDED.name()
        );

        var reservedQuantityField = org.jooq.impl.DSL.coalesce(org.jooq.impl.DSL.sum(bpQuantity), 0).as("reserved_qty");
        var reservedSubquery = dsl.select(bpProductId, reservedQuantityField)
                .from(broadcastProductTable)
                .join(broadcastTable).on(bpBroadcastId.eq(broadcastId))
                .where(broadcastStatus.in(reservedStatuses).and(bpStatus.ne("DELETED")))
                .groupBy(bpProductId)
                .asTable("reserved");
        var reservedProductId = field(name("reserved", "product_id"), Long.class);
        var reservedQty = field(name("reserved", "reserved_qty"), Integer.class);
        var availableQty = stockQty.cast(org.jooq.impl.SQLDataType.BIGINT)
                .sub(safetyStock.cast(org.jooq.impl.SQLDataType.BIGINT))
                .sub(org.jooq.impl.DSL.coalesce(reservedQty, 0).cast(org.jooq.impl.SQLDataType.BIGINT));
        var liveSubquery = dsl.select(
                        bpProductId.as("product_id"),
                        org.jooq.impl.DSL.max(broadcastId).as("broadcast_id")
                )
                .from(broadcastProductTable)
                .join(broadcastTable).on(bpBroadcastId.eq(broadcastId))
                .where(broadcastStatus.eq(BroadcastStatus.ON_AIR.name()).and(bpStatus.ne("DELETED")))
                .groupBy(bpProductId)
                .asTable("live");
        var liveProductId = field(name("live", "product_id"), Long.class);
        var liveBroadcastId = field(name("live", "broadcast_id"), Long.class);

        var condition = sellerField.eq(sellerId)
                .and(statusField.in(statuses))
                .and(availableQty.gt(0L))
                .and(deletedAt.isNull());
        if (keyword != null && !keyword.isBlank()) {
            condition = condition.and(productName.containsIgnoreCase(keyword));
        }

        return dsl.select(
                        productId,
                        productName,
                        price,
                        stockQty,
                        safetyStock,
                        org.jooq.impl.DSL.coalesce(reservedQty, 0).as("reserved_qty"),
                        liveBroadcastId,
                        imageUrl
                )
                .from(productTable)
                .leftJoin(reservedSubquery).on(productId.eq(reservedProductId))
                .leftJoin(liveSubquery).on(productId.eq(liveProductId))
                .leftJoin(productImageTable).on(
                        productId.eq(imageProductId)
                                .and(imageType.eq(ProductImage.ImageType.THUMBNAIL.name()))
                                .and(imageSlotIndex.eq(0))
                                .and(imageDeletedAt.isNull())
                )
                .where(condition)
                .orderBy(productId.asc())
                .fetch(record -> {
                    Long currentBroadcastId = record.get(liveBroadcastId);
                    Integer resolvedPrice = record.get(price);
                    if (currentBroadcastId != null) {
                        Integer originalPrice = redisService.getOriginalPrice(currentBroadcastId, record.get(productId));
                        if (originalPrice != null) {
                            resolvedPrice = originalPrice;
                        }
                    }
                    return ProductSelectResponse.builder()
                            .productId(record.get(productId))
                            .productName(record.get(productName))
                            .price(resolvedPrice)
                            .stockQty(record.get(stockQty))
                            .safetyStock(record.get(safetyStock))
                            .reservedBroadcastQty(record.get("reserved_qty", Integer.class))
                            .imageUrl(record.get(imageUrl))
                            .build();
                });
    }

    @Transactional(readOnly = true)
    public List<ReservationSlotResponse> getReservableSlots(LocalDate date) {
        LocalDateTime start = date.atTime(10, 0);
        LocalDateTime end = date.atTime(23, 0);

        var broadcastTable = table(name("broadcast")).as("b");
        var scheduledField = field(name("b", "scheduled_at"), LocalDateTime.class);
        var statusField = field(name("b", "status"), String.class);

        Map<LocalDateTime, Long> counts = dsl.select(scheduledField, org.jooq.impl.DSL.count())
                .from(broadcastTable)
                .where(
                        scheduledField.between(start, end),
                        statusField.in(BroadcastStatus.RESERVED.name(), BroadcastStatus.READY.name())
                )
                .groupBy(scheduledField)
                .fetchMap(scheduledField, record -> record.get(org.jooq.impl.DSL.count(), Long.class));

        List<ReservationSlotResponse> slots = new java.util.ArrayList<>();
        LocalDateTime cursor = start;
        while (cursor.isBefore(end)) {
            int used = counts.getOrDefault(cursor, 0L).intValue();
            int remaining = Math.max(0, 3 - used);
            if (remaining > 0) {
                slots.add(ReservationSlotResponse.builder()
                        .slotDateTime(cursor)
                        .remainingCapacity(remaining)
                        .selectable(true)
                        .build());
            }
            cursor = cursor.plusMinutes(30);
        }
        return slots;
    }

    @Transactional
    public void saveMediaConfig(Long sellerId, Long broadcastId, MediaConfigRequest request) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

        if (!broadcast.getSeller().getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }

        redisService.saveMediaConfig(
                broadcastId,
                sellerId,
                request.getCameraId(),
                request.getMicrophoneId(),
                request.getCameraOn(),
                request.getMicrophoneOn(),
                request.getVolume()
        );
    }

    @Transactional(readOnly = true)
    public MediaConfigResponse getMediaConfig(Long sellerId, Long broadcastId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

        if (!broadcast.getSeller().getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }

        List<Object> values = redisService.getMediaConfig(broadcastId, sellerId);
        if (values == null || values.stream().allMatch(java.util.Objects::isNull)) {
            return MediaConfigResponse.builder()
                    .cameraId("")
                    .microphoneId("")
                    .cameraOn(true)
                    .microphoneOn(true)
                    .volume(50)
                    .build();
        }

        return MediaConfigResponse.builder()
                .cameraId(values.get(0) != null ? values.get(0).toString() : "")
                .microphoneId(values.get(1) != null ? values.get(1).toString() : "")
                .cameraOn(values.get(2) == null || Boolean.parseBoolean(values.get(2).toString()))
                .microphoneOn(values.get(3) == null || Boolean.parseBoolean(values.get(3).toString()))
                .volume(values.get(4) != null ? Integer.parseInt(values.get(4).toString()) : 50)
                .build();
    }

    @Transactional(readOnly = true)
    public BroadcastResponse getBroadcastDetail(Long sellerId, Long broadcastId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

        if (!broadcast.getSeller().getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }

        return createBroadcastResponse(broadcast);
    }

    @Transactional(readOnly = true)
    public BroadcastResponse getPublicBroadcastDetail(Long broadcastId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

        if (broadcast.getStatus() == BroadcastStatus.DELETED || broadcast.getStatus() == BroadcastStatus.CANCELED) {
            throw new BusinessException(ErrorCode.BROADCAST_NOT_FOUND);
        }

        if (broadcast.getStatus() == BroadcastStatus.VOD) {
            Vod vod = vodRepository.findByBroadcast(broadcast)
                    .orElseThrow(() -> new BusinessException(ErrorCode.VOD_NOT_FOUND));
            if (vod.getStatus() != VodStatus.PUBLIC) {
                throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
            }
        }

        return createBroadcastResponse(broadcast);
    }

    @Transactional(readOnly = true)
    public BroadcastResponse getAdminBroadcastDetail(Long broadcastId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

        return createBroadcastResponse(broadcast);
    }

    @Transactional(readOnly = true)
    public Object getPublicBroadcasts(BroadcastSearch condition, Pageable pageable) {
        if ("ALL".equalsIgnoreCase(condition.getTab())) {
            return getOverview(null, false);
        }
        Slice<BroadcastListResponse> list = broadcastRepository.searchBroadcasts(null, condition, pageable, false);
        injectLiveStats(list.getContent());
        return list;
    }

    @Transactional(readOnly = true)
    public Object getSellerBroadcasts(Long sellerId, BroadcastSearch condition, Pageable pageable) {
        if ("ALL".equalsIgnoreCase(condition.getTab())) {
            return getOverview(sellerId, true);
        }
        Slice<BroadcastListResponse> list = broadcastRepository.searchBroadcasts(sellerId, condition, pageable, true);
        injectLiveStats(list.getContent());
        return list;
    }

    @Transactional(readOnly = true)
    public Object getAdminBroadcasts(BroadcastSearch condition, Pageable pageable) {
        Slice<BroadcastListResponse> list = broadcastRepository.searchBroadcasts(null, condition, pageable, true);
        injectLiveStats(list.getContent());
        return list;
    }

    @Transactional
    public String startBroadcast(Long sellerId, Long broadcastId) {
        String lockKey = "lock:broadcast_transition:" + broadcastId;
        if (!Boolean.TRUE.equals(redisService.acquireLock(lockKey, 3000))) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        try {
            Broadcast broadcast = broadcastRepository.findById(broadcastId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

            if (!broadcast.getSeller().getSellerId().equals(sellerId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
            }

            if (broadcast.getStatus() != BroadcastStatus.ON_AIR
                    && broadcast.getScheduledAt() != null
                    && LocalDateTime.now().isBefore(broadcast.getScheduledAt())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }

            if (broadcast.getStatus() == BroadcastStatus.ON_AIR) {
                try {
                    Map<String, Object> params = Map.of("role", "HOST", "sellerId", sellerId);
                    return openViduService.createToken(broadcastId, params);
                } catch (Exception e) {
                    throw new BusinessException(ErrorCode.OPENVIDU_ERROR);
                }
            }

            String sessionId;
            try {
                sessionId = openViduService.createSession(broadcastId);
            } catch (OpenViduJavaClientException | OpenViduHttpException e) {
                log.error("OpenVidu session creation failed: broadcastId={}, message={}", broadcastId, e.getMessage());
                throw new BusinessException(ErrorCode.OPENVIDU_ERROR);
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.OPENVIDU_ERROR);
            }

            validateTransition(broadcast.getStatus(), BroadcastStatus.ON_AIR);
            broadcast.startBroadcast(sessionId);
            applyLiveProductPrice(broadcast);
            sseService.notifyBroadcastUpdate(broadcastId, "BROADCAST_STARTED", "started");

            try {
                Map<String, Object> params = Map.of("role", "HOST", "sellerId", sellerId);
                String token = openViduService.createToken(broadcastId, params);
                return token;
            } catch (OpenViduJavaClientException | OpenViduHttpException e) {
                log.error("OpenVidu error during broadcast start: broadcastId={}, message={}", broadcastId, e.getMessage());
                throw new BusinessException(ErrorCode.OPENVIDU_ERROR);
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.OPENVIDU_ERROR);
            }
        } finally {
            redisService.releaseLock(lockKey);
        }
    }

    @Transactional
    public String joinBroadcast(Long broadcastId, String viewerId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

        if (broadcast.getStatus() == BroadcastStatus.STOPPED) {
            throw new BusinessException(ErrorCode.BROADCAST_STOPPED_BY_ADMIN);
        }
        if (!isJoinableGroup(broadcast.getStatus())) {
            throw new BusinessException(ErrorCode.BROADCAST_NOT_ON_AIR);
        }

        Long memberId = resolveMemberId(viewerId);
        if (memberId != null && isViewerSanctioned(broadcastId, memberId)) {
            throw new BusinessException(ErrorCode.BROADCAST_ALREADY_SANCTIONED);
        }

        String uuid = (viewerId != null) ? viewerId : UUID.randomUUID().toString();
        redisService.enterLiveRoom(broadcastId, uuid);
        if (broadcast.getStatus() == BroadcastStatus.ON_AIR) {
            redisService.updatePeakViewers(broadcastId);
        }
        recordViewEnter(broadcast, viewerId);

        try {
            Map<String, Object> params = Map.of("role", "SUBSCRIBER");
            return openViduService.createToken(broadcastId, params);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPENVIDU_ERROR);
        }
    }

    @Transactional
    public void leaveBroadcast(Long broadcastId, String viewerId) {
        if (viewerId == null || viewerId.isBlank()) {
            return;
        }
        if (!broadcastRepository.existsById(broadcastId)) {
            return;
        }
        redisService.exitLiveRoom(broadcastId, viewerId);
        broadcastRepository.findById(broadcastId)
                .ifPresent(broadcast -> recordViewExit(broadcast, viewerId));
    }

    @Transactional
    public void endBroadcast(Long sellerId, Long broadcastId) {
        String lockKey = "lock:broadcast_transition:" + broadcastId;
        if (!Boolean.TRUE.equals(redisService.acquireLock(lockKey, 3000))) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        try {
            Broadcast broadcast = broadcastRepository.findById(broadcastId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

            if (!broadcast.getSeller().getSellerId().equals(sellerId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
            }

            validateTransition(broadcast.getStatus(), BroadcastStatus.ENDED);
            broadcast.endBroadcast();
            closeActiveViewHistories(broadcast);
            try {
                openViduService.stopRecording(broadcastId);
            } catch (Exception e) {
                log.warn("Failed to stop OpenVidu recording: broadcastId={}, message={}", broadcastId, e.getMessage());
            }
            openViduService.closeSession(broadcastId);
            sseService.notifyBroadcastUpdate(broadcastId, "BROADCAST_ENDED", "ended");
        } finally {
            redisService.releaseLock(lockKey);
        }
    }

    public void startRecording(Long sellerId, Long broadcastId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

        if (!broadcast.getSeller().getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }

        if (broadcast.getStatus() != BroadcastStatus.ON_AIR) {
            throw new BusinessException(ErrorCode.BROADCAST_NOT_ON_AIR);
        }

        try {
            openViduService.startRecording(broadcastId);
        } catch (OpenViduHttpException e) {
            int status = e.getStatus();
            if (isRetriableRecordingStartStatus(status)) {
                scheduleRecordingStartRetry(broadcastId, "publisher_stream_created", status);
                log.warn("OpenVidu recording start deferred by retry: broadcastId={}, status={}, message={}",
                        broadcastId, status, e.getMessage());
            } else if (status == 409) {
                log.info("OpenVidu recording already started: broadcastId={}", broadcastId);
            } else if (status == 501) {
                log.error("OpenVidu recording module appears disabled: broadcastId={}, status={}, message={}",
                        broadcastId, status, e.getMessage());
                throw new BusinessException(ErrorCode.OPENVIDU_ERROR);
            } else {
                log.error("OpenVidu recording start failed: broadcastId={}, status={}, message={}",
                        broadcastId, status, e.getMessage());
                throw new BusinessException(ErrorCode.OPENVIDU_ERROR);
            }
        } catch (OpenViduJavaClientException e) {
            scheduleRecordingStartRetry(broadcastId, "publisher_stream_created", 0);
            log.warn("OpenVidu recording start deferred by retry: broadcastId={}, status=0, message={}",
                    broadcastId, e.getMessage());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPENVIDU_ERROR);
        }
    }

    @Transactional
    public void pinProduct(Long sellerId, Long broadcastId, Long bpId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));
        if (!broadcast.getSeller().getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }

        broadcastProductRepository.resetPinByBroadcastId(broadcastId);

        BroadcastProduct bp = broadcastProductRepository.findById(bpId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!bp.getBroadcast().getBroadcastId().equals(broadcastId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        bp.setPinned(true);

        sseService.notifyBroadcastUpdate(broadcastId, "PRODUCT_PINNED", bp.getProduct().getId());
    }

    @Transactional
    public String updateVodVisibility(Long sellerId, Long broadcastId, String status) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));
        if (!broadcast.getSeller().getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }
        Vod vod = vodRepository.findByBroadcast(broadcast)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOD_NOT_FOUND));
        VodStatus nextStatus = resolveVisibilityStatus(status);
        if (vod.isVodAdminLock() && nextStatus == VodStatus.PUBLIC) {
            throw new BusinessException(ErrorCode.VOD_ADMIN_LOCKED);
        }
        if (broadcast.getStatus() == BroadcastStatus.STOPPED && nextStatus == VodStatus.PUBLIC) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        vod.changeStatus(nextStatus);
        return nextStatus.name();
    }

    @Transactional
    public void deleteVod(Long sellerId, Long broadcastId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));
        if (!broadcast.getSeller().getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }
        Vod vod = vodRepository.findByBroadcast(broadcast)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOD_NOT_FOUND));
        if (vod.getVodUrl() != null && !vod.getVodUrl().isBlank()) {
            s3Service.deleteObjectByUrl(vod.getVodUrl());
        }
        vodStatsService.flushVodStats(broadcastId);
        redisService.deleteVodKeys(broadcastId);
        vod.markDeleted();
    }

    @Transactional
    public String updateAdminVodVisibility(Long broadcastId, String status) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));
        Vod vod = vodRepository.findByBroadcast(broadcast)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOD_NOT_FOUND));
        VodStatus nextStatus = resolveVisibilityStatus(status);
        vod.changeStatus(nextStatus);
        vod.setAdminLock(nextStatus == VodStatus.PRIVATE);
        if (broadcast.getStatus() == BroadcastStatus.STOPPED && nextStatus == VodStatus.PUBLIC) {
            validateTransition(broadcast.getStatus(), BroadcastStatus.VOD);
            broadcast.changeStatus(BroadcastStatus.VOD);
            restoreOriginalProductPrice(broadcast);
            redisService.persistVodReactionKeys(broadcastId);
        }
        return nextStatus.name();
    }

    @Transactional
    public void deleteAdminVod(Long broadcastId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));
        Vod vod = vodRepository.findByBroadcast(broadcast)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOD_NOT_FOUND));
        if (vod.getVodUrl() != null && !vod.getVodUrl().isBlank()) {
            s3Service.deleteObjectByUrl(vod.getVodUrl());
        }
        vodStatsService.flushVodStats(broadcastId);
        redisService.deleteVodKeys(broadcastId);
        vod.markDeleted();
    }

    private VodStatus resolveVisibilityStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        VodStatus nextStatus;
        try {
            nextStatus = VodStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (nextStatus == VodStatus.DELETED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return nextStatus;
    }

    @EventListener
    public void handleConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String bId = accessor.getFirstNativeHeader("broadcastId");
        String vId = accessor.getFirstNativeHeader("X-Viewer-Id");
        if (bId != null && vId != null) {
            Long broadcastId = parseBroadcastId(bId);
            if (broadcastId == null) {
                log.warn("Invalid broadcastId on connect: {}", bId);
                return;
            }
            redisService.enterLiveRoom(broadcastId, vId);
            broadcastRepository.findById(broadcastId)
                    .filter(broadcast -> broadcast.getStatus() == BroadcastStatus.ON_AIR)
                    .ifPresent(broadcast -> {
                        redisService.updatePeakViewers(broadcastId);
                        recordViewEnter(broadcast, vId);
                    });
            Map<String, Object> attrs = accessor.getSessionAttributes();
            if (attrs != null) {
                attrs.put("broadcastId", bId);
                attrs.put("viewerId", vId);
            }
        }
    }

    @EventListener
    public void handleDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null && attrs.containsKey("broadcastId")) {
            Long broadcastId = parseBroadcastId(String.valueOf(attrs.get("broadcastId")));
            if (broadcastId == null) {
                log.warn("Invalid broadcastId on disconnect: {}", attrs.get("broadcastId"));
                return;
            }
            String viewerId = (String) attrs.get("viewerId");
            redisService.exitLiveRoom(broadcastId, viewerId);
            broadcastRepository.findById(broadcastId)
                    .ifPresent(broadcast -> recordViewExit(broadcast, viewerId));
        }
    }

    public BroadcastLikeResponse likeBroadcast(Long broadcastId, Long memberId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

        if (broadcast.getStatus() == BroadcastStatus.VOD) {
            boolean liked = redisService.toggleVodLike(broadcastId, memberId);
            int baseLikes = broadcastResultRepository.findById(broadcastId)
                    .map(BroadcastResult::getTotalLikes)
                    .orElse(0);
            int pendingDelta = redisService.getVodLikeDelta(broadcastId);
            int likeCount = Math.max(0, baseLikes + pendingDelta);
            return BroadcastLikeResponse.builder()
                    .liked(liked)
                    .likeCount(likeCount)
                    .build();
        }

        boolean liked = redisService.toggleLike(broadcastId, memberId);
        int likeCount = redisService.getLikeCount(broadcastId);
        return BroadcastLikeResponse.builder()
                .liked(liked)
                .likeCount(likeCount)
                .build();
    }

    @Transactional(readOnly = true)
    public BroadcastLikeResponse getBroadcastLikeStatus(Long broadcastId, Long memberId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

        boolean liked = redisService.isMemberLiked(broadcastId, memberId);
        if (broadcast.getStatus() == BroadcastStatus.VOD) {
            int baseLikes = broadcastResultRepository.findById(broadcastId)
                    .map(BroadcastResult::getTotalLikes)
                    .orElse(0);
            int pendingDelta = redisService.getVodLikeDelta(broadcastId);
            int likeCount = Math.max(0, baseLikes + pendingDelta);
            return BroadcastLikeResponse.builder()
                    .liked(liked)
                    .likeCount(likeCount)
                    .build();
        }

        int likeCount = redisService.getLikeCount(broadcastId);
        return BroadcastLikeResponse.builder()
                .liked(liked)
                .likeCount(likeCount)
                .build();
    }

    @Transactional
    public void processVod(OpenViduRecordingWebhook payload) {
        Long broadcastId = parseBroadcastIdFromSession(payload.getSessionId());
        if (broadcastId == null) {
            log.warn("Invalid OpenVidu sessionId for VOD processing: {}", payload.getSessionId());
            return;
        }

        if (payload.getId() == null || payload.getId().isBlank()) {
            log.warn("Missing recording id for VOD processing: sessionId={}", payload.getSessionId());
            return;
        }
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

        if (vodRepository.findByBroadcast(broadcast).isPresent()) {
            log.info("VOD already processed: broadcastId={}", broadcastId);
            redisService.clearRecordingRetry(broadcastId);
            return;
        }

        String recordingId = payload.getId();
        String s3Key = "seller_" + broadcast.getSeller().getSellerId() + "/vods/" + recordingId + ".mp4";
        String s3Url = payload.getUrl() != null ? payload.getUrl() : "";

        s3Url = uploadVodWithRetry(recordingId, s3Key, s3Url);

        boolean isStopped = broadcast.getStatus() == BroadcastStatus.STOPPED;
        boolean hasVodUrl = s3Url != null && !s3Url.isBlank();
        VodStatus status = (isStopped || !hasVodUrl) ? VodStatus.PRIVATE : VodStatus.PUBLIC;
        if (isStopped && hasVodUrl) {
            downloadVodToAdminLocal(s3Url, broadcastId, recordingId);
        }

        long vodSize = payload.getSize() != null ? payload.getSize() : 0L;
        if (vodSize == 0L && s3Url != null && !s3Url.isBlank()) {
            vodSize = s3Service.getObjectSize(s3Url);
        }

        Vod vod = Vod.builder()
                .broadcast(broadcast)
                .vodUrl(s3Url)
                .vodSize(vodSize)
                .vodDuration(payload.getDuration() != null ? payload.getDuration().intValue() : 0)
                .status(status)
                .vodReportCount(0)
                .vodAdminLock(isStopped)
                .build();
        vodRepository.save(vod);

        int uv = redisService.getTotalUniqueViewerCount(broadcastId);
        int likes = redisService.getLikeCount(broadcastId);
        int reports = redisService.getReportCount(broadcastId);
        int mv = redisService.getMaxViewers(broadcastId);
        LocalDateTime peak = redisService.getMaxViewersTime(broadcastId);
        Double avg = viewHistoryRepository.getAverageWatchTime(broadcastId);
        SalesSummary salesSummary = fetchBroadcastSalesSummary(broadcast);
        int totalChats = countBroadcastChats(broadcastId);

        BroadcastResult result = broadcastResultRepository.findById(broadcastId).orElse(null);
        LocalDateTime peakTime = resolveMaxViewsAt(broadcast, peak);
        if (result == null) {
            result = BroadcastResult.builder()
                    .broadcast(broadcast)
                    .totalViews(uv)
                    .totalLikes(likes)
                    .totalReports(reports)
                    .avgWatchTime(avg != null ? avg.intValue() : 0)
                    .maxViews(mv)
                    .pickViewsAt(peakTime)
                    .totalChats(totalChats)
                    .totalSales(salesSummary.totalSales())
                    .build();
        } else {
            result.updateFinalStats(
                    uv,
                    likes,
                    reports,
                    avg != null ? avg.intValue() : 0,
                    mv,
                    peakTime,
                    totalChats,
                    salesSummary.totalSales()
            );
        }
        broadcastResultRepository.save(result);

        redisService.persistVodReactionKeys(broadcastId);
        redisService.deleteBroadcastRuntimeKeys(broadcastId);
        redisService.clearRecordingRetry(broadcastId);
    }

    private void downloadVodToAdminLocal(String vodUrl, Long broadcastId, String recordingId) {
        try {
            Path baseDir = Paths.get(adminVodDownloadDir);
            Files.createDirectories(baseDir);
            String safeRecordingId = recordingId != null && !recordingId.isBlank() ? recordingId : UUID.randomUUID().toString();
            Path target = baseDir.resolve("broadcast-" + broadcastId + "-" + safeRecordingId + ".mp4");
            try (InputStream inputStream = s3Service.getObjectStream(vodUrl, null, null)) {
                Files.copy(inputStream, target);
            }
            log.info("관리자 로컬에 VOD 저장 완료: {}", target.toAbsolutePath());
        } catch (Exception e) {
            log.error("관리자 로컬 VOD 저장 실패: {}", vodUrl, e);
        }
    }

    private String uploadVodWithRetry(String recordingId, String s3Key, String fallbackUrl) {
        int attempts = 0;
        while (attempts < 3) {
            attempts++;
            try {
                disableSslVerification();

                String videoUrl = openViduUrl.replaceAll("/$", "") +
                        "/openvidu/recordings/" + recordingId + "/" + recordingId + ".mp4";

                URL url = new URL(videoUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                String auth = "OPENVIDUAPP:" + openViduSecret;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                conn.setRequestProperty("Authorization", "Basic " + encodedAuth);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (InputStream inputStream = conn.getInputStream()) {
                        long contentLength = conn.getContentLengthLong();
                        String s3Url = s3Service.uploadVodStream(inputStream, s3Key, contentLength);
                        log.info("VOD Upload Success: {}", s3Url);
                        try {
                            openViduService.deleteRecording(recordingId);
                        } catch (OpenViduJavaClientException | OpenViduHttpException e) {
                            log.warn("Failed to delete OpenVidu recording after upload: recordingId={}, reason={}",
                                    recordingId, e.getMessage());
                        }
                        return s3Url;
                    }
                } else {
                    log.error("Failed to fetch recording from OpenVidu: {}", responseCode);
                }
            } catch (Exception e) {
                log.error("VOD Processing Error (attempt {}): {}", attempts, e.getMessage());
            }

            try {
                Thread.sleep(1000L * attempts);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return fallbackUrl;
    }

    @Transactional(readOnly = true)
    public BroadcastStatsResponse getBroadcastStats(Long broadcastId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

        if (broadcast.getStatus() == BroadcastStatus.DELETED || broadcast.getStatus() == BroadcastStatus.CANCELED) {
            throw new BusinessException(ErrorCode.BROADCAST_NOT_FOUND);
        }

        int views = 0;
        int likes = 0;
        int reports = 0;

        if (shouldUseRealtimeStats(broadcast.getStatus())) {
            views = redisService.getRealtimeViewerCount(broadcastId);
            likes = redisService.getLikeCount(broadcastId);
            reports = redisService.getReportCount(broadcastId);
        } else {
            BroadcastResult result = broadcastResultRepository.findById(broadcastId).orElse(null);
            if (result != null) {
                views = result.getTotalViews();
                likes = result.getTotalLikes();
                reports = result.getTotalReports();
            }
        }

        return BroadcastStatsResponse.builder()
                .viewerCount(views)
                .likeCount(likes)
                .reportCount(reports)
                .build();
    }

    @Transactional
    public List<BroadcastProductResponse> getBroadcastProducts(Long broadcastId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));
        List<BroadcastProduct> products = broadcastProductRepository.findAllWithProductByBroadcastId(broadcastId);
        Map<Long, Integer> remainingQuantities = calculateRemainingQuantities(broadcast, products);
        Set<Long> soldOutProductIds = new LinkedHashSet<>();
        boolean pinReleased = false;
        for (BroadcastProduct bp : products) {
            Integer remaining = remainingQuantities.get(bp.getProduct().getId());
            boolean soldOut = bp.markSoldOutIfNeeded(remaining);
            if (remaining == null || remaining <= 0) {
                restoreOriginalPriceIfNeeded(broadcast, bp);
            }
            if (soldOut) {
                soldOutProductIds.add(bp.getProduct().getId());
            }
            if ((remaining == null || remaining <= 0) && bp.isPinned()) {
                bp.setPinned(false);
                pinReleased = true;
            }
        }

        if (!soldOutProductIds.isEmpty()) {
            sseService.notifyBroadcastUpdate(broadcastId, "PRODUCT_SOLD_OUT", soldOutProductIds);
        }
        if (pinReleased) {
            sseService.notifyBroadcastUpdate(broadcastId, "PRODUCT_UNPINNED", "soldout");
        }

        List<Long> productIds = products.stream()
                .map(bp -> bp.getProduct().getId())
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> thumbnailUrls = productIds.isEmpty()
                ? Collections.emptyMap()
                : productImageRepository
                .findAllByProductIdInAndImageTypeAndSlotIndexAndDeletedAtIsNullOrderByProductIdAscIdAsc(
                        productIds, ImageType.THUMBNAIL, 0
                ).stream()
                .collect(Collectors.toMap(
                        ProductImage::getProductId,
                        ProductImage::getProductImageUrl,
                        (left, right) -> left
                ));

        return products.stream()
                .map(bp -> BroadcastProductResponse.fromEntityWithImageUrl(
                        bp,
                        remainingQuantities.getOrDefault(bp.getProduct().getId(), bp.getBpQuantity()),
                        resolveOriginalPrice(broadcast, bp),
                        thumbnailUrls.get(bp.getProduct().getId())
                ))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean canChat(Long broadcastId, Long memberId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

        if (broadcast.getStatus() == BroadcastStatus.STOPPED) {
            return false;
        }

        if (memberId == null) {
            return true;
        }

        return !isViewerSanctioned(broadcastId, memberId, SanctionType.MUTE, SanctionType.OUT);
    }

    public BroadcastReportResponse reportBroadcast(Long broadcastId, Long memberId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

        if (broadcast.getStatus() == BroadcastStatus.VOD) {
            boolean reported = redisService.reportVod(broadcastId, memberId);
            int baseReports = broadcastResultRepository.findById(broadcastId)
                    .map(BroadcastResult::getTotalReports)
                    .orElse(0);
            int pendingDelta = redisService.getVodReportDelta(broadcastId);
            int reportCount = Math.max(0, baseReports + pendingDelta);
            return BroadcastReportResponse.builder()
                    .reported(reported)
                    .reportCount(reportCount)
                    .build();
        }

        boolean reported = redisService.reportBroadcast(broadcastId, memberId);
        int reportCount = redisService.getReportCount(broadcastId);
        return BroadcastReportResponse.builder()
                .reported(reported)
                .reportCount(reportCount)
                .build();
    }

    @Transactional
    public void recordVodView(Long broadcastId, String viewerId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));
        if (broadcast.getStatus() != BroadcastStatus.VOD) {
            return;
        }
        String resolvedViewerId = (viewerId == null || viewerId.isBlank())
                ? UUID.randomUUID().toString()
                : viewerId;
        redisService.recordVodView(broadcastId, resolvedViewerId);
    }

    @Transactional(readOnly = true)
    public BroadcastResultResponse getBroadcastResult(Long broadcastId, Long requesterId, boolean isAdmin) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

        if (!isAdmin && !broadcast.getSeller().getSellerId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }

        BroadcastResult result = broadcastResultRepository.findById(broadcastId).orElse(null);
        Vod vod = vodRepository.findByBroadcast(broadcast).orElse(null);

        int views = 0;
        int likes = 0;
        int chats = 0;
        int maxV = 0;
        int reports = 0;
        int sanctions;
        long avgTime = 0;
        BigDecimal sales = BigDecimal.ZERO;
        LocalDateTime maxTime = null;
        int pendingViewDelta = 0;

        if (result != null) {
            views = result.getTotalViews();
            likes = result.getTotalLikes();
            chats = result.getTotalChats();
            maxV = result.getMaxViews();
            maxTime = result.getPickViewsAt();
            avgTime = result.getAvgWatchTime();
            reports = result.getTotalReports();
        }
        if (broadcast.getStatus() == BroadcastStatus.VOD) {
            pendingViewDelta = redisService.getVodViewDelta(broadcastId);
        }
        sanctions = sanctionRepository.countByBroadcast(broadcast);

        SalesSummary salesSummary = fetchBroadcastSalesSummary(broadcast);
        sales = salesSummary.totalSales();

        List<BroadcastResultResponse.ProductSalesStat> productStats = broadcastProductRepository
                .findAllWithProductByBroadcastId(broadcastId)
                .stream()
                .map(bp -> {
                    SalesMetric metric = salesSummary.productMetrics().get(bp.getProduct().getId());
                    int effectivePrice = bp.getBpPrice() != null ? bp.getBpPrice() : bp.getProduct().getPrice();
                    return BroadcastResultResponse.ProductSalesStat.builder()
                            .productId(bp.getProduct().getId())
                            .productName(bp.getProduct().getProductName())
                            .salesAmount(metric != null ? metric.salesAmount() : BigDecimal.ZERO)
                            .price(effectivePrice)
                            .salesQuantity(metric != null ? metric.salesQuantity() : 0)
                            .build();
                })
                .collect(Collectors.toList());

        long duration = 0;
        if (broadcast.getStartedAt() != null && broadcast.getEndedAt() != null) {
            duration = java.time.Duration.between(broadcast.getStartedAt(), broadcast.getEndedAt()).toMinutes();
        }

        return BroadcastResultResponse.builder()
                .broadcastId(broadcast.getBroadcastId())
                .title(broadcast.getBroadcastTitle())
                .startAt(broadcast.getStartedAt())
                .endAt(broadcast.getEndedAt())
                .durationMinutes(duration)
                .status(broadcast.getStatus())
                .stoppedReason(broadcast.getBroadcastStoppedReason())
                .totalViews(Math.max(0, views + pendingViewDelta))
                .totalLikes(likes)
                .totalSales(sales)
                .totalChats(chats)
                .maxViewers(maxV)
                .maxViewerTime(maxTime)
                .avgWatchTime(avgTime)
                .reportCount(reports)
                .sanctionCount(sanctions)
                .vodUrl((vod != null && vod.getStatus() != VodStatus.DELETED) ? vod.getVodUrl() : null)
                .vodStatus(vod != null ? vod.getStatus() : null)
                .vodAdminLock(vod != null && vod.isVodAdminLock())
                .isEncoding(vod == null)
                .productStats(productStats)
                .build();
    }

    @Transactional(readOnly = true)
    public StatisticsResponse getStatistics(Long sellerId, String period) {
        var sales = broadcastResultRepository.getSalesChart(sellerId, period);
        var arpu = broadcastResultRepository.getArpuChart(sellerId, period);
        List<StatisticsResponse.BroadcastRank> best;
        List<StatisticsResponse.BroadcastRank> worst;
        List<StatisticsResponse.BroadcastRank> topView;
        List<StatisticsResponse.BroadcastRank> worstView;
        List<StatisticsResponse.ProductRank> bestProducts = List.of();
        List<StatisticsResponse.ProductRank> worstProducts = List.of();

        if (sellerId != null) {
            best = broadcastResultRepository.getRanking(sellerId, period, "SALES", true, 5);
            worst = broadcastResultRepository.getRanking(sellerId, period, "SALES", false, 5);
            topView = broadcastResultRepository.getRanking(sellerId, period, "VIEWS", true, 5);
            worstView = broadcastResultRepository.getRanking(sellerId, period, "VIEWS", false, 5);
        } else {
            best = broadcastResultRepository.getRanking(null, period, "SALES", true, 5);
            worst = broadcastResultRepository.getRanking(null, period, "SALES", false, 5);
            topView = List.of();
            worstView = List.of();
            bestProducts = getProductSalesRanking(period, true, 5);
            worstProducts = getProductSalesRanking(period, false, 5);
        }

        return StatisticsResponse.builder()
                .salesChart(sales)
                .arpuChart(arpu)
                .bestBroadcasts(best)
                .worstBroadcasts(worst)
                .topViewerBroadcasts(topView)
                .worstViewerBroadcasts(worstView)
                .bestProducts(bestProducts)
                .worstProducts(worstProducts)
                .build();
    }

    private List<StatisticsResponse.ProductRank> getProductSalesRanking(String period, boolean desc, int limit) {
        var orderTable = org.jooq.impl.DSL.table(name("order")).as("o");
        var orderItemTable = org.jooq.impl.DSL.table(name("order_item")).as("oi");
        var bpTable = org.jooq.impl.DSL.table(name("broadcast_product")).as("bp");
        var productTable = org.jooq.impl.DSL.table(name("product")).as("p");

        var orderIdField = field(name("o", "order_id"), Long.class);
        var orderStatusField = field(name("o", "status"), String.class);
        var orderPaidAtField = field(name("o", "paid_at"), LocalDateTime.class);
        var orderDeletedAtField = field(name("o", "deleted_at"), LocalDateTime.class);

        var orderItemOrderIdField = field(name("oi", "order_id"), Long.class);
        var orderItemProductIdField = field(name("oi", "product_id"), Long.class);
        var orderItemQuantityField = field(name("oi", "quantity"), Integer.class);
        var orderItemUnitPriceField = field(name("oi", "unit_price"), Integer.class);
        var orderItemDeletedAtField = field(name("oi", "deleted_at"), LocalDateTime.class);

        var bpProductIdField = field(name("bp", "product_id"), Long.class);

        var productIdField = field(name("p", "product_id"), Long.class);
        var productNameField = field(name("p", "product_name"), String.class);

        LocalDateTime startDate = resolveRankingStartDate(period);
        var effectivePrice = orderItemUnitPriceField.cast(BigDecimal.class);
        var salesExpr = org.jooq.impl.DSL.sum(effectivePrice.mul(orderItemQuantityField.cast(BigDecimal.class))).as("sales_amount");

        var orderField = desc ? salesExpr.desc().nullsLast() : salesExpr.asc().nullsLast();
        var bpExists = org.jooq.impl.DSL.exists(
                dsl.selectOne()
                        .from(bpTable)
                        .where(bpProductIdField.eq(orderItemProductIdField))
        );

        return dsl.select(productIdField, productNameField, salesExpr)
                .from(orderItemTable)
                .join(orderTable).on(orderItemOrderIdField.eq(orderIdField))
                .join(productTable).on(orderItemProductIdField.eq(productIdField))
                .where(
                        orderStatusField.in(OrderStatus.PAID.name(), OrderStatus.COMPLETED.name()),
                        orderPaidAtField.isNotNull(),
                        orderPaidAtField.ge(startDate),
                        orderDeletedAtField.isNull(),
                        orderItemDeletedAtField.isNull(),
                        bpExists
                )
                .groupBy(productIdField, productNameField)
                .orderBy(orderField)
                .limit(limit)
                .fetch(record -> StatisticsResponse.ProductRank.builder()
                        .productId(record.get(productIdField))
                        .title(record.get(productNameField))
                        .totalSales(record.get(salesExpr) != null ? record.get(salesExpr) : BigDecimal.ZERO)
                        .build());
    }

    private LocalDateTime resolveRankingStartDate(String period) {
        LocalDateTime now = LocalDateTime.now();
        if ("DAILY".equalsIgnoreCase(period)) {
            return now.withHour(0).withMinute(0).withSecond(0).withNano(0);
        }
        if ("MONTHLY".equalsIgnoreCase(period)) {
            return now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        }
        return now.withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

    private SalesSummary fetchBroadcastSalesSummary(Broadcast broadcast) {
        if (broadcast.getStartedAt() == null) {
            return new SalesSummary(BigDecimal.ZERO, Map.of());
        }
        LocalDateTime startedAt = broadcast.getStartedAt();
        LocalDateTime endedAt = broadcast.getEndedAt() != null ? broadcast.getEndedAt() : LocalDateTime.now();

        var orderTable = org.jooq.impl.DSL.table(name("order")).as("o");
        var orderItemTable = org.jooq.impl.DSL.table(name("order_item")).as("oi");
        var bpTable = org.jooq.impl.DSL.table(name("broadcast_product")).as("bp");

        var orderIdField = field(name("o", "order_id"), Long.class);
        var orderStatusField = field(name("o", "status"), String.class);
        var orderPaidAtField = field(name("o", "paid_at"), LocalDateTime.class);
        var orderDeletedAtField = field(name("o", "deleted_at"), LocalDateTime.class);

        var orderItemOrderIdField = field(name("oi", "order_id"), Long.class);
        var orderItemProductIdField = field(name("oi", "product_id"), Long.class);
        var orderItemQuantityField = field(name("oi", "quantity"), Integer.class);
        var orderItemUnitPriceField = field(name("oi", "unit_price"), Integer.class);
        var orderItemDeletedAtField = field(name("oi", "deleted_at"), LocalDateTime.class);

        var bpBroadcastIdField = field(name("bp", "broadcast_id"), Long.class);
        var bpProductIdField = field(name("bp", "product_id"), Long.class);
        var bpPriceField = field(name("bp", "bp_price"), Integer.class);

        var priceMatchCondition = bpPriceField.isNull().or(orderItemUnitPriceField.eq(bpPriceField));

        var effectivePrice = org.jooq.impl.DSL.coalesce(bpPriceField, orderItemUnitPriceField).cast(BigDecimal.class);
        var salesAmount = org.jooq.impl.DSL.sum(
                effectivePrice.mul(orderItemQuantityField.cast(BigDecimal.class))
        ).as("sales_amount");
        var salesQuantity = org.jooq.impl.DSL.sum(orderItemQuantityField).cast(Integer.class).as("sales_quantity");

        var records = dsl.select(orderItemProductIdField, salesQuantity, salesAmount)
                .from(orderItemTable)
                .join(orderTable).on(orderItemOrderIdField.eq(orderIdField))
                .join(bpTable).on(bpProductIdField.eq(orderItemProductIdField)
                        .and(bpBroadcastIdField.eq(broadcast.getBroadcastId())))
                .where(
                        orderStatusField.in(OrderStatus.PAID.name(), OrderStatus.COMPLETED.name()),
                        orderPaidAtField.isNotNull(),
                        orderPaidAtField.between(startedAt, endedAt),
                        priceMatchCondition,
                        orderDeletedAtField.isNull(),
                        orderItemDeletedAtField.isNull()
                )
                .groupBy(orderItemProductIdField)
                .fetch();

        Map<Long, SalesMetric> metrics = records.stream()
                .collect(Collectors.toMap(
                        record -> record.get(orderItemProductIdField),
                        record -> new SalesMetric(
                                record.get(salesQuantity) != null ? record.get(salesQuantity) : 0,
                                record.get(salesAmount) != null ? record.get(salesAmount) : BigDecimal.ZERO
                        )
                ));

        BigDecimal totalSales = metrics.values().stream()
                .map(SalesMetric::salesAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new SalesSummary(totalSales, metrics);
    }

    @Transactional
    public void refreshBroadcastTotalSales(Long broadcastId) {
        if (broadcastId == null) {
            return;
        }
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElse(null);
        if (broadcast == null) {
            return;
        }
        BroadcastResult result = broadcastResultRepository.findById(broadcastId).orElse(null);
        if (result == null) {
            return;
        }
        SalesSummary salesSummary = fetchBroadcastSalesSummary(broadcast);
        BigDecimal totalSales = salesSummary.totalSales() != null ? salesSummary.totalSales() : BigDecimal.ZERO;
        result.updateTotalSales(totalSales);
        broadcastResultRepository.save(result);
    }

    private int countBroadcastChats(Long broadcastId) {
        return (int) liveChatRepository.countByBroadcastIdAndMsgTypeIn(
                broadcastId,
                List.of(LiveMessageType.TALK, LiveMessageType.PURCHASE, LiveMessageType.NOTICE)
        );
    }

    @Transactional
    public void saveBroadcastResultSnapshot(Broadcast broadcast) {
        if (broadcast == null) {
            return;
        }

        Long broadcastId = broadcast.getBroadcastId();
        int uv = redisService.getTotalUniqueViewerCount(broadcastId);
        int likes = redisService.getLikeCount(broadcastId);
        int reports = redisService.getReportCount(broadcastId);
        int mv = redisService.getMaxViewers(broadcastId);
        LocalDateTime peak = redisService.getMaxViewersTime(broadcastId);
        Double avg = viewHistoryRepository.getAverageWatchTime(broadcastId);
        int totalChats = countBroadcastChats(broadcastId);
        SalesSummary salesSummary = fetchBroadcastSalesSummary(broadcast);

        BroadcastResult result = broadcastResultRepository.findById(broadcastId).orElse(null);
        int avgWatchTime = avg != null ? avg.intValue() : 0;
        BigDecimal totalSales = salesSummary.totalSales() != null ? salesSummary.totalSales() : BigDecimal.ZERO;
        LocalDateTime peakTime = peak;
        int maxViews = mv;
        int totalViews = uv;
        int totalLikes = likes;
        int totalReports = reports;
        int chats = totalChats;

        if (result != null) {
            totalViews = Math.max(result.getTotalViews(), uv);
            totalLikes = Math.max(result.getTotalLikes(), likes);
            totalReports = Math.max(result.getTotalReports(), reports);
            chats = Math.max(result.getTotalChats(), totalChats);
            maxViews = Math.max(result.getMaxViews(), mv);
            if (mv <= result.getMaxViews()) {
                peakTime = result.getPickViewsAt();
            }
            if (peakTime == null) {
                peakTime = peak;
            }
            if (avg == null) {
                avgWatchTime = result.getAvgWatchTime();
            }
            if (result.getTotalSales() != null && result.getTotalSales().compareTo(totalSales) > 0) {
                totalSales = result.getTotalSales();
            }
        }

        peakTime = resolveMaxViewsAt(broadcast, peakTime);
        if (result == null) {
            result = BroadcastResult.builder()
                    .broadcast(broadcast)
                    .totalViews(totalViews)
                    .totalLikes(totalLikes)
                    .totalReports(totalReports)
                    .avgWatchTime(avgWatchTime)
                    .maxViews(maxViews)
                    .pickViewsAt(peakTime)
                    .totalChats(chats)
                    .totalSales(totalSales)
                    .build();
        } else {
            result.updateFinalStats(
                    totalViews,
                    totalLikes,
                    totalReports,
                    avgWatchTime,
                    maxViews,
                    peakTime,
                    chats,
                    totalSales
            );
        }
        broadcastResultRepository.save(result);
    }

    private LocalDateTime resolveMaxViewsAt(Broadcast broadcast, LocalDateTime peakTime) {
        if (peakTime != null) {
            return peakTime;
        }
        if (broadcast.getStartedAt() != null) {
            return broadcast.getStartedAt();
        }
        if (broadcast.getCreatedAt() != null) {
            return broadcast.getCreatedAt();
        }
        return LocalDateTime.now();
    }

    private record SalesSummary(BigDecimal totalSales, Map<Long, SalesMetric> productMetrics) {
    }

    private record SalesMetric(int salesQuantity, BigDecimal salesAmount) {
    }

    private void recordViewEnter(Broadcast broadcast, String viewerId) {
        if (broadcast == null || viewerId == null || viewerId.isBlank()) {
            return;
        }
        if (viewHistoryRepository.findActiveHistory(broadcast.getBroadcastId(), viewerId).isEmpty()) {
            viewHistoryRepository.save(ViewHistory.enter(broadcast, viewerId));
        }
    }

    private void recordViewExit(Broadcast broadcast, String viewerId) {
        if (broadcast == null || viewerId == null || viewerId.isBlank()) {
            return;
        }
        viewHistoryRepository.findActiveHistory(broadcast.getBroadcastId(), viewerId)
                .ifPresent(history -> {
                    history.recordExit();
                    viewHistoryRepository.save(history);
                });
    }

    private void closeActiveViewHistories(Broadcast broadcast) {
        if (broadcast == null) {
            return;
        }
        viewHistoryRepository.closeActiveHistories(broadcast, LocalDateTime.now());
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void syncBroadcastSchedules() {
        LocalDateTime now = LocalDateTime.now();

        List<Long> readyTargets = broadcastRepository.findBroadcastIdsForReadyTransition(now);
        for (Long broadcastId : readyTargets) {
            Broadcast broadcast = broadcastRepository.findById(broadcastId).orElse(null);
            if (broadcast != null && broadcast.getStatus() == BroadcastStatus.RESERVED) {
                validateTransition(broadcast.getStatus(), BroadcastStatus.READY);
                broadcast.readyBroadcast();
                sseService.notifyBroadcastUpdate(broadcastId, "BROADCAST_READY", "ready");
            }
        }

        List<Long> noShowTargets = broadcastRepository.findBroadcastIdsForNoShow(now);
        for (Long broadcastId : noShowTargets) {
            Broadcast broadcast = broadcastRepository.findById(broadcastId).orElse(null);
            if (broadcast != null && (broadcast.getStatus() == BroadcastStatus.RESERVED || broadcast.getStatus() == BroadcastStatus.READY)) {
                validateTransition(broadcast.getStatus(), BroadcastStatus.CANCELED);
                broadcast.markNoShow("broadcast start time violation");
                sseService.notifyBroadcastUpdate(broadcastId, "BROADCAST_CANCELED", "no_show");
            }
        }

        List<BroadcastRepositoryCustom.BroadcastScheduleInfo> schedules = broadcastRepository.findBroadcastSchedules(
                now.minusHours(2),
                now.plusHours(2),
                List.of(BroadcastStatus.ON_AIR, BroadcastStatus.READY, BroadcastStatus.ENDED, BroadcastStatus.RESERVED)
        );

        for (BroadcastRepositoryCustom.BroadcastScheduleInfo schedule : schedules) {
            if (schedule.scheduledAt() == null) {
                continue;
            }
            if (schedule.status() == BroadcastStatus.RESERVED) {
                LocalDateTime startNoticeAt = schedule.scheduledAt().minusMinutes(30);
                if (!startNoticeAt.isAfter(now) && schedule.scheduledAt().isAfter(now)) {
                    String noticeKey = redisService.getScheduleNoticeKey(schedule.broadcastId(), "start_30m");
                    if (redisService.setIfAbsent(noticeKey, "sent", java.time.Duration.ofHours(2))) {
                        Broadcast broadcast = broadcastRepository.findById(schedule.broadcastId()).orElse(null);
                        if (broadcast != null) {
                            broadcastScheduleEmailService.sendStartReminder(broadcast);
                        }
                    }
                }
            }
            LocalDateTime scheduledEnd = schedule.scheduledAt().plusMinutes(30);
            if (!scheduledEnd.isAfter(now)) {
                String noticeKey = redisService.getScheduleNoticeKey(schedule.broadcastId(), "ended");
                if (redisService.setIfAbsent(noticeKey, "sent", java.time.Duration.ofHours(2))) {
                    Broadcast broadcast = broadcastRepository.findById(schedule.broadcastId()).orElse(null);
                    if (broadcast != null && broadcast.getStatus() == BroadcastStatus.ON_AIR) {
                        validateTransition(broadcast.getStatus(), BroadcastStatus.ENDED);
                        broadcast.endBroadcast();
                        closeActiveViewHistories(broadcast);
                        openViduService.closeSession(schedule.broadcastId());
                        triggerRecordingFallback(schedule.broadcastId(), "scheduled_end");
                    }
                    if (broadcast != null && broadcast.getStatus() == BroadcastStatus.ENDED) {
                        validateTransition(broadcast.getStatus(), BroadcastStatus.VOD);
                        broadcast.changeStatus(BroadcastStatus.VOD);
                        restoreOriginalProductPrice(broadcast);
                    }
                    if (broadcast != null) {
                        saveBroadcastResultSnapshot(broadcast);
                    }
                    sseService.notifyBroadcastUpdate(schedule.broadcastId(), "BROADCAST_SCHEDULED_END", "ended");
                }
                continue;
            }

            LocalDateTime noticeAt = scheduledEnd.minusMinutes(1);
            if (!noticeAt.isAfter(now)) {
                String noticeKey = redisService.getScheduleNoticeKey(schedule.broadcastId(), "ending_soon");
                if (redisService.setIfAbsent(noticeKey, "sent", java.time.Duration.ofHours(2))) {
                    Broadcast broadcast = broadcastRepository.findById(schedule.broadcastId()).orElse(null);
                    if (broadcast != null) {
                        sseService.notifyTargetUser(schedule.broadcastId(), broadcast.getSeller().getSellerId(), "BROADCAST_ENDING_SOON", "1m");
                    }
                }
            }
        }
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void processRecordingFallbackQueue() {
        for (Long broadcastId : redisService.popDueRecordingRetries(20)) {
            triggerRecordingFallback(broadcastId, "retry_queue");
        }
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processRecordingStartRetryQueue() {
        for (Long broadcastId : redisService.popDueRecordingStartRetries(20)) {
            attemptStartRecordingRetry(broadcastId, "retry_queue");
        }
    }

    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void recoverMissingVodOrResult() {
        List<Broadcast> targets = broadcastRepository.findMissingVodOrResultByStatus(
                List.of(BroadcastStatus.ENDED, BroadcastStatus.STOPPED)
        );

        for (Broadcast broadcast : targets) {
            Long broadcastId = broadcast.getBroadcastId();
            boolean hasVod = vodRepository.findByBroadcast(broadcast).isPresent();
            boolean hasResult = broadcastResultRepository.findById(broadcastId).isPresent();

            if (!hasVod) {
                log.info("Missing VOD detected, triggering fallback: broadcastId={}", broadcastId);
                triggerRecordingFallback(broadcastId, "missing_vod");
            }

            if (!hasResult) {
                log.info("Missing broadcast result detected, saving snapshot: broadcastId={}", broadcastId);
                saveBroadcastResultSnapshot(broadcast);
            }
        }
    }

    private void triggerRecordingFallback(Long broadcastId, String reason) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId).orElse(null);
        if (broadcast == null) {
            redisService.clearRecordingRetry(broadcastId);
            return;
        }
        if (vodRepository.findByBroadcast(broadcast).isPresent()) {
            redisService.clearRecordingRetry(broadcastId);
            return;
        }

        String sessionId = "broadcast-" + broadcastId;
        try {
            Optional<Recording> recording = openViduService.findRecordingBySessionId(sessionId);
            if (recording.isEmpty()) {
                scheduleRecordingRetry(broadcastId, reason, "not_found");
                return;
            }

            String status = String.valueOf(recording.get().getStatus()).toLowerCase();
            if ("ready".equals(status)) {
                OpenViduRecordingWebhook payload = new OpenViduRecordingWebhook(
                        "recordingStatusChanged",
                        recording.get().getId(),
                        recording.get().getSessionId(),
                        recording.get().getName(),
                        recording.get().getSize(),
                        recording.get().getDuration(),
                        status,
                        recording.get().getUrl()
                );
                processVod(payload);
                redisService.clearRecordingRetry(broadcastId);
                return;
            }

            if ("failed".equals(status)) {
                log.warn("OpenVidu recording failed: broadcastId={}, status={}", broadcastId, status);
                redisService.clearRecordingRetry(broadcastId);
                return;
            }

            scheduleRecordingRetry(broadcastId, reason, status);
        } catch (OpenViduJavaClientException | OpenViduHttpException ex) {
            log.warn("OpenVidu recording status check failed: broadcastId={}, reason={}, message={}",
                    broadcastId, reason, ex.getMessage());
            scheduleRecordingRetry(broadcastId, reason, "error");
        } catch (Exception ex) {
            log.error("Recording fallback error: broadcastId={}, reason={}", broadcastId, reason, ex);
            scheduleRecordingRetry(broadcastId, reason, "exception");
        }
    }

    private void scheduleRecordingRetry(Long broadcastId, String reason, String status) {
        int attempt = redisService.incrementRecordingRetryAttempt(broadcastId, RECORDING_RETRY_TTL);
        if (attempt > RECORDING_RETRY_MAX_ATTEMPTS) {
            log.warn("Recording fallback retries exceeded: broadcastId={}, reason={}, status={}", broadcastId, reason, status);
            redisService.clearRecordingRetry(broadcastId);
            return;
        }
        Duration delay = RECORDING_RETRY_BASE_DELAY.multipliedBy(attempt);
        redisService.scheduleRecordingRetry(broadcastId, delay);
        log.info("Recording fallback scheduled: broadcastId={}, reason={}, status={}, attempt={}, delay={}s",
                broadcastId, reason, status, attempt, delay.toSeconds());
    }

    private void attemptStartRecordingRetry(Long broadcastId, String reason) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId).orElse(null);
        if (broadcast == null || broadcast.getStatus() != BroadcastStatus.ON_AIR) {
            redisService.clearRecordingStartRetry(broadcastId);
            return;
        }
        try {
            openViduService.startRecording(broadcastId);
            redisService.clearRecordingStartRetry(broadcastId);
            log.info("OpenVidu recording start succeeded after retry: broadcastId={}, reason={}", broadcastId, reason);
        } catch (OpenViduHttpException e) {
            int status = e.getStatus();
            if (isRetriableRecordingStartStatus(status)) {
                scheduleRecordingStartRetry(broadcastId, reason, status);
                return;
            }
            if (status == 409) {
                redisService.clearRecordingStartRetry(broadcastId);
                log.info("OpenVidu recording already started during retry: broadcastId={}, reason={}", broadcastId, reason);
                return;
            }
            redisService.clearRecordingStartRetry(broadcastId);
            log.error("OpenVidu recording start retry failed: broadcastId={}, reason={}, status={}, message={}",
                    broadcastId, reason, status, e.getMessage());
        } catch (OpenViduJavaClientException e) {
            scheduleRecordingStartRetry(broadcastId, reason, 0);
        }
    }

    private boolean isRetriableRecordingStartStatus(int status) {
        return status == 406 || (status >= 500 && status < 600 && status != 501);
    }

    private void scheduleRecordingStartRetry(Long broadcastId, String reason, int status) {
        int attempt = redisService.incrementRecordingStartRetryAttempt(broadcastId, RECORDING_START_RETRY_TTL);
        if (attempt > RECORDING_START_RETRY_MAX_ATTEMPTS) {
            log.warn("Recording start retries exceeded: broadcastId={}, reason={}, status={}", broadcastId, reason, status);
            redisService.clearRecordingStartRetry(broadcastId);
            return;
        }
        Duration delay = RECORDING_START_RETRY_BASE_DELAY.multipliedBy(attempt);
        redisService.scheduleRecordingStartRetry(broadcastId, delay);
        log.info("Recording start retry scheduled: broadcastId={}, reason={}, status={}, attempt={}, delay={}s",
                broadcastId, reason, status, attempt, delay.toSeconds());
    }

    private void saveBroadcastProducts(Long sellerId, Broadcast broadcast, List<BroadcastProductRequest> products) {
        if (products == null || products.isEmpty()) {
            return;
        }

        int order = 1;
        for (BroadcastProductRequest dto : products) {
            Product product = productRepository.findById(dto.getProductId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

            if (!product.getSellerId().equals(sellerId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
            }

            int stockQty = product.getStockQty() == null ? 0 : product.getStockQty();
            int safetyStock = product.getSafetyStock() == null ? 0 : product.getSafetyStock();
            int maxQuantity = stockQty - safetyStock;
            if (dto.getBpQuantity() == null || dto.getBpQuantity() > maxQuantity) {
                throw new BusinessException(ErrorCode.PRODUCT_SOLD_OUT);
            }

            BroadcastProduct bp = BroadcastProduct.builder()
                    .broadcast(broadcast)
                    .product(product)
                    .bpPrice(dto.getBpPrice())
                    .bpQuantity(dto.getBpQuantity())
                    .displayOrder(order++)
                    .isPinned(false)
                    .status(BroadcastProductStatus.SELLING)
                    .build();
            broadcastProductRepository.save(bp);
        }
    }

    private void saveQcards(Broadcast broadcast, List<QcardRequest> qcards) {
        if (qcards == null || qcards.isEmpty()) {
            return;
        }

        int sortOrder = 1;
        for (QcardRequest dto : qcards) {
            Qcard qcard = Qcard.builder()
                    .broadcast(broadcast)
                    .qcardQuestion(dto.getQuestion())
                    .sortOrder(sortOrder++)
                    .build();

            qcardRepository.save(qcard);
        }
    }

    private void updateBroadcastProducts(Long sellerId, Broadcast broadcast, List<BroadcastProductRequest> products) {
        broadcastProductRepository.deleteByBroadcast(broadcast);
        saveBroadcastProducts(sellerId, broadcast, products);
    }

    private void updateQcards(Broadcast broadcast, List<QcardRequest> qcards) {
        qcardRepository.deleteByBroadcast(broadcast);
        saveQcards(broadcast, qcards);
    }

    void applyLiveProductPrice(Broadcast broadcast) {
        List<BroadcastProduct> products = broadcastProductRepository.findAllWithProductByBroadcastId(broadcast.getBroadcastId());
        for (BroadcastProduct bp : products) {
            Integer bpPrice = bp.getBpPrice();
            if (bpPrice == null) {
                continue;
            }
            Product product = bp.getProduct();
            redisService.storeOriginalPrice(broadcast.getBroadcastId(), product.getId(), product.getPrice());
            product.changePrice(bpPrice);
        }
    }

    void restoreOriginalProductPrice(Broadcast broadcast) {
        List<BroadcastProduct> products = broadcastProductRepository.findAllWithProductByBroadcastId(broadcast.getBroadcastId());
        for (BroadcastProduct bp : products) {
            Integer originalPrice = redisService.getOriginalPrice(broadcast.getBroadcastId(), bp.getProduct().getId());
            if (originalPrice == null) {
                continue;
            }
            bp.getProduct().changePrice(originalPrice);
        }
        redisService.clearOriginalPrices(broadcast.getBroadcastId());
    }

    private void validateTransition(BroadcastStatus from, BroadcastStatus to) {
        if (!isTransitionAllowed(from, to)) {
            throw new BusinessException(ErrorCode.BROADCAST_INVALID_TRANSITION);
        }
    }

    private boolean isTransitionAllowed(BroadcastStatus from, BroadcastStatus to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return switch (from) {
            case RESERVED -> to == BroadcastStatus.READY || to == BroadcastStatus.CANCELED || to == BroadcastStatus.DELETED;
            case CANCELED -> to == BroadcastStatus.RESERVED || to == BroadcastStatus.DELETED;
            case READY -> to == BroadcastStatus.ON_AIR || to == BroadcastStatus.STOPPED || to == BroadcastStatus.CANCELED;
            case ON_AIR -> to == BroadcastStatus.ENDED || to == BroadcastStatus.STOPPED;
            case ENDED -> to == BroadcastStatus.VOD || to == BroadcastStatus.STOPPED;
            case STOPPED -> to == BroadcastStatus.VOD;
            default -> false;
        };
    }

    private List<BroadcastProductResponse> getProductListResponse(Broadcast broadcast) {
        Map<Long, Integer> remainingQuantities = calculateRemainingQuantities(broadcast, broadcast.getProducts());
        List<Long> productIds = broadcast.getProducts().stream()
                .map(bp -> bp.getProduct().getId())
                .distinct()
                .toList();
        Map<Long, String> thumbnailUrls = productIds.isEmpty()
                ? Collections.emptyMap()
                : productImageRepository
                .findAllByProductIdInAndImageTypeAndSlotIndexAndDeletedAtIsNullOrderByProductIdAscIdAsc(
                        productIds, ImageType.THUMBNAIL, 0
                ).stream()
                .collect(Collectors.toMap(
                        ProductImage::getProductId,
                        ProductImage::getProductImageUrl,
                        (left, right) -> left
                ));
        return broadcast.getProducts().stream()
                .map(bp -> BroadcastProductResponse.fromEntityWithImageUrl(
                        bp,
                        remainingQuantities.getOrDefault(bp.getProduct().getId(), bp.getBpQuantity()),
                        resolveOriginalPrice(broadcast, bp),
                        thumbnailUrls.get(bp.getProduct().getId())
                ))
                .collect(Collectors.toList());
    }

    private Integer resolveOriginalPrice(Broadcast broadcast, BroadcastProduct bp) {
        if (broadcast == null || bp == null) {
            return null;
        }
        Integer originalPrice = redisService.getOriginalPrice(broadcast.getBroadcastId(), bp.getProduct().getId());
        return originalPrice != null ? originalPrice : bp.getProduct().getPrice();
    }

    private void restoreOriginalPriceIfNeeded(Broadcast broadcast, BroadcastProduct bp) {
        if (broadcast == null || bp == null) {
            return;
        }
        if (broadcast.getStatus() != BroadcastStatus.ON_AIR) {
            return;
        }
        Integer originalPrice = redisService.getOriginalPrice(broadcast.getBroadcastId(), bp.getProduct().getId());
        if (originalPrice == null) {
            return;
        }
        bp.getProduct().changePrice(originalPrice);
        redisService.removeOriginalPrice(broadcast.getBroadcastId(), bp.getProduct().getId());
    }

    @Transactional
    public void restoreCostPriceIfSoldOut(Long productId) {
        if (productId == null) {
            return;
        }
        List<Long> broadcastIds = broadcastProductRepository.findOnAirBroadcastIdsByProductId(productId);
        if (broadcastIds == null || broadcastIds.isEmpty()) {
            return;
        }
        List<BroadcastProduct> products = broadcastProductRepository.findAllWithProductByBroadcastIdIn(broadcastIds);
        Map<Long, List<BroadcastProduct>> productsByBroadcast = products.stream()
                .collect(Collectors.groupingBy(bp -> bp.getBroadcast().getBroadcastId()));
        for (Long broadcastId : broadcastIds) {
            Broadcast broadcast = broadcastRepository.findById(broadcastId).orElse(null);
            if (broadcast == null || broadcast.getStatus() != BroadcastStatus.ON_AIR) {
                continue;
            }
            List<BroadcastProduct> broadcastProducts = productsByBroadcast.getOrDefault(broadcastId, List.of());
            if (broadcastProducts.isEmpty()) {
                continue;
            }
            Map<Long, Integer> remainingQuantities = calculateRemainingQuantities(broadcast, broadcastProducts);
            for (BroadcastProduct bp : broadcastProducts) {
                if (!bp.getProduct().getId().equals(productId)) {
                    continue;
                }
                Integer remaining = remainingQuantities.get(bp.getProduct().getId());
                if (remaining == null || remaining <= 0) {
                    restoreOriginalPriceIfNeeded(broadcast, bp);
                }
            }
        }
    }

    private List<QcardResponse> getQcardListResponse(Broadcast broadcast) {
        return broadcast.getQcards().stream()
                .map(q -> QcardResponse.builder()
                        .question(q.getQcardQuestion())
                        .sortOrder(q.getSortOrder())
                        .build())
                .collect(Collectors.toList());
    }

    private boolean isLiveGroup(BroadcastStatus status) {
        return status == BroadcastStatus.ON_AIR
                || status == BroadcastStatus.READY
                || status == BroadcastStatus.ENDED
                || status == BroadcastStatus.STOPPED;
    }

    private boolean shouldUseRealtimeStats(BroadcastStatus status) {
        return status == BroadcastStatus.ON_AIR
                || status == BroadcastStatus.READY
                || status == BroadcastStatus.ENDED;
    }

    private boolean isJoinableGroup(BroadcastStatus status) {
        return status == BroadcastStatus.ON_AIR || status == BroadcastStatus.READY;
    }

    private Long parseMemberId(String viewerId) {
        if (viewerId == null || viewerId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(viewerId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseBroadcastId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseBroadcastIdFromSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        String numeric = sessionId.replaceAll("\\D+", "");
        if (numeric.isBlank()) {
            return null;
        }
        return parseBroadcastId(numeric);
    }

    private Long resolveMemberId(String viewerId) {
        Long memberId = parseMemberId(viewerId);
        if (memberId != null) {
            return memberId;
        }
        if (viewerId == null || viewerId.isBlank()) {
            return null;
        }
        Member member = memberRepository.findByLoginId(viewerId);
        if (member == null) {
            return null;
        }
        return member.getMemberId();
    }

    private boolean isViewerSanctioned(Long broadcastId, Long memberId, SanctionType... types) {
        SanctionRepositoryCustom.SanctionTypeResult result = sanctionRepository.findLatestSanction(broadcastId, memberId);
        if (result == null || result.status() == null) {
            return false;
        }
        for (SanctionType type : types) {
            if (type.name().equalsIgnoreCase(result.status())) {
                return true;
            }
        }
        return false;
    }

    private boolean isViewerSanctioned(Long broadcastId, Long memberId) {
        return isViewerSanctioned(broadcastId, memberId, SanctionType.OUT);
    }

    private BroadcastResponse createBroadcastResponse(Broadcast broadcast) {
        Integer views = 0;
        Integer likes = 0;
        Integer reports = 0;
        String vodUrl = null;

        if (shouldUseRealtimeStats(broadcast.getStatus())) {
            views = redisService.getRealtimeViewerCount(broadcast.getBroadcastId());
            likes = redisService.getLikeCount(broadcast.getBroadcastId());
            reports = redisService.getReportCount(broadcast.getBroadcastId());
        } else {
            BroadcastResult result = broadcastResultRepository.findById(broadcast.getBroadcastId()).orElse(null);
            if (result != null) {
                views = result.getTotalViews();
                likes = result.getTotalLikes();
                reports = result.getTotalReports();
            }
        }

        if (broadcast.getStatus() == BroadcastStatus.VOD) {
            Vod vod = vodRepository.findByBroadcast(broadcast).orElse(null);
            if (vod != null && vod.getStatus() == VodStatus.PUBLIC) {
                vodUrl = vod.getVodUrl();
            }
        }

        return BroadcastResponse.fromEntity(
                broadcast,
                broadcast.getTagCategory().getTagCategoryName(),
                views,
                likes,
                reports,
                getProductListResponse(broadcast),
                getQcardListResponse(broadcast),
                vodUrl
        );
    }

    private BroadcastAllResponse getOverview(Long sellerId, boolean isAdmin) {
        List<BroadcastListResponse> onAir = broadcastRepository.findTop5ByStatus(
                sellerId,
                List.of(BroadcastStatus.ON_AIR, BroadcastStatus.READY, BroadcastStatus.ENDED, BroadcastStatus.STOPPED),
                BroadcastRepositoryCustom.BroadcastSortOrder.STARTED_AT_DESC,
                isAdmin
        );
        List<BroadcastListResponse> reserved = broadcastRepository.findTop5ByStatus(
                sellerId,
                isAdmin ? List.of(BroadcastStatus.RESERVED, BroadcastStatus.CANCELED) : List.of(BroadcastStatus.RESERVED),
                BroadcastRepositoryCustom.BroadcastSortOrder.SCHEDULED_AT_ASC,
                isAdmin
        );
        List<BroadcastListResponse> vod = broadcastRepository.findTop5ByStatus(
                sellerId,
                List.of(BroadcastStatus.VOD),
                BroadcastRepositoryCustom.BroadcastSortOrder.ENDED_AT_DESC,
                isAdmin
        );
        injectLiveDetails(onAir);
        return BroadcastAllResponse.builder().onAir(onAir).reserved(reserved).vod(vod).build();
    }

    private void injectLiveStats(List<BroadcastListResponse> list) {
        list.forEach(item -> {
            if (shouldUseRealtimeStats(item.getStatus())) {
                item.setLiveViewerCount(redisService.getRealtimeViewerCount(item.getBroadcastId()));
                item.setTotalLikes(redisService.getLikeCount(item.getBroadcastId()));
                item.setReportCount(redisService.getReportCount(item.getBroadcastId()));
            }
        });
    }

    private void injectLiveDetails(List<BroadcastListResponse> list) {
        List<Long> liveIds = list.stream()
                .filter(item -> isLiveGroup(item.getStatus()))
                .map(BroadcastListResponse::getBroadcastId)
                .toList();
        if (liveIds.isEmpty()) {
            return;
        }

        var productMap = broadcastProductRepository.findAllWithProductByBroadcastIdIn(liveIds).stream()
                .collect(Collectors.groupingBy(bp -> bp.getBroadcast().getBroadcastId()));
        var broadcastMap = broadcastRepository.findAllById(liveIds).stream()
                .collect(Collectors.toMap(Broadcast::getBroadcastId, java.util.function.Function.identity()));

        list.forEach(item -> {
            if (isLiveGroup(item.getStatus())) {
                if (shouldUseRealtimeStats(item.getStatus())) {
                    item.setLiveViewerCount(redisService.getRealtimeViewerCount(item.getBroadcastId()));
                    item.setTotalLikes(redisService.getLikeCount(item.getBroadcastId()));
                    item.setReportCount(redisService.getReportCount(item.getBroadcastId()));
                }

                List<BroadcastProduct> products = productMap.getOrDefault(item.getBroadcastId(), List.of());
                Broadcast broadcast = broadcastMap.get(item.getBroadcastId());
                Map<Long, Integer> remainingQuantities = calculateRemainingQuantities(broadcast, products);
                item.setProducts(products.stream().map(bp -> {
                    Product p = bp.getProduct();
                    int remaining = remainingQuantities.getOrDefault(p.getId(), bp.getBpQuantity());
                    return BroadcastListResponse.SimpleProductInfo.builder()
                            .name(p.getProductName())
                            .stock(remaining)
                            .isSoldOut(remaining <= 0)
                            .build();
                }).collect(Collectors.toList()));
            }
        });
    }

    private Map<Long, Integer> calculateRemainingQuantities(Broadcast broadcast, List<BroadcastProduct> products) {
        if (broadcast == null || products == null || products.isEmpty()) {
            return Map.of();
        }
        SalesSummary salesSummary = fetchBroadcastSalesSummary(broadcast);
        Map<Long, SalesMetric> metrics = salesSummary.productMetrics();
        Map<Long, Integer> totalQuantities = products.stream()
                .collect(Collectors.groupingBy(
                        bp -> bp.getProduct().getId(),
                        Collectors.summingInt(BroadcastProduct::getBpQuantity)
                ));
        return totalQuantities.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Math.max(0, entry.getValue()
                                - Optional.ofNullable(metrics.get(entry.getKey()))
                                .map(SalesMetric::salesQuantity)
                                .orElse(0))
                ));
    }

    private void ensureSlotCapacityForReservation(LocalDateTime scheduledAt) {
        long slotCount = broadcastRepository.countByTimeSlot(scheduledAt, scheduledAt.plusMinutes(30));
        if (slotCount >= 3) {
            throw new BusinessException(ErrorCode.BROADCAST_SLOT_FULL);
        }
    }
    private void ensureSlotCapacityAfterReservation(LocalDateTime scheduledAt) {
        long slotCount = broadcastRepository.countByTimeSlot(scheduledAt, scheduledAt.plusMinutes(30));
        if (slotCount > 3) {
            throw new BusinessException(ErrorCode.BROADCAST_SLOT_FULL);
        }
    }
    private String buildDbSlotLockKey(LocalDateTime scheduledAt) {
        return "db-lock:broadcast-slot:" + scheduledAt;
    }
    private boolean acquireDbSlotLock(String lockKey, int timeoutSeconds) {
        Integer result = dsl.resultQuery("SELECT GET_LOCK(?, ?)", lockKey, timeoutSeconds)
                .fetchOne(0, Integer.class);
        return result != null && result == 1;
    }
    private void releaseDbSlotLock(String lockKey) {
        try {
            dsl.resultQuery("SELECT RELEASE_LOCK(?)", lockKey).fetch();
        } catch (Exception e) {
            log.warn("DB slot lock release failed: key={}, message={}", lockKey, e.getMessage());
        }
    }

    private void disableSslVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }};
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (Exception e) {
            log.warn("SSL verification disable failed: {}", e.getMessage());
        }
    }
}
