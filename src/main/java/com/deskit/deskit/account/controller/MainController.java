package com.deskit.deskit.account.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class MainController {

    @GetMapping(value = {
            "/",
            "/{path:^(?!api$)(?!.*\\..*$).*$}",
            "/{path:^(?!api$)(?!.*\\..*$).*$}/**"
    })
    public String mainAPI() {
        return "forward:/index.html";
    }
}
