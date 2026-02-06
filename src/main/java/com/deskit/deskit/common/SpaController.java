package com.deskit.deskit.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaController {

//    @RequestMapping(value = {"/", "/{path:[^\\.]*}"})
//    public String forwardRoot(HttpServletRequest request) {
//        return forwardIfSpa(request);
//    }
//
//    @RequestMapping(value = "/**/{path:[^\\.]*}")
//    public String forwardDeep(HttpServletRequest request) {
//        return forwardIfSpa(request);
//    }
//
//    private String forwardIfSpa(HttpServletRequest request) {
//        String uri = request.getRequestURI();
//
//        if (uri.startsWith("/api")
//                || uri.startsWith("/ws")
//                || uri.startsWith("/openvidu")
//                || uri.startsWith("/oauth")
//                || uri.startsWith("/login")
//                || uri.startsWith("/oauth2")) {
//            return null; // SPA forward 대상 아님
//        }
//
//        return "forward:/index.html";
//    }

//    // [수정된 정규식]
//    // 1. /api, /ws, /openvidu, /oauth, /login, /oauth2 로 시작하지 않음 ((?!...))
//    // 2. 파일 확장자(.js, .css 등)가 없음 ([^\\.]*)
//    @RequestMapping(value = "/{path:^(?!api|ws|openvidu|oauth|login|oauth2)[^\\.]*}/**")
//    public String redirect() {
//        return "forward:/index.html";
//    }
//
//    // 루트 경로(/)는 별도로 매핑
//    @RequestMapping("/")
//    public String root() {
//        return "forward:/index.html";
//    }
    // gemini : spacontroller 불필요
}
