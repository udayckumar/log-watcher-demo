package com.example.logwatcher.core;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UiController {
    @GetMapping("/log")
    public String logPage() {
        return "forward:/log/index.html";
    }
}
