package com.opensource.idempotent.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业务逻辑异常
 * Created by double on 2019/7/11.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ServiceException extends RuntimeException{

    private String code;
    private String msg;

    public ServiceException(String msg) {
        this.msg = msg;
    }
}