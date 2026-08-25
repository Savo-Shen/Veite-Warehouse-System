package com.ruoyi.wms.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 批量生成标题的任务进度。
 * <p>
 * 实测单次调用约 2 秒，148 个商品串行接近 5 分钟，同步返回必然超时且中断后无从得知
 * 进度，故改为提交任务 + 轮询进度。任务状态放内存，重启即失效——这是可接受的：
 * 任务本身不长，且每完成一个商品标题就已落库，重启后重新提交只会跳过已生成的。
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
public class EcGenTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String taskId;

    /** running / done / failed */
    private String state = "running";

    private int total;
    private int finished;
    private int ok;
    private int skipped;
    private int failed;

    /** 当前正在处理的商品名，供前端显示 */
    private String current;

    /** 整体失败原因（如 AI 未配置），非单条失败 */
    private String message;

    private LocalDateTime startTime = LocalDateTime.now();
    private LocalDateTime endTime;

    /** 每个商品的结果，字段：id / ecName / state / title / sellingPoints / message */
    private List<Map<String, Object>> results = new ArrayList<>();

    public int getPercent() {
        return total == 0 ? 0 : finished * 100 / total;
    }
}
