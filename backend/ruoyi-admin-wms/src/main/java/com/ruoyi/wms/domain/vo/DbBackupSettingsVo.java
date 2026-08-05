package com.ruoyi.wms.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 数据库自动备份设置。可在后台「数据库备份」页面直接修改，保存在 sys_config 中。
 *
 * @author savo
 */
@Data
public class DbBackupSettingsVo {

    /** 按间隔小时备份 */
    public static final String MODE_INTERVAL = "interval";
    /** 每天（或每周指定几天）定时备份 */
    public static final String MODE_DAILY = "daily";

    /** 是否开启自动备份 */
    private boolean enabled = true;

    /** 备份计划模式：interval / daily */
    private String mode = MODE_INTERVAL;

    /** interval 模式：距上次备份超过多少小时就备份 */
    private int intervalHours = 24;

    /** daily 模式：每天几点备份，HH:mm */
    private String dailyTime = "03:00";

    /** daily 模式：星期几备份，1=周一 … 7=周日 */
    private List<Integer> weekdays = List.of(1, 2, 3, 4, 5, 6, 7);

    /** 保留天数，0 表示不清理 */
    private int retentionDays = 14;

    /** 无论多旧都至少保留的份数 */
    private int minKeep = 3;

    /** 备份存放目录 */
    private String dir = "";

    /** 离机副本目录，留空表示不做副本 */
    private String mirrorDir = "";

    /** mysqldump 可执行文件路径，留空表示自动查找 */
    private String mysqldumpPath = "";
}
