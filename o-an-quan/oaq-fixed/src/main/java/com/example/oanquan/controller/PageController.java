package com.example.oanquan.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {
    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/game/{id}")
    public String game(@PathVariable Long id) {
        return "game";
    }

    @GetMapping("/lobby")
    public String lobby() {
        return "lobby";
    }
}
