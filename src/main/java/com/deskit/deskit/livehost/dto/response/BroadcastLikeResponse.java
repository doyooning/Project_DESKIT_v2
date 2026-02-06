package com.deskit.deskit.livehost.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastLikeResponse {
    private boolean liked;
    private int likeCount;
}
