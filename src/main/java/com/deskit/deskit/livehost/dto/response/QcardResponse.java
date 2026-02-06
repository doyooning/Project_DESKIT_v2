package com.deskit.deskit.livehost.dto.response;

import com.deskit.deskit.livehost.entity.Qcard;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QcardResponse {
    private Long qcardId;
    private int sortOrder;
    private String question;

    public static QcardResponse fromEntity(Qcard qcard) {
        return QcardResponse.builder()
                .qcardId(qcard.getQcardId())
                .sortOrder(qcard.getSortOrder())
                .question(qcard.getQcardQuestion())
                .build();
    }
}
