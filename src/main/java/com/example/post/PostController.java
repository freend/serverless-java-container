package com.example.post;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/post")
public class PostController {
    @GetMapping
    public String get() {
        return "Get post";
    }
    @PostMapping
    public String post() {
        return "Post post";
    }
    @PutMapping
    public String put() {
        return "Put post";
    }
    @DeleteMapping
    public String delete() {
        return "Delete post";
    }
}
