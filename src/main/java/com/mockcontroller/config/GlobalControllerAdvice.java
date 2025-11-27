package com.mockcontroller.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    @Value("${app.faq.url:}")
    private String faqUrl;

    @ModelAttribute("faqUrl")
    public String getFaqUrl() {
        return faqUrl;
    }
}

