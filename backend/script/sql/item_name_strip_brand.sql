-- 商品名称去品牌后缀：把「SC气缸（东特）」改成「SC气缸」，品牌信息放到 item_brand 字段。
-- 只处理括号内容能对上品牌资料（含简称）的名称，「（内藏式）」「（黄铜）」等规格说明保留。
-- 幂等：UPDATE 带 item_name 现值守卫，重复执行是空操作；备份表已存在则不再覆盖。
-- 回滚：执行同目录 item_name_strip_brand_rollback.sql（基于备份表逐行还原）。

CREATE TABLE IF NOT EXISTS wms_item_name_backup_20260814 AS
  SELECT id, item_name, item_brand FROM wms_item;

UPDATE wms_item SET item_name = 'SC气缸' WHERE id = 1945743292155072513 AND item_name = 'SC气缸（东特）'; -- 品牌：东特
UPDATE wms_item SET item_name = 'PC螺纹直通' WHERE id = 1946493336743350273 AND item_name = 'PC螺纹直通（东特）'; -- 品牌：东特
UPDATE wms_item SET item_name = 'PCF内螺纹直通' WHERE id = 1946494988745478147 AND item_name = 'PCF内螺纹直通（东特）'; -- 品牌：东特
UPDATE wms_item SET item_name = 'PL L型螺纹二通' WHERE id = 1946494988745478148 AND item_name = 'PL L型螺纹二通（东特）'; -- 品牌：东特
UPDATE wms_item SET item_name = 'DPLL L型加长螺纹二通' WHERE id = 1946494988745478150 AND item_name = 'DPLL L型加长螺纹二通（山克斯）'; -- 品牌：山克斯（SUNCOS）
UPDATE wms_item SET item_name = 'PV L型二通' WHERE id = 1946494988745478151 AND item_name = 'PV L型二通（东特）'; -- 品牌：东特
UPDATE wms_item SET item_name = 'DPU直通' WHERE id = 1946494988745478152 AND item_name = 'DPU直通（东特）'; -- 品牌：东特
UPDATE wms_item SET item_name = 'PG 变径直通' WHERE id = 1946494988745478153 AND item_name = 'PG 变径直通（东特）'; -- 品牌：东特
UPDATE wms_item SET item_name = 'DPE T型三通' WHERE id = 1946494988745478155 AND item_name = 'DPE T型三通（东特）'; -- 品牌：东特
UPDATE wms_item SET item_name = 'PEG T型三通' WHERE id = 1946494988745478156 AND item_name = 'PEG T型三通（东特）'; -- 品牌：东特
UPDATE wms_item SET item_name = 'PY Y型三通' WHERE id = 1946494988745478157 AND item_name = 'PY Y型三通（东特）'; -- 品牌：东特
UPDATE wms_item SET item_name = 'DPW Y型变径' WHERE id = 1946494988745478158 AND item_name = 'DPW Y型变径（东特）'; -- 品牌：东特
UPDATE wms_item SET item_name = 'DPX Y型三通带螺纹' WHERE id = 1946494988745478159 AND item_name = 'DPX Y型三通带螺纹（山克斯）'; -- 品牌：山克斯（SUNCOS）
UPDATE wms_item SET item_name = 'DPZA十字四通' WHERE id = 1946494988745478160 AND item_name = 'DPZA十字四通（东特）'; -- 品牌：东特
UPDATE wms_item SET item_name = 'PK五通' WHERE id = 1946494988745478161 AND item_name = 'PK五通（山克斯）'; -- 品牌：山克斯（SUNCOS）
UPDATE wms_item SET item_name = 'SL（JSC） L型节流阀' WHERE id = 1946494988745478166 AND item_name = 'SL（JSC） L型节流阀（东特）'; -- 品牌：东特
UPDATE wms_item SET item_name = 'SDA薄型气缸' WHERE id = 1947135347628478465 AND item_name = 'SDA薄型气缸（东特）'; -- 品牌：东特
UPDATE wms_item SET item_name = 'PE管' WHERE id = 1947235691024912385 AND item_name = 'PE管（环球牌）'; -- 品牌：环球牌
UPDATE wms_item SET item_name = '电磁水阀' WHERE id = 1950103654488907777 AND item_name = '电磁水阀（海达）'; -- 品牌：海达（HNDA）
UPDATE wms_item SET item_name = '电磁水阀' WHERE id = 1950136556102656001 AND item_name = '电磁水阀（神州）'; -- 品牌：神州
UPDATE wms_item SET item_name = '气动手动阀' WHERE id = 1950136772511965185 AND item_name = '气动手动阀（山耐斯）'; -- 品牌：山耐斯
UPDATE wms_item SET item_name = '气动手动阀' WHERE id = 1950137076271849473 AND item_name = '气动手动阀（卓良）'; -- 品牌：卓良(zholo)
UPDATE wms_item SET item_name = '气动手动阀' WHERE id = 1950137211735285762 AND item_name = '气动手动阀（盛达）'; -- 品牌：盛达(SDPC)
UPDATE wms_item SET item_name = '气动电磁阀' WHERE id = 1957291547699494914 AND item_name = '气动电磁阀（盛达）'; -- 品牌：盛达(SDPC)
UPDATE wms_item SET item_name = '气动电磁阀' WHERE id = 1957293094353289218 AND item_name = '气动电磁阀(Mindman)'; -- 品牌：Mindman
UPDATE wms_item SET item_name = '气动电磁阀' WHERE id = 1957293430182821890 AND item_name = '气动电磁阀(JYC)'; -- 品牌：JYC
UPDATE wms_item SET item_name = '气动电磁阀' WHERE id = 1957293660122955777 AND item_name = '气动电磁阀(亚德客)'; -- 品牌：亚德客(AirTac)
UPDATE wms_item SET item_name = '气动电磁阀' WHERE id = 1957293832794062849 AND item_name = '气动电磁阀(亨博电磁)'; -- 品牌：亨博电磁
UPDATE wms_item SET item_name = '气动电磁阀' WHERE id = 1957294149786976257 AND item_name = '气动电磁阀(卓良)'; -- 品牌：卓良(zholo)
UPDATE wms_item SET item_name = '气动电磁阀' WHERE id = 1957296908615598081 AND item_name = '气动电磁阀(TSPC)'; -- 品牌：天盛(TSPC)
UPDATE wms_item SET item_name = '气动电磁阀' WHERE id = 1957322438400327682 AND item_name = '气动电磁阀(威特)'; -- 品牌：威特
UPDATE wms_item SET item_name = '液压电磁阀' WHERE id = 1957324018298175489 AND item_name = '液压电磁阀(三阳)'; -- 品牌：三阳
UPDATE wms_item SET item_name = '液压电磁阀' WHERE id = 1957406146239184898 AND item_name = '液压电磁阀(威特)'; -- 品牌：威特
UPDATE wms_item SET item_name = '液压电磁阀' WHERE id = 1957406552268783618 AND item_name = '液压电磁阀(YOULIDA)'; -- 品牌：YOULIDA
UPDATE wms_item SET item_name = '液压电磁阀' WHERE id = 1957406965848129538 AND item_name = '液压电磁阀(LIANXING)'; -- 品牌：联心(LIANXING)
UPDATE wms_item SET item_name = '液压电磁阀' WHERE id = 1957407323622260737 AND item_name = '液压电磁阀(XD)'; -- 品牌：XD
UPDATE wms_item SET item_name = '液压电磁阀' WHERE id = 1957407646273290242 AND item_name = '液压电磁阀(万尔福)'; -- 品牌：万尔福(WANERF)
UPDATE wms_item SET item_name = '吹尘枪' WHERE id = 1957705468256239617 AND item_name = '吹尘枪(DADA)'; -- 品牌：DADA
UPDATE wms_item SET item_name = '吹尘枪' WHERE id = 1957705592567021569 AND item_name = '吹尘枪(海达)'; -- 品牌：海达（HNDA）
UPDATE wms_item SET item_name = '吹尘枪' WHERE id = 1957705703200178178 AND item_name = '吹尘枪(AT10)'; -- 品牌：AT10
UPDATE wms_item SET item_name = '脚踏阀' WHERE id = 1958493579299012609 AND item_name = '脚踏阀（SDPC）'; -- 品牌：盛达(SDPC)
UPDATE wms_item SET item_name = '调压阀' WHERE id = 1958497020146630657 AND item_name = '调压阀（亚德客）'; -- 品牌：亚德客(AirTac)
UPDATE wms_item SET item_name = '调压阀' WHERE id = 1958505996359864322 AND item_name = '调压阀（盛达）'; -- 品牌：盛达(SDPC)
UPDATE wms_item SET item_name = '伸缩管' WHERE id = 1958814887828295681 AND item_name = '伸缩管（环球牌）'; -- 品牌：环球牌
UPDATE wms_item SET item_name = '调压过滤器' WHERE id = 1959065767781634050 AND item_name = '调压过滤器（盛达）'; -- 品牌：盛达(SDPC)
UPDATE wms_item SET item_name = '调压过滤器' WHERE id = 1959066154215444481 AND item_name = '调压过滤器（海达）'; -- 品牌：海达（HNDA）
UPDATE wms_item SET item_name = '调压过滤器' WHERE id = 1959067125582696449 AND item_name = '调压过滤器（亚德客）'; -- 品牌：亚德客(AirTac)
UPDATE wms_item SET item_name = '调压过滤器' WHERE id = 1959536456372314113 AND item_name = '调压过滤器（宝丰）'; -- 品牌：宝丰（BAOF）
UPDATE wms_item SET item_name = 'SDA薄型气缸' WHERE id = 1961325380799344642 AND item_name = 'SDA薄型气缸（盛达）'; -- 品牌：盛达(SDPC)
UPDATE wms_item SET item_name = 'SDA薄型气缸' WHERE id = 1961325847285641218 AND item_name = 'SDA薄型气缸（三川）'; -- 品牌：三川气动（SACU）
UPDATE wms_item SET item_name = 'SDA薄型气缸' WHERE id = 1961326369019310082 AND item_name = 'SDA薄型气缸（JLC）'; -- 品牌：JLC
UPDATE wms_item SET item_name = 'SDA薄型气缸' WHERE id = 1961326478096379906 AND item_name = 'SDA薄型气缸（CHTD）'; -- 品牌：CHTD
UPDATE wms_item SET item_name = 'SDA薄型气缸' WHERE id = 1961326574124969985 AND item_name = 'SDA薄型气缸（宝诚）'; -- 品牌：宝诚（CNB&C）
UPDATE wms_item SET item_name = 'SDA薄型气缸' WHERE id = 1961328203100368898 AND item_name = 'SDA薄型气缸（亚德客）'; -- 品牌：亚德客(AirTac)
UPDATE wms_item SET item_name = 'SDA薄型气缸' WHERE id = 1961330719770849282 AND item_name = 'SDA薄型气缸（卓良）'; -- 品牌：卓良(zholo)
UPDATE wms_item SET item_name = 'SDA薄型气缸' WHERE id = 1961343180108509186 AND item_name = 'SDA薄型气缸（JYC）'; -- 品牌：JYC
UPDATE wms_item SET item_name = 'SDA薄型气缸' WHERE id = 1961343322622570498 AND item_name = 'SDA薄型气缸（JG）'; -- 品牌：JG
UPDATE wms_item SET item_name = 'SDA薄型气缸' WHERE id = 1961343501874540545 AND item_name = 'SDA薄型气缸（SNRCE）'; -- 品牌：SNRCE
UPDATE wms_item SET item_name = 'SDA薄型气缸' WHERE id = 1961344870886977537 AND item_name = 'SDA薄型气缸（三正）'; -- 品牌：三正气动
UPDATE wms_item SET item_name = 'SDA薄型气缸' WHERE id = 1961350413374500865 AND item_name = 'SDA薄型气缸（菲特）'; -- 品牌：菲特气动
UPDATE wms_item SET item_name = 'ACQ薄型气缸' WHERE id = 1961355004363190273 AND item_name = 'ACQ薄型气缸（威特）'; -- 品牌：威特
UPDATE wms_item SET item_name = '调压过滤器' WHERE id = 1961752195116367873 AND item_name = '调压过滤器（山耐斯）'; -- 品牌：山耐斯
UPDATE wms_item SET item_name = '调压阀' WHERE id = 1961752423773044737 AND item_name = '调压阀（宝丰）'; -- 品牌：宝丰（BAOF）
UPDATE wms_item SET item_name = '调压阀' WHERE id = 1961752527825338369 AND item_name = '调压阀（海达）'; -- 品牌：海达（HNDA）
UPDATE wms_item SET item_name = '调压阀' WHERE id = 1961752633286918146 AND item_name = '调压阀（百灵）'; -- 品牌：百灵（BLCH）
UPDATE wms_item SET item_name = '电磁水阀' WHERE id = 1961760984506892290 AND item_name = '电磁水阀（恒荣）'; -- 品牌：恒荣（地球）
UPDATE wms_item SET item_name = '电磁水阀' WHERE id = 1961761450636673026 AND item_name = '电磁水阀（盛达）'; -- 品牌：盛达(SDPC)
UPDATE wms_item SET item_name = '电磁水阀' WHERE id = 1961761688059445250 AND item_name = '电磁水阀（卓良）'; -- 品牌：卓良(zholo)
UPDATE wms_item SET item_name = '气动电磁阀' WHERE id = 1961763591795310594 AND item_name = '气动电磁阀（MCF）'; -- 品牌：MCF
UPDATE wms_item SET item_name = '气动电磁阀' WHERE id = 1961764406509502465 AND item_name = '气动电磁阀（VERMD）'; -- 品牌：唯尔美德（VERMD）
UPDATE wms_item SET item_name = '调压阀' WHERE id = 1961768205739438082 AND item_name = '调压阀（MODEL）'; -- 品牌：MODEL
UPDATE wms_item SET item_name = '气动电磁阀' WHERE id = 1961768608409399297 AND item_name = '气动电磁阀（KSD）'; -- 品牌：KSD
UPDATE wms_item SET item_name = '制冷电磁阀' WHERE id = 1961769205611180034 AND item_name = '制冷电磁阀（百达）'; -- 品牌：百达
UPDATE wms_item SET item_name = '气动电磁阀' WHERE id = 1961769854637780993 AND item_name = '气动电磁阀（ZLPC）'; -- 品牌：ZLPC
UPDATE wms_item SET item_name = 'SC气缸' WHERE id = 1962408389208256513 AND item_name = 'SC气缸（盛达）'; -- 品牌：盛达(SDPC)
UPDATE wms_item SET item_name = 'SC气缸' WHERE id = 1962409671482486785 AND item_name = 'SC气缸（三川）'; -- 品牌：三川气动（SACU）
UPDATE wms_item SET item_name = 'SC气缸' WHERE id = 1962410193425870849 AND item_name = 'SC气缸（HJNN）'; -- 品牌：HJNN
UPDATE wms_item SET item_name = 'SC气缸' WHERE id = 1962411008316223490 AND item_name = 'SC气缸（CHTD）'; -- 品牌：CHTD
UPDATE wms_item SET item_name = 'SC气缸' WHERE id = 1962411154781319169 AND item_name = 'SC气缸（宝诚）'; -- 品牌：宝诚（CNB&C）
UPDATE wms_item SET item_name = 'SC气缸' WHERE id = 1962411231373504513 AND item_name = 'SC气缸（CHLED）'; -- 品牌：CHLED
UPDATE wms_item SET item_name = 'SC气缸' WHERE id = 1962411700590292993 AND item_name = 'SC气缸（亚邦）'; -- 品牌：亚邦（YABANG）
UPDATE wms_item SET item_name = 'SC气缸' WHERE id = 1962412150555226114 AND item_name = 'SC气缸（威特）'; -- 品牌：威特
UPDATE wms_item SET item_name = 'SC气缸' WHERE id = 1962412263461695489 AND item_name = 'SC气缸（亚德客）'; -- 品牌：亚德客(AirTac)
UPDATE wms_item SET item_name = 'SC气缸' WHERE id = 1962421158460600322 AND item_name = 'SC气缸（卓良）'; -- 品牌：卓良(zholo)
UPDATE wms_item SET item_name = '电磁水阀' WHERE id = 1962482885785104385 AND item_name = '电磁水阀（YPCN）'; -- 品牌：YPCN
UPDATE wms_item SET item_name = '电磁水阀' WHERE id = 1962486792531816449 AND item_name = '电磁水阀（永灵）'; -- 品牌：永灵
UPDATE wms_item SET item_name = 'C式接头' WHERE id = 1962779204617498625 AND item_name = 'C式接头（明铁）'; -- 品牌：明铁
UPDATE wms_item SET item_name = 'C式接头' WHERE id = 1962781170483589121 AND item_name = 'C式接头（JCP）'; -- 品牌：JCP
UPDATE wms_item SET item_name = 'C式接头' WHERE id = 1962781644771291138 AND item_name = 'C式接头（TRM）'; -- 品牌：TRM
UPDATE wms_item SET item_name = 'C式接头' WHERE id = 1962781827231903745 AND item_name = 'C式接头（新禾）'; -- 品牌：新禾
UPDATE wms_item SET item_name = 'C式自锁接头' WHERE id = 1962782050490511361 AND item_name = 'C式自锁接头（明铁）'; -- 品牌：明铁
UPDATE wms_item SET item_name = 'SPC螺纹直通' WHERE id = 1966314375321075714 AND item_name = 'SPC螺纹直通（山克斯）'; -- 品牌：山克斯（SUNCOS）
UPDATE wms_item SET item_name = 'PC螺纹直通' WHERE id = 1966319373098532865 AND item_name = 'PC螺纹直通（气动之星）'; -- 品牌：气动之星（CYKL）
UPDATE wms_item SET item_name = 'PC螺纹直通' WHERE id = 1966320110574616578 AND item_name = 'PC螺纹直通（Xing）'; -- 品牌：Xing
UPDATE wms_item SET item_name = 'DPL L型螺纹二通' WHERE id = 1966771925128691714 AND item_name = 'DPL L型螺纹二通（山克斯）'; -- 品牌：山克斯（SUNCOS）
UPDATE wms_item SET item_name = 'DPL L型螺纹二通' WHERE id = 1966773001177382914 AND item_name = 'DPL L型螺纹二通（气动之星）'; -- 品牌：气动之星（CYKL）
UPDATE wms_item SET item_name = 'DPL L型螺纹二通' WHERE id = 1966773416161820673 AND item_name = 'DPL L型螺纹二通（亚一气动）'; -- 品牌：亚一气动（YYC）
UPDATE wms_item SET item_name = 'DPL L型螺纹二通' WHERE id = 1966773823424544769 AND item_name = 'DPL L型螺纹二通（MYA00）'; -- 品牌：MYA00
UPDATE wms_item SET item_name = 'SPD螺纹T型' WHERE id = 1968595278525202433 AND item_name = 'SPD螺纹T型（山克斯）'; -- 品牌：山克斯（SUNCOS）
UPDATE wms_item SET item_name = 'SPD螺纹T 型' WHERE id = 1968595973563318273 AND item_name = 'SPD螺纹T 型（三正气动）'; -- 品牌：三正气动
UPDATE wms_item SET item_name = 'PZA十字四通' WHERE id = 1968596938991435778 AND item_name = 'PZA十字四通（山克斯）'; -- 品牌：山克斯（SUNCOS）
UPDATE wms_item SET item_name = 'PB螺纹T型上面螺丝' WHERE id = 1968608801829519362 AND item_name = 'PB螺纹T型上面螺丝（山克斯）'; -- 品牌：山克斯（SUNCOS）
UPDATE wms_item SET item_name = 'PB螺纹T型上面螺纹' WHERE id = 1968609271880974337 AND item_name = 'PB螺纹T型上面螺纹（三正气动）'; -- 品牌：三正气动
UPDATE wms_item SET item_name = 'PE T型三通' WHERE id = 1968614244203200514 AND item_name = 'PE T型三通（山克斯）'; -- 品牌：山克斯（SUNCOS）
UPDATE wms_item SET item_name = 'PEG T型三通' WHERE id = 1968615071730991106 AND item_name = 'PEG T型三通（山克斯）'; -- 品牌：山克斯（SUNCOS）
UPDATE wms_item SET item_name = 'PE Y型三通' WHERE id = 1968618218532732929 AND item_name = 'PE Y型三通（亚一气动）'; -- 品牌：亚一气动（YYC）
UPDATE wms_item SET item_name = 'PU直通' WHERE id = 1968635061704163330 AND item_name = 'PU直通（山克斯）'; -- 品牌：山克斯（SUNCOS）
UPDATE wms_item SET item_name = 'PU直通' WHERE id = 1968635264612007937 AND item_name = 'PU直通（亚一气动）'; -- 品牌：亚一气动（YYC）
UPDATE wms_item SET item_name = 'PU直通' WHERE id = 1968635362888744962 AND item_name = 'PU直通（气动之星）'; -- 品牌：气动之星（CYKL）
UPDATE wms_item SET item_name = 'PG变经直通' WHERE id = 1968635804498624514 AND item_name = 'PG变经直通（山克斯）'; -- 品牌：山克斯（SUNCOS）
UPDATE wms_item SET item_name = 'PG变经直通' WHERE id = 1968635986132959234 AND item_name = 'PG变经直通（亚一气动）'; -- 品牌：亚一气动（YYC）
UPDATE wms_item SET item_name = 'PG变经直通' WHERE id = 1968636092005580801 AND item_name = 'PG变经直通（气动之星）'; -- 品牌：气动之星（CYKL）
UPDATE wms_item SET item_name = 'PY-Y型三通' WHERE id = 1970311721994158082 AND item_name = 'PY-Y型三通（山克斯）'; -- 品牌：山克斯（SUNCOS）
UPDATE wms_item SET item_name = 'PY-Y型三通' WHERE id = 1970312612293902337 AND item_name = 'PY-Y型三通（亚一气动）'; -- 品牌：亚一气动（YYC）
UPDATE wms_item SET item_name = 'DPW Y型变径' WHERE id = 1970313131716509697 AND item_name = 'DPW Y型变径（山克斯）'; -- 品牌：山克斯（SUNCOS）
UPDATE wms_item SET item_name = 'PV L型二通' WHERE id = 1970314825875587074 AND item_name = 'PV L型二通（山克斯）'; -- 品牌：山克斯（SUNCOS）
UPDATE wms_item SET item_name = 'PV L型二通' WHERE id = 1970315066968375297 AND item_name = 'PV L型二通（亚一气动）'; -- 品牌：亚一气动（YYC）
UPDATE wms_item SET item_name = 'SL（JSC） L型节流阀' WHERE id = 1970318014079303681 AND item_name = 'SL（JSC） L型节流阀（山克斯）'; -- 品牌：山克斯（SUNCOS）
UPDATE wms_item SET item_name = 'SL（JSC） L型节流阀' WHERE id = 1970318212104978434 AND item_name = 'SL（JSC） L型节流阀（气动之星）'; -- 品牌：气动之星（CYKL）
UPDATE wms_item SET item_name = 'PCF内螺纹直通' WHERE id = 1970418590469324801 AND item_name = 'PCF内螺纹直通（山克斯）'; -- 品牌：山克斯（SUNCOS）
UPDATE wms_item SET item_name = 'PCF内螺纹直通' WHERE id = 1970418785307328513 AND item_name = 'PCF内螺纹直通（三正）'; -- 品牌：三正气动
UPDATE wms_item SET item_name = 'PM隔板直通' WHERE id = 1970419305442967553 AND item_name = 'PM隔板直通（东特）'; -- 品牌：东特
UPDATE wms_item SET item_name = 'PM隔板直通' WHERE id = 1970419718833569793 AND item_name = 'PM隔板直通（山克斯）'; -- 品牌：山克斯（SUNCOS）
UPDATE wms_item SET item_name = 'ZPL L型螺纹二通' WHERE id = 1970422024215986178 AND item_name = 'ZPL L型螺纹二通（亿日）'; -- 品牌：亿日
UPDATE wms_item SET item_name = 'DSA管道阀' WHERE id = 1970828506967310338 AND item_name = 'DSA管道阀（东特）'; -- 品牌：东特
UPDATE wms_item SET item_name = 'DSA管道阀' WHERE id = 1970828628845395970 AND item_name = 'DSA管道阀（山克斯）'; -- 品牌：山克斯（SUNCOS）
UPDATE wms_item SET item_name = '脚踏阀' WHERE id = 1992810813500506114 AND item_name = '脚踏阀（亚德客）'; -- 品牌：亚德客(AirTac)
UPDATE wms_item SET item_name = 'PC螺纹直通' WHERE id = 1997876728420188161 AND item_name = 'PC螺纹直通（亿日）'; -- 品牌：亿日
