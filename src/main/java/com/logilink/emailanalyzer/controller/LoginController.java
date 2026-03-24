package com.logilink.emailanalyzer.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/")
    public String home() {
        return "redirect:/emails";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
