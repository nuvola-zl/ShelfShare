package com.shelf.common.code;

/**
 * 错误码常量 - A-BB-CCC 模型
 * A = 业务成功/通用业务错误, B = 系统错误, C = 第三方错误
 */
public final class ErrorCode {

    private ErrorCode() {}

    // ========== 成功 ==========
    public static final String SUCCESS = "A00000";

    // ========== 通用域 (00) ==========
    /** 参数校验失败 */
    public static final String PARAM_ERROR = "A00001";
    /** 资源不存在 */
    public static final String RESOURCE_NOT_FOUND = "A00002";
    /** 资源已存在/重复 */
    public static final String RESOURCE_DUPLICATE = "A00003";
    /** 资源状态不允许操作 */
    public static final String RESOURCE_STATUS_INVALID = "A00004";
    /** 并发冲突/系统繁忙 */
    public static final String SYSTEM_BUSY = "A00005";
    /** 请求已处理 */
    public static final String REQUEST_PROCESSED = "A00007";
    /** 远程调用异常 */
    public static final String REMOTE_CALL_ERROR = "A00008";
    /** 未登录/未授权 */
    public static final String UNAUTHORIZED = "A00010";
    /** 禁止访问/操作受限（账号冻结、权限不足等） */
    public static final String FORBIDDEN = "A00011";

    // ========== 用户中心 (01) ==========
    /** 用户名/学号已存在 */
    public static final String USERNAME_EXISTS = "A01001";
    /** 密码错误 */
    public static final String PASSWORD_ERROR = "A01002";
    /** 账户不存在 */
    public static final String ACCOUNT_NOT_FOUND = "A01004";
    /** 手机号已存在 */
    public static final String PHONE_EXISTS = "A01005";

    // ========== 教材/库存中心 (02) ==========
    /** 库存不足 */
    public static final String STOCK_INSUFFICIENT = "A02003";

    // ========== 借阅中心 (03) ==========
    /** 借阅数量已达上限 */
    public static final String BORROW_LIMIT_EXCEEDED = "A03001";
    /** 已借阅该教材（归还前不可重复申领） */
    public static final String BORROW_DUPLICATE = "A03002";

    // ========== 系统错误 (B) ==========
    /** 系统内部错误 */
    public static final String SYSTEM_ERROR = "B00001";
    /** 数据库操作失败 */
    public static final String DB_ERROR = "B00002";
    /** 分布式锁异常 */
    public static final String LOCK_ERROR = "B00003";

    // ========== 第三方错误 (C) ==========
    /** OSS上传失败 */
    public static final String OSS_UPLOAD_FAILED = "C06001";
}