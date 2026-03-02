package ru.itis.documents.controller.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AppController {

    @GetMapping("/app")
    public String appHome() {
        return "app/index";
    }

    @GetMapping("/admin")
    public String adminHome() {
        return "admin/index";
    }
}