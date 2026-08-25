package com.ruoyi.wms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.common.mybatis.core.page.TableDataInfo;
import com.ruoyi.common.web.core.BaseController;
import com.ruoyi.wms.domain.bo.HoseFittingBo;
import com.ruoyi.wms.domain.bo.HoseQuoteBo;
import com.ruoyi.wms.domain.entity.HoseCrimp;
import com.ruoyi.wms.domain.entity.HosePiece;
import com.ruoyi.wms.domain.vo.HoseCrimpVo;
import com.ruoyi.wms.domain.vo.HoseFerruleVo;
import com.ruoyi.wms.domain.vo.HoseFittingVo;
import com.ruoyi.wms.domain.vo.HosePieceVo;
import com.ruoyi.wms.domain.vo.HoseQuoteVo;
import com.ruoyi.wms.domain.vo.HoseSpecVo;
import com.ruoyi.wms.service.HoseService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 压油管（液压胶管总成）
 *
 * @author savo
 * @date 2026-08-23
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/wms/hose")
public class HoseController extends BaseController {

    private final HoseService hoseService;

    /**
     * 胶管规格 + 在库汇总
     */
    @SaCheckPermission("wms:hose:list")
    @GetMapping("/spec/list")
    public R<List<HoseSpecVo>> specList(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Boolean onlyInStock) {
        return R.ok(hoseService.querySpecList(keyword, onlyInStock));
    }

    /**
     * 胶管在库分段明细（一段一行）
     */
    @SaCheckPermission("wms:hose:list")
    @GetMapping("/piece/list")
    public R<List<HosePieceVo>> pieceList(@RequestParam(required = false) String keyword) {
        return R.ok(hoseService.queryPieceList(keyword));
    }

    /**
     * 接头列表
     */
    @SaCheckPermission("wms:hose:list")
    @GetMapping("/fitting/list")
    public TableDataInfo<HoseFittingVo> fittingList(HoseFittingBo bo, PageQuery pageQuery) {
        return hoseService.queryFittingPage(bo, pageQuery);
    }

    /**
     * 接头下拉选项，配料时选两端接头用
     */
    @SaCheckPermission("wms:hose:list")
    @GetMapping("/fitting/options")
    public R<List<HoseFittingVo>> fittingOptions(@RequestParam(required = false) String keyword) {
        return R.ok(hoseService.queryFittingOptions(keyword));
    }

    /**
     * 扣压外套列表
     */
    @SaCheckPermission("wms:hose:list")
    @GetMapping("/ferrule/list")
    public R<List<HoseFerruleVo>> ferruleList(@RequestParam(required = false) String keyword) {
        return R.ok(hoseService.queryFerruleList(keyword));
    }

    /**
     * 扣压参数列表
     */
    @SaCheckPermission("wms:hose:list")
    @GetMapping("/crimp/list")
    public R<List<HoseCrimpVo>> crimpList() {
        return R.ok(hoseService.queryCrimpList());
    }

    /**
     * 配料查询：接一根总成要什么料、料在哪、怎么压、多少钱。
     * 只读，不写库存，所以用 list 权限。
     */
    @SaCheckPermission("wms:hose:list")
    @PostMapping("/quote")
    public R<HoseQuoteVo> quote(@Validated @RequestBody HoseQuoteBo bo) {
        return R.ok(hoseService.quote(bo));
    }

    /*
     * 接头/外套的库存、库位、进价不在这里改了。
     * 它们现在是普通商品 SKU，进货走入库单、卖出走出库单、盘点走盘点单，
     * 改库位和进价去「基础资料 → 商品管理」。
     * 这样才有出入库留痕，也才能单卖接头。
     */

    /**
     * 扣压参数回填（现场实测后填）
     */
    @SaCheckPermission("wms:hose:edit")
    @Log(title = "压油管-扣压参数", businessType = BusinessType.UPDATE)
    @PutMapping("/crimp")
    public R<Integer> saveCrimp(@RequestBody List<HoseCrimp> list) {
        return R.ok(hoseService.saveCrimp(list));
    }

    /**
     * 新增一段胶管（进了新盘管，或者盘点补录）
     */
    @SaCheckPermission("wms:hose:edit")
    @Log(title = "压油管-新增胶管段", businessType = BusinessType.INSERT)
    @PostMapping("/piece")
    public R<Void> addPiece(@RequestBody HosePiece piece) {
        hoseService.addPiece(piece);
        return R.ok();
    }

    /**
     * 修改一段胶管
     */
    @SaCheckPermission("wms:hose:edit")
    @Log(title = "压油管-修改胶管段", businessType = BusinessType.UPDATE)
    @PutMapping("/piece")
    public R<Void> updatePiece(@RequestBody HosePiece piece) {
        hoseService.updatePiece(piece);
        return R.ok();
    }

    /**
     * 裁走一段：接完管把用掉的米数扣掉。剩 0 标记用完，不物理删除。
     */
    @SaCheckPermission("wms:hose:edit")
    @Log(title = "压油管-裁管扣库存", businessType = BusinessType.UPDATE)
    @PutMapping("/piece/cut/{id}")
    public R<Void> cutPiece(@NotNull(message = "主键不能为空") @PathVariable Long id,
                            @RequestParam BigDecimal usedM) {
        hoseService.cutPiece(id, usedM);
        return R.ok();
    }

    /**
     * 删除一段（录错了才用）
     */
    @SaCheckPermission("wms:hose:edit")
    @Log(title = "压油管-删除胶管段", businessType = BusinessType.DELETE)
    @DeleteMapping("/piece/{id}")
    public R<Void> deletePiece(@NotNull(message = "主键不能为空") @PathVariable Long id) {
        hoseService.deletePiece(id);
        return R.ok();
    }
}
