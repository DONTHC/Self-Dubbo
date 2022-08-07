package cn.hc.enums;

import lombok.*;

/**
 * @author HCong
 * @create 2022/8/5
 */
@Getter
@AllArgsConstructor
@ToString
public enum RpcResponseCodeEnum {

    SUCCESS(200, "The remote call is successful"),
    FAIL(500, "The remote call is fail");

    private final int code;
    private final String message;
}
