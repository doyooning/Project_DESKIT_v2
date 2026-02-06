package com.deskit.deskit.account.dto;

import com.deskit.deskit.account.enums.JobCategory;
import com.deskit.deskit.account.enums.MBTI;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record MySettingsUpdateRequest(
        @JsonProperty("mbti")
        MBTI mbti,

        @JsonProperty("job_category")
        JobCategory jobCategory,

        @JsonProperty("marketing_agreed")
        @JsonAlias({"is_agreed", "isAgreed", "agreed"})
        Boolean marketingAgreed
) {}
