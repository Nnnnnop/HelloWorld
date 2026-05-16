package com.example.polyusigwebsite.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final List<String> allowedFileTypes;

    public ConfigController(
            @Value("${sig.security.allowed-file-types:pdf,doc,docx,xls,xlsx,ppt,pptx,png,jpg,jpeg,gif,webp,bmp,svg,java,py,c,cpp,js,ts,r,m,go,rs,txt,md,json,xml,yaml,yml,sql,csv,ipynb,zip,rar,7z,tar,gz}") String allowedFileTypes
    ) {
        this.allowedFileTypes = Arrays.stream(allowedFileTypes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }

    @GetMapping("/allowed-file-types")
    public List<String> getAllowedFileTypes() {
        return allowedFileTypes;
    }
}
