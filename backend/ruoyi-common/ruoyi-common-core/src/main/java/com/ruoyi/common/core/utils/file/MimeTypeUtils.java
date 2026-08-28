package com.ruoyi.common.core.utils.file;

/**
 * 媒体类型工具类
 *
 * @author ruoyi
 */
public class MimeTypeUtils {
    public static final String IMAGE_PNG = "image/png";

    public static final String IMAGE_JPG = "image/jpg";

    public static final String IMAGE_JPEG = "image/jpeg";

    public static final String IMAGE_BMP = "image/bmp";

    public static final String IMAGE_GIF = "image/gif";

    public static final String[] IMAGE_EXTENSION = {"bmp", "gif", "jpg", "jpeg", "png"};

    public static final String[] FLASH_EXTENSION = {"swf", "flv"};

    public static final String[] MEDIA_EXTENSION = {"swf", "flv", "mp3", "wav", "wma", "wmv", "mid", "avi", "mpg",
        "asf", "rm", "rmvb"};

    public static final String[] VIDEO_EXTENSION = {"mp4", "avi", "rmvb"};

    public static final String[] DEFAULT_ALLOWED_EXTENSION = {
        // 图片
        "bmp", "gif", "jpg", "jpeg", "png",
        // word excel powerpoint
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "html", "htm", "txt",
        // 压缩文件
        "rar", "zip", "gz", "bz2",
        // 视频格式
        "mp4", "avi", "rmvb",
        // pdf
        "pdf"};

    /**
     * OSS 上传允许的后缀。
     * <p>
     * 刻意不复用 {@link #DEFAULT_ALLOWED_EXTENSION}：那个列表里有 html/htm，
     * 而 /system/oss/blob/{ossId} 是匿名同源可读的，传个 html 上去就是存储型 XSS，
     * 能直接偷走管理员的 token。svg 同理（可内嵌 script），也不放行。
     */
    public static final String[] UPLOAD_ALLOWED_EXTENSION = {
        // 图片
        "bmp", "gif", "jpg", "jpeg", "png", "webp", "heic",
        // office
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "txt",
        // 压缩包
        "rar", "zip", "7z", "gz",
        // 视频
        "mp4", "avi", "rmvb", "mov",
        // pdf
        "pdf"};

    /**
     * 允许直接以原始 MIME 内联渲染的类型。
     * 不在这个集合里的一律按 application/octet-stream + attachment 下发，
     * 避免浏览器把用户上传的内容当成同源 HTML/脚本执行。
     */
    private static final java.util.Set<String> INLINE_SAFE_CONTENT_TYPES = java.util.Set.of(
        IMAGE_PNG, IMAGE_JPG, IMAGE_JPEG, IMAGE_BMP, IMAGE_GIF,
        "image/webp", "image/heic", "application/pdf");

    /**
     * 判断某个 MIME 是否可以安全地内联渲染
     */
    public static boolean isInlineSafeContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        // 去掉 charset 等参数后比较
        int semicolon = contentType.indexOf(';');
        String bare = (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).trim().toLowerCase();
        return INLINE_SAFE_CONTENT_TYPES.contains(bare);
    }

}
