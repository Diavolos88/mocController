package com.mockcontroller.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class ModelAttributeAdvice {

    @Value("${app.api.base-url:http://localhost:8085}")
    private String apiBaseUrl;

    @ModelAttribute("apiBaseUrl")
    public String apiBaseUrl() {
        return apiBaseUrl;
    }
}

