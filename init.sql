-- 用户基础信息表
CREATE TABLE `sys_user` (
                            `id`          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                            `student_no`  VARCHAR(32)      NOT NULL COMMENT '学号（登录账号）',
                            `password`    VARCHAR(128)     NOT NULL COMMENT '密码（BCrypt加密）',
                            `real_name`   VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '真实姓名',
                            `nickname`    VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '昵称',
                            `phone`       VARCHAR(16)      NOT NULL DEFAULT '' COMMENT '手机号',
                            `avatar`      VARCHAR(256)     NOT NULL DEFAULT '' COMMENT '头像URL',
                            `college`     VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '学院',
                            `major`       VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '专业',
                            `grade`       TINYINT          NOT NULL DEFAULT 0 COMMENT '年级：1大一 2大二 3大三 4大四',
                            `gender`      TINYINT          NOT NULL DEFAULT 0 COMMENT '性别：0未知 1男 2女',
                            `role`        TINYINT          NOT NULL DEFAULT 0 COMMENT '角色：0学生 1管理员',
                            `create_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            `deleted`     TINYINT          NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_student_no` (`student_no`) USING BTREE,
                            UNIQUE KEY `uk_phone` (`phone`) USING BTREE,
                            KEY `idx_college_major_grade` (`college`, `major`, `grade`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户基础信息表';

-- 用户借阅额度表
CREATE TABLE `user_borrow_quota` (
                                     `user_id`              BIGINT UNSIGNED  NOT NULL COMMENT '用户ID',
                                     `current_borrow_count` INT              NOT NULL DEFAULT 0 COMMENT '当前在借数量',
                                     `total_borrow_count`   INT              NOT NULL DEFAULT 0 COMMENT '历史累计借阅数量',
                                     `overdue_count`        INT              NOT NULL DEFAULT 0 COMMENT '累计逾期次数',
                                     `create_time`          DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `update_time`          DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                     PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户借阅额度表';

CREATE TABLE `course_book` (
                               `id`           BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                               `grade`        TINYINT             NOT NULL DEFAULT 0 COMMENT '年级：1大一 2大二 3大三 4大四',
                               `major`        VARCHAR(64)         NOT NULL DEFAULT '' COMMENT '专业名称，如：计算机科学与技术',
                               `course_name`  VARCHAR(64)         NOT NULL DEFAULT '' COMMENT '课程名称，如：面向对象程序设计',
                               `isbn`         VARCHAR(16)         NOT NULL COMMENT 'ISBN',
                               `is_required`  TINYINT             NOT NULL DEFAULT 1 COMMENT '是否必修：0选修 1必修',
                               `create_time`  DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `update_time`  DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               KEY `idx_grade_major` (`grade`, `major`) USING BTREE COMMENT '捐赠时下拉框用',
                               KEY `idx_isbn` (`isbn`) USING BTREE COMMENT '关联SKU用'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预置课程-教材目录';

ALTER TABLE course_book ADD COLUMN author VARCHAR(64) NOT NULL DEFAULT '' COMMENT '作者';
ALTER TABLE course_book ADD COLUMN publisher VARCHAR(64) NOT NULL DEFAULT '' COMMENT '出版社';


CREATE TABLE `book_sku` (
                            `id`              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                            `isbn`            VARCHAR(16)         NOT NULL COMMENT 'ISBN（唯一）',
                            `title`           VARCHAR(128)        NOT NULL DEFAULT '' COMMENT '书名',
                            `author`          VARCHAR(64)         NOT NULL DEFAULT '' COMMENT '作者',
                            `publisher`       VARCHAR(64)         NOT NULL DEFAULT '' COMMENT '出版社',
                            `cover_image`     VARCHAR(256)        NOT NULL DEFAULT '' COMMENT '封面图URL（预置或首本捐赠图）',
                            `total_stock`     INT                 NOT NULL DEFAULT 0 COMMENT '总入库数（累计捐赠数）',
                            `available_stock` INT                 NOT NULL DEFAULT 0 COMMENT '当前可申领数',
                            `version`         INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
                            `create_time`     DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_time`     DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_isbn` (`isbn`) USING BTREE COMMENT 'ISBN唯一聚合维度'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ISBN逻辑库存';


CREATE TABLE `book_instance` (
                                 `id`             BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                 `instance_code`  VARCHAR(32)         NOT NULL COMMENT '实体书唯一编码，如 BOOK-202608-00001',
                                 `isbn`           VARCHAR(16)         NOT NULL COMMENT '关联ISBN',
                                 `status`         VARCHAR(16)         NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING待入库/AVAILABLE可申领/RESERVED已预留/BORROWED已借出/DAMAGED已损坏',
                                 `location`       VARCHAR(32)         NOT NULL DEFAULT '主书库' COMMENT '存放位置（MVP默认主书库）',
                                 `reserved_by`    BIGINT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '预留人ID（status=RESERVED时有效）',
                                 `reserved_time`  DATETIME            NULL COMMENT '预留时间（用于兜底释放扫描）',
                                 `damaged_reason` VARCHAR(256)        NOT NULL DEFAULT '' COMMENT '损坏原因（status=DAMAGED时填写）',
                                 `create_time`    DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（即捐赠入库时间）',
                                 `update_time`    DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `uk_instance_code` (`instance_code`) USING BTREE COMMENT '实体书唯一编码',
                                 KEY `idx_isbn_status_time` (`isbn`, `status`, `create_time`) USING BTREE COMMENT '申领FIFO分配用：按ISBN+状态筛选，按入库时间排序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体书';


CREATE TABLE `donate_record` (
                                 `id`              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                 `request_id`      VARCHAR(64)         NOT NULL COMMENT '幂等请求ID（前端生成UUID）',
                                 `user_id`         BIGINT UNSIGNED     NOT NULL COMMENT '捐赠人ID',
                                 `instance_id`     BIGINT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '关联实体书ID（审核通过后回填）',
                                 `isbn`            VARCHAR(16)         NOT NULL COMMENT '捐赠的ISBN',
                                 `title`           VARCHAR(128)        NOT NULL DEFAULT '' COMMENT '书名（冗余快照，防止目录变更）',
                                 `cover_image_url` VARCHAR(256)        NOT NULL DEFAULT '' COMMENT '封面照片URL（阿里云OSS）',
                                 `inner_image_url` VARCHAR(256)        NOT NULL DEFAULT '' COMMENT '内页照片URL（阿里云OSS）',
                                 `status`          VARCHAR(16)         NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING待审核/ACCEPTED已通过/REJECTED已拒绝',
                                 `remark`          VARCHAR(256)        NOT NULL DEFAULT '' COMMENT '备注（拒绝原因等）',
                                 `create_time`     DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `update_time`     DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `uk_request_id` (`request_id`) USING BTREE COMMENT '【幂等】同一request_id重复提交直接返回上次结果',
                                 KEY `idx_user_id` (`user_id`) USING BTREE COMMENT '我的捐赠记录查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='捐赠记录';

CREATE TABLE `borrow_record` (
                                 `id`              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                 `record_no`       VARCHAR(32)         NOT NULL COMMENT '凭证号，如 BR202608190001，给用户查看',
                                 `request_id`      VARCHAR(64)         NULL COMMENT '【幂等】前端生成的UUID，防止重复提交',
                                 `user_id`         BIGINT UNSIGNED     NOT NULL COMMENT '申领人ID',
                                 `instance_id`     BIGINT UNSIGNED     NOT NULL COMMENT '分配的实体书ID',
                                 `isbn`            VARCHAR(16)         NOT NULL COMMENT 'ISBN冗余',
                                 `book_title`      VARCHAR(128)        NOT NULL DEFAULT '' COMMENT '书名冗余（我的借阅列表直接展示，减少跨服务调用）',
                                 `status`          VARCHAR(16)         NOT NULL DEFAULT 'PENDING_PICKUP' COMMENT '状态：PENDING_PICKUP待领取/BORROWED已借出/RETURNED已归还/OVERDUE已逾期/CANCELLED已取消',
                                 `borrow_time`     DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申领时间',
                                 `pickup_deadline` DATETIME            NOT NULL COMMENT '领取截止时间（申领后7天）',
                                 `pickup_time`     DATETIME            NULL COMMENT '实际领取时间（管理员确认时）',
                                 `due_date`        DATETIME            NULL COMMENT '到期时间（领取后一学期，如4个月）',
                                 `return_time`     DATETIME            NULL COMMENT '实际归还时间',
                                 `remind_time`     DATETIME            NULL COMMENT '到期提醒发送时间（NULL=未提醒，防止重复提醒）',
                                 `overdue_days`    INT                 NOT NULL DEFAULT 0 COMMENT '逾期天数（逾期时计算）',
                                 `cancel_reason`   VARCHAR(32)         NOT NULL DEFAULT '' COMMENT '取消原因：SYSTEM_TIMEOUT超时/USER_CANCEL主动取消',
                                 `create_time`     DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `update_time`     DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `uk_record_no` (`record_no`) USING BTREE COMMENT '凭证号唯一',
                                 UNIQUE KEY `uk_request_id` (`request_id`) USING BTREE COMMENT '【幂等】同一request_id重复提交直接返回上次结果',
                                 KEY `idx_user_status` (`user_id`, `status`) USING BTREE COMMENT '我的借阅查询',
                                 KEY `idx_instance_status` (`instance_id`, `status`) USING BTREE COMMENT '归还时通过实体书查记录',
                                 KEY `idx_status_pickup_deadline` (`status`, `pickup_deadline`) USING BTREE COMMENT '【定时任务】超时释放扫描：只扫PENDING_PICKUP且过期的',
                                 KEY `idx_status_due_date` (`status`, `due_date`) USING BTREE COMMENT '【定时任务】逾期扫描+到期提醒'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='借阅记录';

CREATE TABLE `dead_letter_record` (
                                      `id`             BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                      `type`           VARCHAR(32)         NOT NULL COMMENT '死信类型：INVENTORY_MISMATCH库存不一致/PICKUP_TIMEOUT释放失败/RETURN_FAIL归还失败/OVERDUE逾期未处理',
                                      `biz_type`       VARCHAR(16)         NOT NULL DEFAULT '' COMMENT '业务类型：BORROW申领/RETURN归还/DONATE捐赠',
                                      `biz_id`         VARCHAR(64)         NOT NULL DEFAULT '' COMMENT '业务ID（如 borrow_record.record_no 或 book_instance.instance_code）',
                                      `user_id`        BIGINT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '关联用户ID',
                                      `error_msg`      VARCHAR(512)        NOT NULL DEFAULT '' COMMENT '异常信息摘要',
                                      `context`        JSON                NULL COMMENT '上下文快照（JSON格式，存isbn/instanceId/oldStatus等，补偿时直接读）',
                                      `status`         TINYINT             NOT NULL DEFAULT 0 COMMENT '0待处理 1已解决 2已忽略',
                                      `resolve_remark` VARCHAR(256)        NOT NULL DEFAULT '' COMMENT '处理备注（管理员填写）',
                                      `resolve_time`   DATETIME            NULL COMMENT '处理时间',
                                      `create_time`    DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      `update_time`    DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                      PRIMARY KEY (`id`),
                                      KEY `idx_type_status` (`type`, `status`) USING BTREE COMMENT '管理端按类型筛选待处理',
                                      KEY `idx_biz` (`biz_type`, `biz_id`) USING BTREE COMMENT '根据业务ID反查死信',
                                      KEY `idx_create_time` (`create_time`) USING BTREE COMMENT '按时间倒序查看'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='死信记录';


-- ============================================
-- 1. 新增幂等操作日志表（防重复调用）
-- ============================================
CREATE TABLE `idempotent_record` (
                                     `id`          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                     `request_id`  VARCHAR(64)      NOT NULL COMMENT '幂等键（前端生成的UUID）',
                                     `biz_type`    VARCHAR(32)      NOT NULL COMMENT '业务类型：BORROW_INCREASE额度增加/BORROW_DECREASE额度减少/OVERDUE_INCREASE逾期增加',
                                     `create_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     PRIMARY KEY (`id`),
                                     UNIQUE KEY `uk_request_biz` (`request_id`, `biz_type`) USING BTREE COMMENT '同一业务同一请求只处理一次'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='幂等操作日志（防Feign重试导致重复扣减）';

-- ============================================
-- 2. 给 book_sku 增加 edition（版次）字段
-- ============================================
ALTER TABLE `book_sku`
    ADD COLUMN `edition` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '版次，如：第3版、第四版' AFTER `publisher`;

-- ============================================
-- 3. 给 course_book 增加 edition（版次）字段
-- ============================================
ALTER TABLE `course_book`
    ADD COLUMN `edition` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '版次' AFTER `publisher`;

ALTER TABLE borrow_record
    ADD COLUMN instance_code VARCHAR(32) NOT NULL DEFAULT '' COMMENT '实体书编码冗余',
    ADD COLUMN location VARCHAR(32) NOT NULL DEFAULT '' COMMENT '存放位置冗余';



-- ============================================
-- 【优化】覆盖索引：热门统计场景下按 create_time 范围查询并按 isbn 分组
-- 优化前：EXPLAIN 出现 Using where; Using temporary; Using filesort
-- 优化后：Index Range Scan + Covering Index，避免回表和文件排序
-- ============================================
ALTER TABLE borrow_record ADD INDEX idx_create_time_isbn (create_time, isbn);


-- ============================================
-- 【优化】热门教材日聚合表（空间换时间）
-- 设计背景：
--   1. borrow_record 是明细流水表，数据量会持续增长（百万级+）
--   2. 每天全量统计近7天或某个月份，需要扫描大量明细行，性能衰减明显
--   3. 日聚合表每天只保留"每个ISBN当天的申领次数"，数据量极小
--
-- 数据量估算：
--   假设每天被申领的不同ISBN有 50 种
--   保留2年 = 730天 × 50条 ≈ 3.6万条（MySQL 小表，毫秒级查询）
-- ============================================
CREATE TABLE `hot_book_daily` (
                                  `stat_date`   DATE        NOT NULL COMMENT '统计日期',
                                  `isbn`        VARCHAR(16) NOT NULL COMMENT 'ISBN',
                                  `apply_count` INT         NOT NULL DEFAULT 0 COMMENT '当日申领次数',
                                  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
                                  PRIMARY KEY (`stat_date`, `isbn`) USING BTREE COMMENT '按日期+ISBN聚合，天然支持日期范围查询',
                                  KEY `idx_stat_date` (`stat_date`) USING BTREE COMMENT '单独按日期查询、清理过期数据用'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='热门教材日聚合表（支持近7天实时榜 + 历史月份回溯）';


-- ============================================
-- 【已有索引回顾/确认】borrow_record 覆盖索引
-- 作用：支持定时任务按天聚合时走索引范围扫描，避免全表扫描
-- 使用场景：WHERE DATE(create_time) = '2026-08-25' GROUP BY isbn
-- ============================================