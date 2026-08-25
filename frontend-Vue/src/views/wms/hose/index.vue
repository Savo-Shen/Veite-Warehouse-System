<template>
  <div class="p-2 hose-page">
    <!-- ============ 配料查询：柜台上最常用的一块 ============ -->
    <el-card shadow="hover">
      <template #header>
        <div class="card-head">
          <span class="card-title">配料查询</span>
          <span class="card-tip">
            填「什么管、多长、两头什么接头」，出料在哪、怎么压、多少钱。压不了也会给报价。
          </span>
          <!--
            进价默认藏着：客户就站在柜台边上，屏幕他看得见。
            跟手机查价页一个做法，点星星才亮，整页的进价一起显隐。
          -->
          <el-tooltip :content="showCost ? '隐藏进价' : '显示进价（客户在旁边时别点）'" placement="top">
            <el-button class="cost-toggle" :class="{ active: showCost }" circle size="small"
                       :icon="showCost ? StarFilled : Star" @click="showCost = !showCost" />
          </el-tooltip>
        </div>
      </template>

      <el-form ref="quoteFormRef" :model="quoteForm" :rules="quoteRules" label-width="92px">
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="胶管规格" prop="hoseCode">
              <el-select v-model="quoteForm.hoseCode" filterable placeholder="选管，如 1302 四分两层"
                         style="width:100%" @change="onSpecChange">
                <el-option v-for="s in specOptions" :key="s.hoseCode"
                           :label="`${s.hoseCode}  ${s.nickname} ${s.inch} ${s.layerName}`"
                           :value="s.hoseCode">
                  <span style="float:left">{{ s.hoseCode }} · {{ s.nickname }} {{ s.inch }} {{ s.layerName }}</span>
                  <span style="float:right" :class="s.pieceCount > 0 ? 'opt-ok' : 'opt-none'">
                    {{ s.pieceCount > 0 ? `最长 ${num(s.maxLengthM)}米 / 共${num(s.totalLengthM)}米` : '无库存' }}
                  </span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :xs="12" :sm="6" :md="4">
            <el-form-item label="长度(米)" prop="lengthM">
              <el-input-number v-model="quoteForm.lengthM" :min="0.1" :step="0.1" :precision="2"
                               controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>

          <el-col :xs="12" :sm="6" :md="4">
            <el-form-item label="做几根" prop="assemblyQty">
              <el-input-number v-model="quoteForm.assemblyQty" :min="1" :step="1"
                               controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>

          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="外套">
              <!-- element-plus 2.2 的 radio-button 用 label 传值，2.6 才有 value -->
              <el-radio-group v-model="quoteForm.skinType">
                <el-radio-button label="非剥皮">非剥皮</el-radio-button>
                <el-radio-button label="剥皮">剥皮</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>

          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="A端接头" prop="endASku">
              <el-select v-model="quoteForm.endASku" filterable remote clearable
                         :remote-method="searchFitting" :loading="fittingSearching"
                         placeholder="搜现场叫法，如 22×1.5 A型面" style="width:100%">
                <el-option v-for="f in fittingOptions" :key="f.fittingSku"
                           :label="f.fieldName" :value="f.fittingSku">
                  <span style="float:left">{{ f.fieldName }}</span>
                  <span style="float:right;font-size:12px" :class="stockClass(f.qty)">
                    {{ f.qty == null ? '未盘' : f.qty + ' 个' }}
                  </span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="B端接头">
              <el-select v-model="quoteForm.endBSku" filterable remote clearable
                         :remote-method="searchFitting" :loading="fittingSearching"
                         placeholder="留空 = 跟 A 端同款" style="width:100%">
                <el-option v-for="f in fittingOptions" :key="f.fittingSku"
                           :label="f.fieldName" :value="f.fittingSku">
                  <span style="float:left">{{ f.fieldName }}</span>
                  <span style="float:right;font-size:12px" :class="stockClass(f.qty)">
                    {{ f.qty == null ? '未盘' : f.qty + ' 个' }}
                  </span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :xs="24" :md="8">
            <el-form-item label=" ">
              <el-button type="primary" icon="Search" :loading="quoting" @click="doQuote">查配料</el-button>
              <el-button icon="Refresh" @click="resetQuote">重填</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- ---------- 查询结果 ---------- -->
      <div v-if="quote" class="quote-result">
        <el-alert :type="verdictType" :closable="false" show-icon>
          <template #title>
            <span class="verdict">{{ quote.verdict }}</span>
          </template>
          <div class="summary">{{ quote.summary }}</div>
        </el-alert>

        <!-- 压不了 → 报价 + 去仓库压。这块要显眼，柜台照着念 -->
        <div v-if="quote.goWarehouse" class="go-warehouse">
          <el-icon><Van /></el-icon>
          <div>
            <div class="gw-title">这根店里压不了，去仓库压</div>
            <div class="gw-sub">
              报价照下面这个报。缺什么、要带什么、在哪个库位，配料单里都列了；
              店里没料的去仓库拿，压机压不动的直接在仓库压。
            </div>
          </div>
        </div>

        <el-row :gutter="12" style="margin-top:12px">
          <!-- 配料单 -->
          <el-col :xs="24" :lg="16">
            <div class="block-title">配料单 · 一根总成 = 1 段胶管 + 2×(接头 + 外套)</div>
            <el-table :data="quote.lines" size="small" border>
              <el-table-column label="部位" prop="role" width="120" />
              <el-table-column label="要拿什么" min-width="200">
                <template #default="{ row }">
                  <div class="line-name">{{ row.name }}</div>
                  <div class="line-sub">{{ row.code }}<span v-if="row.spec"> · {{ row.spec }}</span></div>
                </template>
              </el-table-column>
              <el-table-column label="需要" prop="needText" width="110" />
              <el-table-column label="在库" min-width="180">
                <template #default="{ row }">{{ row.stockText }}</template>
              </el-table-column>
              <el-table-column label="在哪" min-width="140">
                <template #default="{ row }">
                  <span :class="{ 'no-loc': row.locationText === '库位没填' }">{{ row.locationText }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)" size="small" effect="plain">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column v-if="showCost" label="进价小计" width="90" align="right">
                <template #default="{ row }">
                  <span v-if="row.amount != null">{{ row.amount }}</span>
                  <span v-else class="no-price">待录价</span>
                </template>
              </el-table-column>
            </el-table>

            <div v-if="quote.blockers.length" class="msg-list blockers">
              <div v-for="(b, i) in quote.blockers" :key="i">· {{ b }}</div>
            </div>
            <div v-if="quote.warnings.length" class="msg-list warnings">
              <div v-for="(w, i) in quote.warnings" :key="i">· {{ w }}</div>
            </div>

            <!-- 该切哪一段 -->
            <div v-if="quote.allPieces.length" class="block-title" style="margin-top:14px">
              胶管在库分段 · 优先切最短的够用段，长料留着接长单
            </div>
            <el-table v-if="quote.allPieces.length" :data="quote.allPieces" size="small" border>
              <el-table-column label="长度" width="100">
                <template #default="{ row }">{{ num(row.lengthM) }} 米</template>
              </el-table-column>
              <el-table-column label="库位" min-width="160">
                <template #default="{ row }">{{ row.locationCode }} {{ row.locationName }}</template>
              </el-table-column>
              <el-table-column label="够不够" width="120" align="center">
                <template #default="{ row }">
                  <el-tag v-if="usable(row)" type="success" size="small" effect="plain">够，可切</el-tag>
                  <el-tag v-else type="info" size="small" effect="plain">不够</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="110" align="right">
                <template #default="{ row }">
                  <el-button v-if="usable(row)" link type="primary"
                             v-hasPermi="['wms:hose:edit']" @click="openCut(row)">切这段</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-col>

          <!-- 报价 -->
          <el-col :xs="24" :lg="8">
            <div class="block-title">报价</div>
            <div class="quote-box">
              <div class="sell-price">
                <span class="sp-label">售价</span>
                <b class="sp-value">{{ quote.sellPrice }}</b>
                <span class="sp-unit">元</span>
              </div>
              <div v-if="quote.missingCostCount > 0" class="qb-warn">
                还有 {{ quote.missingCostCount }} 项没录进价，实际要比这个高
              </div>
              <!-- 进价这一段点星星才出来 -->
              <template v-if="showCost">
                <el-divider style="margin:10px 0" />
                <div class="qb-row">
                  <span>进价合计</span><b>{{ quote.costTotal }} 元</b>
                </div>
                <div class="qb-row">
                  <span>加价</span><b>×{{ Number(quote.sellMarkup) }}</b>
                </div>
              </template>
              <div class="qb-note">
                售价 = 进价 × {{ Number(quote.sellMarkup) }}。要改倍数改后端
                HoseService 里的 SELL_MARKUP 一个常量。
              </div>
            </div>

            <div class="block-title" style="margin-top:14px">扣压参数</div>
            <el-descriptions v-if="quote.crimp" :column="1" size="small" border>
              <el-descriptions-item label="适用">
                {{ quote.crimp.layerScope }} · {{ quote.crimp.nickname }} {{ quote.crimp.inch }}
              </el-descriptions-item>
              <el-descriptions-item label="模具号">{{ dash(quote.crimp.dieNo) }}</el-descriptions-item>
              <el-descriptions-item label="扣压直径">{{ dash(quote.crimp.crimpDiameterMm, 'mm') }}</el-descriptions-item>
              <el-descriptions-item label="剥胶长度">{{ dash(quote.crimp.stripLengthMm, 'mm') }}</el-descriptions-item>
              <el-descriptions-item label="插入深度">{{ dash(quote.crimp.insertDepthMm, 'mm') }}</el-descriptions-item>
              <el-descriptions-item label="压机档位">{{ dash(quote.crimp.pressGear) }}</el-descriptions-item>
            </el-descriptions>
            <el-empty v-else description="这一档没有扣压参数记录" :image-size="60" />
            <div class="qb-note">
              这几个值是机器和厂牌相关的，只能现场实测。第一根压完把量出来的值填到下面「扣压参数」页里，以后就有了。
            </div>
          </el-col>
        </el-row>

        <!-- 教程 -->
        <div class="block-title" style="margin-top:14px">怎么压</div>
        <ol class="steps">
          <li v-for="(s, i) in quote.steps" :key="i">{{ s }}</li>
        </ol>
      </div>
    </el-card>

    <!-- ============ 库存 ============ -->
    <el-card shadow="hover" style="margin-top:8px">
      <el-tabs v-model="tab" @tab-change="onTabChange">
        <!-- 胶管 -->
        <el-tab-pane label="胶管库存" name="hose">
          <div class="bar">
            <el-input v-model="specKeyword" placeholder="搜 1302 / 四分 / 二层" clearable
                      style="width:220px" @keyup.enter="loadSpecs" @clear="loadSpecs" />
            <el-checkbox v-model="specOnlyInStock" @change="loadSpecs">只看有货的</el-checkbox>
            <el-button icon="Search" @click="loadSpecs">查询</el-button>
            <span class="bar-tip">
              判断「接得了接不了」看的是<b>最长一段</b>，不是合计米数——余料不能接。
            </span>
          </div>
          <el-table v-loading="specLoading" :data="specList" size="small" border row-key="hoseCode">
            <el-table-column label="代号" prop="hoseCode" width="80" />
            <el-table-column label="规格" min-width="180">
              <template #default="{ row }">
                {{ row.nickname }} {{ row.inch }} · {{ row.layerName }}
              </template>
            </el-table-column>
            <el-table-column label="最长一段" width="100" align="right">
              <template #default="{ row }">
                <b :class="row.maxLengthM > 0 ? 'opt-ok' : 'opt-none'">{{ num(row.maxLengthM) }} 米</b>
              </template>
            </el-table-column>
            <el-table-column label="合计" width="90" align="right">
              <template #default="{ row }">{{ num(row.totalLengthM) }} 米</template>
            </el-table-column>
            <el-table-column label="分段" min-width="150">
              <template #default="{ row }">
                <span v-if="row.pieceText">{{ row.pieceCount }} 段：{{ row.pieceText }}</span>
                <span v-else class="opt-none">无库存</span>
              </template>
            </el-table-column>
            <el-table-column label="库位" min-width="140">
              <template #default="{ row }">{{ row.locationNames || '—' }}</template>
            </el-table-column>
            <el-table-column v-if="showCost" label="进价" width="120" align="right">
              <template #default="{ row }">
                <span v-if="row.costPrice != null">
                  {{ row.costPrice }} 元/米
                  <el-tag v-if="row.priceSource === '推算'" size="small" type="warning" effect="plain">推算</el-tag>
                </span>
                <span v-else class="no-price">—</span>
              </template>
            </el-table-column>
            <el-table-column label="工作压力" width="100" align="right">
              <template #default="{ row }">{{ dash(row.workPressureMpa, 'MPa') }}</template>
            </el-table-column>
          </el-table>

          <div class="bar" style="margin-top:12px">
            <span class="block-title" style="margin:0">在库分段明细</span>
            <el-button type="primary" plain icon="Plus" size="small"
                       v-hasPermi="['wms:hose:edit']" @click="openAddPiece">进新料</el-button>
          </div>
          <el-table v-loading="pieceLoading" :data="pieceList" size="small" border>
            <el-table-column label="代号" prop="hoseCode" width="80" />
            <el-table-column label="规格" min-width="170">
              <template #default="{ row }">{{ row.nickname }} {{ row.inch }} · {{ row.layerName }}</template>
            </el-table-column>
            <el-table-column label="长度" width="90" align="right">
              <template #default="{ row }">{{ num(row.lengthM) }} 米</template>
            </el-table-column>
            <el-table-column label="库位" min-width="150">
              <template #default="{ row }">{{ row.locationCode }} {{ row.locationName }}</template>
            </el-table-column>
            <el-table-column label="备注" prop="remark" min-width="130" />
            <el-table-column label="操作" width="150" align="right">
              <template #default="{ row }">
                <el-button link type="primary" v-hasPermi="['wms:hose:edit']" @click="openCut(row)">裁走</el-button>
                <el-button link type="danger" v-hasPermi="['wms:hose:edit']" @click="removePiece(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 接头 -->
        <el-tab-pane label="接头" name="fitting">
          <div class="bar">
            <el-input v-model="fittingQuery.keyword" placeholder="搜 22×1.5 / A型面 / G3/8" clearable
                      style="width:220px" @keyup.enter="loadFittings" @clear="loadFittings" />
            <el-select v-model="fittingQuery.threadSystem" placeholder="体系" clearable style="width:100px">
              <el-option label="公制" value="公制" />
              <el-option label="英制" value="英制" />
              <el-option label="美制" value="美制" />
            </el-select>
            <el-select v-model="fittingQuery.seatType" placeholder="型" clearable style="width:90px">
              <el-option label="A型" value="A" />
              <el-option label="C型" value="C" />
              <el-option label="D型" value="D" />
            </el-select>
            <el-select v-model="fittingQuery.gender" placeholder="芯/面" clearable style="width:95px">
              <el-option label="芯(公)" value="芯" />
              <el-option label="面(母)" value="面" />
            </el-select>
            <el-select v-model="fittingQuery.angle" placeholder="直/弯" clearable style="width:90px">
              <el-option label="直" value="直" />
              <el-option label="弯" value="弯" />
            </el-select>
            <el-checkbox v-model="fittingQuery.onlySeen">只看纸上有的</el-checkbox>
            <el-checkbox v-model="fittingQuery.onlyInStock">只看有货的</el-checkbox>
            <el-button type="primary" icon="Search" @click="loadFittings">查询</el-button>
            <el-button link type="primary" icon="Link" @click="goItem">去商品管理</el-button>
          </div>
          <el-alert type="info" :closable="false" show-icon style="margin-bottom:8px"
                    title="接头现在是普通商品：进货走入库单、卖出走出库单、盘点走盘点单，改库位和进价去商品管理。本页只读。空白 = 还没盘过，跟 0（盘过确认没有）不是一回事。" />
          <el-table v-loading="fittingLoading" :data="fittingList" size="small" border>
            <el-table-column label="现场叫法" prop="fieldName" min-width="150" />
            <el-table-column label="SKU" prop="fittingSku" min-width="140" />
            <el-table-column label="密封形式" min-width="180">
              <template #default="{ row }">
                <div>{{ row.seatType }}型 · {{ row.sealStd }}</div>
                <div class="line-sub">{{ row.stdCode }}</div>
              </template>
            </el-table-column>
            <el-table-column label="可配管通径(参考)" prop="boreHint" min-width="180" />
            <el-table-column label="库存" width="90" align="right">
              <template #default="{ row }">
                <span v-if="row.qty == null" class="opt-none">未盘</span>
                <span v-else :class="row.qty > 0 ? 'opt-ok' : 'opt-short'">{{ Number(row.qty) }} 个</span>
              </template>
            </el-table-column>
            <el-table-column label="库位" min-width="150">
              <template #default="{ row }">
                <span v-if="row.locationCode">{{ row.locationCode }} {{ row.locationName }}</span>
                <span v-else class="opt-none">未设</span>
              </template>
            </el-table-column>
            <el-table-column v-if="showCost" label="进价" width="100" align="right">
              <template #default="{ row }">
                <span v-if="row.costPrice != null">{{ row.costPrice }}</span>
                <span v-else class="no-price">—</span>
              </template>
            </el-table-column>
            <el-table-column label="纸上" width="60" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.seenOnSheet" type="success" size="small" effect="plain">有</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <pagination v-show="fittingTotal > 0" :total="fittingTotal"
                      v-model:page="fittingQuery.pageNum" v-model:limit="fittingQuery.pageSize"
                      @pagination="loadFittings" />
        </el-tab-pane>

        <!-- 外套 -->
        <el-tab-pane label="扣压外套" name="ferrule">
          <div class="bar">
            <el-input v-model="ferruleKeyword" placeholder="搜 F12 / 四分" clearable
                      style="width:200px" @keyup.enter="loadFerrules" @clear="loadFerrules" />
            <el-button icon="Search" @click="loadFerrules">查询</el-button>
            <el-button link type="primary" icon="Link" @click="goItem">去商品管理</el-button>
            <span class="bar-tip">三层管外径跟一二层不同，外套不通用，所以三层单独一档。库存与进价同样只读。</span>
          </div>
          <el-table v-loading="ferruleLoading" :data="ferruleList" size="small" border>
            <el-table-column label="SKU" prop="ferruleSku" width="150" />
            <el-table-column label="名称" prop="ferruleName" min-width="220" />
            <el-table-column label="适用层数" prop="layerScope" width="100" />
            <el-table-column label="剥皮" prop="skinType" width="80" />
            <el-table-column label="库存" width="90" align="right">
              <template #default="{ row }">
                <span v-if="row.qty == null" class="opt-none">未盘</span>
                <span v-else :class="row.qty > 0 ? 'opt-ok' : 'opt-short'">{{ Number(row.qty) }} 个</span>
              </template>
            </el-table-column>
            <el-table-column label="库位" min-width="150">
              <template #default="{ row }">
                <span v-if="row.locationCode">{{ row.locationCode }} {{ row.locationName }}</span>
                <span v-else class="opt-none">未设</span>
              </template>
            </el-table-column>
            <el-table-column v-if="showCost" label="进价" width="100" align="right">
              <template #default="{ row }">
                <span v-if="row.costPrice != null">{{ row.costPrice }}</span>
                <span v-else class="no-price">—</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 扣压参数 -->
        <el-tab-pane label="扣压参数" name="crimp">
          <div class="bar">
            <el-button type="success" plain icon="Check" :disabled="!crimpDirty"
                       v-hasPermi="['wms:hose:edit']" @click="saveCrimps">
              保存改动{{ crimpDirty ? `（${crimpDirty}）` : '' }}
            </el-button>
            <span class="bar-tip">
              这些值机器和厂牌相关，只能实测。「店里能压」取消勾选的档次，配料查询会直接判成「去仓库压」。
            </span>
          </div>
          <el-table v-loading="crimpLoading" :data="crimpList" size="small" border>
            <el-table-column label="适用层数" prop="layerScope" width="100" />
            <el-table-column label="通径" width="120">
              <template #default="{ row }">{{ row.boreCode }} {{ row.nickname }} {{ row.inch }}</template>
            </el-table-column>
            <el-table-column label="模具号" width="120">
              <template #default="{ row }">
                <el-input v-model="row.dieNo" size="small" placeholder="待实测" />
              </template>
            </el-table-column>
            <el-table-column label="扣压直径mm" width="120" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.crimpDiameterMm" :precision="2" :controls="false"
                                 size="small" placeholder="待实测" style="width:96px" />
              </template>
            </el-table-column>
            <el-table-column label="剥胶长度mm" width="120" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.stripLengthMm" :precision="1" :controls="false"
                                 size="small" placeholder="待实测" style="width:96px" />
              </template>
            </el-table-column>
            <el-table-column label="插入深度mm" width="120" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.insertDepthMm" :precision="1" :controls="false"
                                 size="small" placeholder="待实测" style="width:96px" />
              </template>
            </el-table-column>
            <el-table-column label="压机档位" width="110">
              <template #default="{ row }">
                <el-input v-model="row.pressGear" size="small" placeholder="待实测" />
              </template>
            </el-table-column>
            <el-table-column label="店里能压" width="90" align="center">
              <template #default="{ row }">
                <el-checkbox :model-value="row.shopCanCrimp === 1"
                             @update:model-value="v => row.shopCanCrimp = v ? 1 : 0" />
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 裁管 -->
    <el-dialog v-model="cutOpen" title="裁走一段" width="420px">
      <el-form label-width="90px">
        <el-form-item label="这一段">
          <span>{{ cutRow.hoseCode }} · {{ num(cutRow.lengthM) }} 米 · {{ cutRow.locationCode }}</span>
        </el-form-item>
        <el-form-item label="裁走(米)">
          <el-input-number v-model="cutUsed" :min="0.1" :max="Number(cutRow.lengthM) || 1"
                           :step="0.1" :precision="2" controls-position="right" />
        </el-form-item>
        <el-form-item label="剩下">
          <b>{{ (Number(cutRow.lengthM || 0) - Number(cutUsed || 0)).toFixed(2) }} 米</b>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cutOpen = false">取消</el-button>
        <el-button type="primary" @click="doCut">确认裁走</el-button>
      </template>
    </el-dialog>

    <!-- 进新料 -->
    <el-dialog v-model="addOpen" title="进新料（新增一段胶管）" width="440px">
      <el-form label-width="90px">
        <el-form-item label="胶管规格">
          <el-select v-model="addForm.hoseCode" filterable placeholder="选规格" style="width:100%">
            <el-option v-for="s in specOptions" :key="s.hoseCode"
                       :label="`${s.hoseCode}  ${s.nickname} ${s.inch} ${s.layerName}`"
                       :value="s.hoseCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="长度(米)">
          <el-input-number v-model="addForm.lengthM" :min="0.1" :step="0.5" :precision="2"
                           controls-position="right" />
        </el-form-item>
        <el-form-item label="库位">
          <el-select v-model="addForm.locationId" filterable clearable placeholder="选库位" style="width:100%">
            <el-option v-for="l in locationList" :key="l.id"
                       :label="`${l.locationCode} ${l.locationName || ''}`" :value="l.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="addForm.remark" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addOpen = false">取消</el-button>
        <el-button type="primary" @click="doAddPiece">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Hose">
import { Van, Star, StarFilled } from '@element-plus/icons-vue'
import {
  listHoseSpec, listHosePiece, listHoseFitting, optionsHoseFitting,
  listHoseFerrule, listHoseCrimp, quoteHose, saveHoseCrimp,
  addHosePiece, cutHosePiece, delHosePiece
} from '@/api/wms/hose'
import { listLocationNoPage } from '@/api/wms/location'
import { useRouter } from 'vue-router'

const { proxy } = getCurrentInstance()
const router = useRouter()

/** 接头/外套现在是普通商品，改库位和进价去商品管理 */
function goItem() {
  router.push({ path: '/basic/item' })
}

/* ---------------- 公共 ---------------- */

const locationList = ref([])
const specOptions = ref([])

/**
 * 进价是否显示。默认藏着 —— 柜台屏幕客户看得见，售价可以给他看，进价不行。
 * 整页共用一个开关：配料单的小计、报价框里的进价、三个库存页签的进价列一起显隐。
 */
const showCost = ref(false)

function num(v) {
  if (v === null || v === undefined || v === '') return '0'
  return String(Number(v))
}

function dash(v, unit) {
  if (v === null || v === undefined || v === '') return '待实测'
  return unit ? `${Number(v)} ${unit}` : v
}

function stockClass(qty) {
  if (qty == null) return 'opt-none'
  return qty > 0 ? 'opt-ok' : 'opt-short'
}

function statusType(s) {
  return { 够: 'success', 缺: 'danger', 未盘: 'warning', 无档案: 'info' }[s] || 'info'
}

/* ---------------- 配料查询 ---------------- */

const quoteFormRef = ref()
const quoting = ref(false)
const quote = ref(null)
const quoteForm = reactive({
  hoseCode: undefined,
  lengthM: 1,
  assemblyQty: 1,
  skinType: '非剥皮',
  endASku: undefined,
  endBSku: undefined
})
const quoteRules = {
  hoseCode: [{ required: true, message: '选一个胶管规格', trigger: 'change' }],
  lengthM: [{ required: true, message: '填长度', trigger: 'blur' }]
}

const verdictType = computed(() => {
  if (!quote.value) return 'info'
  if (quote.value.canCrimp) return 'success'
  return quote.value.verdict === '去仓库压' ? 'warning' : 'error'
})

function usable(row) {
  return Number(row.lengthM) >= Number(quoteForm.lengthM || 0)
}

const fittingOptions = ref([])
const fittingSearching = ref(false)

function searchFitting(kw) {
  fittingSearching.value = true
  optionsHoseFitting(kw).then(res => {
    fittingOptions.value = res.data || []
  }).finally(() => {
    fittingSearching.value = false
  })
}

function onSpecChange() {
  // 换规格后原来的结果就不对了，清掉免得看串
  quote.value = null
}

function doQuote() {
  quoteFormRef.value.validate(valid => {
    if (!valid) return
    quoting.value = true
    quoteHose({ ...quoteForm }).then(res => {
      quote.value = res.data
    }).finally(() => {
      quoting.value = false
    })
  })
}

function resetQuote() {
  quote.value = null
  quoteForm.hoseCode = undefined
  quoteForm.lengthM = 1
  quoteForm.assemblyQty = 1
  quoteForm.skinType = '非剥皮'
  quoteForm.endASku = undefined
  quoteForm.endBSku = undefined
}

/* ---------------- 胶管 ---------------- */

const tab = ref('hose')
const specLoading = ref(false)
const specList = ref([])
const specKeyword = ref('')
const specOnlyInStock = ref(false)
const pieceLoading = ref(false)
const pieceList = ref([])

function loadSpecs() {
  specLoading.value = true
  listHoseSpec({ keyword: specKeyword.value, onlyInStock: specOnlyInStock.value })
    .then(res => { specList.value = res.data || [] })
    .finally(() => { specLoading.value = false })
}

function loadPieces() {
  pieceLoading.value = true
  listHosePiece({}).then(res => { pieceList.value = res.data || [] })
    .finally(() => { pieceLoading.value = false })
}

/* 裁管 */
const cutOpen = ref(false)
const cutRow = ref({})
const cutUsed = ref(1)

function openCut(row) {
  cutRow.value = { ...row }
  cutUsed.value = Math.min(Number(quoteForm.lengthM) || 1, Number(row.lengthM))
  cutOpen.value = true
}

function doCut() {
  cutHosePiece(cutRow.value.id, cutUsed.value).then(() => {
    proxy.$modal.msgSuccess('已扣减')
    cutOpen.value = false
    refreshHoseTab()
    if (quote.value) doQuote()
  })
}

/* 进新料 */
const addOpen = ref(false)
const addForm = reactive({ hoseCode: undefined, lengthM: 20, locationId: undefined, remark: '' })

function openAddPiece() {
  addForm.hoseCode = quoteForm.hoseCode
  addForm.lengthM = 20
  addForm.locationId = undefined
  addForm.remark = ''
  addOpen.value = true
}

function doAddPiece() {
  if (!addForm.hoseCode) {
    proxy.$modal.msgWarning('选一个胶管规格')
    return
  }
  addHosePiece({ ...addForm }).then(() => {
    proxy.$modal.msgSuccess('已新增')
    addOpen.value = false
    refreshHoseTab()
  })
}

function removePiece(row) {
  proxy.$modal.confirm(`删掉 ${row.hoseCode} 这段 ${num(row.lengthM)} 米？只在录错时才删，正常用掉请用「裁走」。`)
    .then(() => delHosePiece(row.id))
    .then(() => {
      proxy.$modal.msgSuccess('已删除')
      refreshHoseTab()
    })
    .catch(() => {})
}

function refreshHoseTab() {
  loadSpecs()
  loadPieces()
  listHoseSpec({}).then(res => { specOptions.value = res.data || [] })
}

/* ---------------- 接头 ---------------- */

const fittingLoading = ref(false)
const fittingList = ref([])
const fittingTotal = ref(0)
const fittingQuery = reactive({
  pageNum: 1, pageSize: 20, keyword: '',
  threadSystem: undefined, seatType: undefined, gender: undefined, angle: undefined,
  onlySeen: false, onlyInStock: false
})

function loadFittings() {
  fittingLoading.value = true
  listHoseFitting(fittingQuery).then(res => {
    fittingList.value = res.rows || []
    fittingTotal.value = res.total || 0
  }).finally(() => { fittingLoading.value = false })
}

/* ---------------- 外套 ---------------- */

const ferruleLoading = ref(false)
const ferruleList = ref([])
const ferruleKeyword = ref('')

function loadFerrules() {
  ferruleLoading.value = true
  listHoseFerrule({ keyword: ferruleKeyword.value }).then(res => {
    ferruleList.value = res.data || []
  }).finally(() => { ferruleLoading.value = false })
}

/* ---------------- 扣压参数 ---------------- */

const crimpLoading = ref(false)
const crimpList = ref([])
const crimpSnapshot = ref(new Map())

function crimpKey(r) {
  return [r.dieNo, r.crimpDiameterMm, r.stripLengthMm, r.insertDepthMm, r.pressGear, r.shopCanCrimp]
    .map(v => v ?? '').join('|')
}

const crimpDirty = computed(() =>
  crimpList.value.filter(r => crimpSnapshot.value.get(r.id) !== crimpKey(r)).length)

function loadCrimps() {
  crimpLoading.value = true
  listHoseCrimp().then(res => {
    crimpList.value = res.data || []
    crimpSnapshot.value = new Map(crimpList.value.map(r => [r.id, crimpKey(r)]))
  }).finally(() => { crimpLoading.value = false })
}

function saveCrimps() {
  const changed = crimpList.value.filter(r => crimpSnapshot.value.get(r.id) !== crimpKey(r))
  if (!changed.length) return
  saveHoseCrimp(changed.map(r => ({
    id: r.id, dieNo: r.dieNo, crimpDiameterMm: r.crimpDiameterMm,
    stripLengthMm: r.stripLengthMm, insertDepthMm: r.insertDepthMm,
    pressGear: r.pressGear, shopCanCrimp: r.shopCanCrimp
  }))).then(() => {
    proxy.$modal.msgSuccess(`已保存 ${changed.length} 条`)
    loadCrimps()
  })
}

/* ---------------- 初始化 ---------------- */

function onTabChange(name) {
  if (name === 'fitting' && !fittingList.value.length) loadFittings()
  if (name === 'ferrule' && !ferruleList.value.length) loadFerrules()
  if (name === 'crimp' && !crimpList.value.length) loadCrimps()
}

onMounted(() => {
  listLocationNoPage({}).then(res => { locationList.value = res.data || res.rows || [] })
  listHoseSpec({}).then(res => {
    specOptions.value = res.data || []
    specList.value = specOptions.value
  })
  loadPieces()
  searchFitting('')
})
</script>

<style scoped>
.card-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.card-title { font-size: 15px; font-weight: 600; }
.card-tip { font-size: 12px; color: var(--el-text-color-secondary); }

.quote-result { margin-top: 6px; }
.verdict { font-size: 15px; font-weight: 700; }
.summary { margin-top: 2px; line-height: 1.6; }

.go-warehouse {
  display: flex; align-items: center; gap: 12px;
  margin-top: 10px; padding: 12px 16px; border-radius: 6px;
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-5);
}
.go-warehouse .el-icon { font-size: 28px; color: var(--el-color-warning); }
.gw-title { font-size: 16px; font-weight: 700; color: var(--el-color-warning-dark-2); }
.gw-sub { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 2px; }

.block-title {
  font-size: 13px; font-weight: 600; margin-bottom: 6px;
  color: var(--el-text-color-primary);
}
.line-name { font-weight: 600; }
.line-sub { font-size: 12px; color: var(--el-text-color-secondary); }
.no-loc { color: var(--el-color-warning); }
.no-price { color: var(--el-color-warning); font-size: 12px; }

.msg-list { margin-top: 8px; font-size: 13px; line-height: 1.8; }
.msg-list.blockers { color: var(--el-color-danger); }
.msg-list.warnings { color: var(--el-color-warning); }

.cost-toggle { margin-left: auto; }
.cost-toggle.active {
  color: var(--el-color-warning);
  border-color: var(--el-color-warning);
}

.sell-price { display: flex; align-items: baseline; gap: 6px; }
.sp-label { font-size: 13px; color: var(--el-text-color-secondary); }
.sp-value { font-size: 30px; font-weight: 700; color: var(--el-color-danger); line-height: 1.2; }
.sp-unit { font-size: 14px; color: var(--el-text-color-secondary); }

.quote-box {
  padding: 12px 14px; border-radius: 6px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}
.qb-row { display: flex; justify-content: space-between; align-items: baseline; padding: 3px 0; }
.qb-row b { font-size: 17px; }
.qb-row .p1 { color: var(--el-color-info); }
.qb-row .p2 { color: var(--el-color-primary); }
.qb-row .p3 { color: var(--el-color-success); }
.qb-warn { font-size: 12px; color: var(--el-color-warning); margin-top: 4px; }
.qb-note { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 8px; line-height: 1.6; }

.steps { margin: 0; padding-left: 22px; line-height: 2; }
.steps li { font-size: 13px; }

.bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
.bar-tip { font-size: 12px; color: var(--el-text-color-secondary); }

.opt-ok { color: var(--el-color-success); }
.opt-none { color: var(--el-text-color-placeholder); }
.opt-short { color: var(--el-color-danger); }
</style>
