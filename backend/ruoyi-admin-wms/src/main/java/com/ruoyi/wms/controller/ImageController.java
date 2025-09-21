package com.ruoyi.wms.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/wms/image")
public class ImageController {

    @GetMapping(value = "/logo", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getLogo() throws IOException {
        // 读取本地文件（你可以根据实际路径调整）
        Path path = Paths.get("/Users/savo_shen/Programs/仓库出入库管理系统/wms-ruoyi/ruoyi-admin-wms/src/main/resources/static/images/veite.png"); // 修改为你 logo.png 的实际路径
        byte[] imageBytes = Files.readAllBytes(path);

        return ResponseEntity
                .ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(imageBytes);
    }
}