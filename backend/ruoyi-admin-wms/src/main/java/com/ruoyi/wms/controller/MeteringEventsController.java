package com.ruoyi.wms.controller;

import com.ruoyi.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 兼容误投到 WMS 的生产计量事件。
 * <p>
 * 真正的计量接口属于计费服务；这里仅吞掉错误路由，避免被当成静态资源异常刷屏。
 */
@Slf4j
@RestController
public class MeteringEventsController {

    @PostMapping("/metering/events")
    public R<Void> ignoreMeteringEvents(@RequestBody(required = false) String body) {
        log.warn("收到误投到 WMS 的 /metering/events，请检查生产节点计量地址配置。payload={}", body);
        return R.fail(404, "计量接口不在 WMS，请检查 metering/events 上报地址");
    }
}
