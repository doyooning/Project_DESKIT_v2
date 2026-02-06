package com.deskit.deskit.account.oauth;

import java.util.Map;

public class KakaoResponse implements OAuth2Response {

    private final Map<String, Object> attribute;

    public KakaoResponse(Map<String, Object> attribute) {

        this.attribute = attribute;
    }


    @Override
    public String getProvider() {

        return "kakao";
    }

    @Override
    public String getProviderId() {

        return attribute.get("id").toString();
    }

    @Override
    public String getEmail() {
        Map<String, Object> kakaoAccount =
                (Map<String, Object>) attribute.get("kakao_account");

        return (String) kakaoAccount.get("email");
    }

    // kakao 이름은 개인 개발자 앱으로는 닉네임(실명 아님)만 수집 가능

    @Override
    public String getName() {
        Map<String, Object> kakaoAccount =
                (Map<String, Object>) attribute.get("kakao_account");
        Map<String, Object> profile =
                (Map<String, Object>) kakaoAccount.get("profile");

        return (String) profile.get("nickname");
    }

    @Override
    public String getProfileUrl() {
        Map<String, Object> kakaoAccount =
                (Map<String, Object>) attribute.get("kakao_account");

        return kakaoAccount == null
                ? ""
                : (kakaoAccount.get("profile")) == null
                ? ""
                : (String) ((Map<String, Object>) kakaoAccount.get("profile"))
                .get("profile_image_url");
    }
}
