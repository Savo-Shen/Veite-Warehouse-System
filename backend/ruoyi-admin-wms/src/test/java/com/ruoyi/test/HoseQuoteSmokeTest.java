package com.ruoyi.test;

import com.ruoyi.RuoYiApplication;
import com.ruoyi.wms.domain.bo.HoseQuoteBo;
import com.ruoyi.wms.domain.vo.HoseQuoteLineVo;
import com.ruoyi.wms.domain.vo.HoseQuoteVo;
import com.ruoyi.wms.service.HoseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 压油管配料查询的冒烟测试。
 * <p>
 * 守的是这个模块最容易写错的一条规则：<b>胶管余料不能合并使用</b>。
 * 1602 合计 32 米，实际是 10+10+4+8 四段，接一张 12 米的单子一段都不够 ——
 * 任何拿 SUM(length_m) 判断够不够的实现都会在这里判错。
 * <p>
 * 前置条件：本机 MySQL 的 ry-vue 库已执行
 * {@code backend/script/sql/hose_module.sql} + {@code hose_module_seed.sql}，
 * 且胶管分段是 2026-08-23 那批实盘数据。换库或改了盘点数据后这个测试会失败，
 * 属于预期 —— 改断言里的数字，别改判定逻辑。
 * <p>
 * 生产打包走 {@code mvn -DskipTests clean package}，不会跑到这里。
 */
@SpringBootTest(classes = RuoYiApplication.class)
public class HoseQuoteSmokeTest {

    @Autowired
    private HoseService hoseService;

    private HoseQuoteVo quote(String hoseCode, String len, Integer qty, String a, String b) {
        HoseQuoteBo bo = new HoseQuoteBo();
        bo.setHoseCode(hoseCode);
        bo.setLengthM(new BigDecimal(len));
        bo.setAssemblyQty(qty);
        bo.setEndASku(a);
        bo.setEndBSku(b);
        return hoseService.quote(bo);
    }

    private HoseQuoteLineVo line(HoseQuoteVo vo, String role) {
        return vo.getLines().stream().filter(l -> l.getRole().equals(role)).findFirst().orElseThrow();
    }

    /** 核心规则：合计够但单段不够 → 压不了，且必须照样给报价 */
    @Test
    public void 余料不能合并使用() {
        HoseQuoteVo vo = quote("1602", "12", 1, "M22x1.5-A-面", null);

        assertFalse(vo.getCanCrimp(), "最长一段只有 10 米，接不了 12 米");
        assertTrue(vo.getGoWarehouse(), "压不了就要指向仓库");
        assertEquals("缺", line(vo, "胶管").getStatus());
        assertTrue(vo.getBlockers().stream().anyMatch(s -> s.contains("余料不能接")),
            "拦截原因要说清是余料问题，不是没货：" + vo.getBlockers());
        assertEquals(0, vo.getUsablePieces().size(), "没有哪一段够 12 米");
        assertEquals(4, vo.getAllPieces().size(), "但四段都要列出来，好解释");

        // 压不了也必须出报价 —— 柜台要拿这个数答复客户
        assertTrue(vo.getCostTotal().compareTo(BigDecimal.ZERO) > 0);
        assertNotNull(vo.getSellPrice());
        // 售价 = 进价 × 2（取整到元）
        assertEquals(0, vo.getSellPrice().compareTo(
            vo.getCostTotal().multiply(new BigDecimal("2")).setScale(0, java.math.RoundingMode.HALF_UP)));
        // 常显的那句结论不能带进价 —— 客户站在柜台边上看得见
        assertFalse(vo.getSummary().contains(vo.getCostTotal().toPlainString()),
            "结论里泄露了进价：" + vo.getSummary());
    }

    /** 同一批库存，接 3 米就够 */
    @Test
    public void 单段够长就能压() {
        HoseQuoteVo vo = quote("1602", "3", 1, "M22x1.5-A-面", "G3/8-C-弯-芯");

        assertTrue(vo.getCanCrimp());
        assertFalse(vo.getGoWarehouse());
        assertEquals("够", line(vo, "胶管").getStatus());
        assertEquals(4, vo.getUsablePieces().size(), "四段都 ≥3 米");
        // 两头不同款要各出一行
        assertNotNull(line(vo, "A端接头"));
        assertNotNull(line(vo, "B端接头"));
    }

    /** 一段 100 米可以裁出多根；两端同款时需求要合并成 2×根数 */
    @Test
    public void 一段可以裁多根() {
        HoseQuoteVo vo = quote("1001", "2", 8, "G3/8-A-面", null);

        assertTrue(vo.getCanCrimp(), "100 米裁 8 根 2 米绰绰有余");
        HoseQuoteLineVo f = line(vo, "两端接头（同款）");
        assertEquals("16 个", f.getNeedText(), "8 根 × 两头 = 16 个，不能只算 8 个");
        assertEquals("16 个", line(vo, "扣压外套").getNeedText());
    }

    /** 三层管外径跟一二层不同，外套不通用，必须落到 F3 那一档 */
    @Test
    public void 三层管外套不能拿一二层的顶() {
        HoseQuoteVo vo = quote("2503", "2", 1, "M22x1.5-C-面", null);
        assertTrue(line(vo, "扣压外套").getCode().startsWith("F3-"),
            "三层管要用 F3 外套，实际拿到 " + line(vo, "扣压外套").getCode());
    }

    /** 一段都没有的规格：压不了，但报价照给 */
    @Test
    public void 没库存也要出报价() {
        HoseQuoteVo vo = quote("3204", "5", 1, "M22x1.5-A-面", null);

        assertFalse(vo.getCanCrimp());
        assertTrue(vo.getBlockers().stream().anyMatch(s -> s.contains("一段都没有")));
        assertTrue(vo.getCostTotal().compareTo(BigDecimal.ZERO) > 0, "没货也要能报价");
    }

    /** 接头还没盘点（qty 为 null）时只提醒，不当成缺料 —— 否则现在全库都压不了 */
    @Test
    public void 未盘点只提醒不拦截() {
        HoseQuoteVo vo = quote("1602", "3", 1, "M22x1.5-A-面", null);
        assertEquals("未盘", line(vo, "两端接头（同款）").getStatus());
        assertTrue(vo.getWarnings().stream().anyMatch(s -> s.contains("还没盘过库存")));
        assertTrue(vo.getCanCrimp(), "未盘点不能拦路，否则页面刚上线就全是红的");
    }
}
