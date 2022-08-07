package cn.hc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author HCong
 * @create 2022/8/6
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hello implements Serializable {
    private String message;
    private String description;
}
