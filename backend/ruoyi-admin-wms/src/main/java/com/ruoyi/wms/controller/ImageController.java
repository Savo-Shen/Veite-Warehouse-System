package com.ruoyi.wms.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

/**
 * 打印模板用到的图片
 */
@RestController
@RequestMapping("/wms/image")
public class ImageController {

    private static final String LOGO_LOCATION = "static/images/veite.png";

    /**
     * 打印模板的公司logo，从 classpath 读取，不依赖部署机器的绝对路径
     */
    @GetMapping(value = "/logo", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getLogo() throws IOException {
        ClassPathResource resource = new ClassPathResource(LOGO_LOCATION);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        byte[] imageBytes;
        try (InputStream in = resource.getInputStream()) {
            imageBytes = in.readAllBytes();
        }
        return ResponseEntity
                .ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(imageBytes);
    }
}
