package com.deskit.deskit.account.dto;

import com.deskit.deskit.account.enums.JobCategory;
import com.deskit.deskit.account.enums.MBTI;
import com.fasterxml.jackson.annotation.JsonProperty;

public record MySettingsResponse(
        @JsonProperty("mbti")
        MBTI mbti,

        @JsonProperty("job_category")
        JobCategory jobCategory,

        @JsonProperty("marketing_agreed")
        boolean marketingAgreed
) {}
