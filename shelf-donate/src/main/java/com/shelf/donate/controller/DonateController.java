package com.shelf.donate.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.lang.UUID;
import com.shelf.common.oss.AliOssTemplate;
import com.shelf.common.result.Result;
import com.shelf.donate.domain.dto.DonateSubmitDTO;
import com.shelf.donate.domain.vo.DonateRecordVO;
import com.shelf.donate.service.IDonateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/donate")
@RequiredArgsConstructor
public class DonateController {

    private final IDonateService donateService;
    private final AliOssTemplate aliOssTemplate;

    /** 允许的图片后缀 */
    private static final List<String> ALLOWED_IMAGE_TYPES = List.of("jpg", "jpeg", "png", "gif", "webp");
    /** 单个图片最大 10MB */
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;


    @PostMapping
    @SaCheckLogin
    public Result<Void> donate(@Valid @RequestBody DonateSubmitDTO dto) {
        donateService.donate(dto);
        return Result.success();
    }


    /**
     * 捐赠图片上传（封面图 / 内页图）
     * 前端先调此接口拿到 URL，再随 donate 接口提交
     */
    @PostMapping("/upload-image")
    @SaCheckLogin
    public Result<String> uploadImage(@RequestPart("file") MultipartFile file) {
        // 1. 空文件校验
        if (file == null || file.isEmpty()) {
            return Result.error("A00001", "上传文件不能为空");
        }

        // 2. 文件大小校验
        if (file.getSize() > MAX_IMAGE_SIZE) {
            return Result.error("A00001", "图片大小不能超过10MB");
        }

        // 3. 提取并校验后缀
        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename).toLowerCase();
        if (!ALLOWED_IMAGE_TYPES.contains(extension)) {
            return Result.error("A00001", "不支持的图片类型: " + extension);
        }

        // 4. 上传至 OSS（目录：donate/）
        try {
            String objectName = "donate/" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
            String url = aliOssTemplate.upload(file.getBytes(), objectName);
            return Result.success(url);
        } catch (Exception e) {
            return Result.error("A00001", "图片上传失败，请重试");
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int lastDot = filename.lastIndexOf(".");
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }

    @GetMapping("/my-records")
    @SaCheckLogin
    public Result<List<DonateRecordVO>> listMyDonates() {
        return Result.success(donateService.listMyDonates());
    }
}