package com.opensource.idempotent.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Created by double on 2019/7/11.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ServerResponse implements Serializable{

    private static final long serialVersionUID = 7498483649536881777L;

    private Integer status;

    private String msg;

    private Object data;

    @JsonIgnore
    public boolean isSuccess() {
        return this.status == ResponseCode.SUCCESS.getCode();
    }

    public static ServerResponse success() {
        return new ServerResponse(ResponseCode.SUCCESS.getCode(), null, null);
    }

    public static ServerResponse success(String msg) {
        return new ServerResponse(ResponseCode.SUCCESS.getCode(), msg, null);
    }

    public static ServerResponse success(Object data) {
        return new ServerResponse(ResponseCode.SUCCESS.getCode(), null, data);
    }

    public static ServerResponse success(String msg, Object data) {
        return new ServerResponse(ResponseCode.SUCCESS.getCode(), msg, data);
    }

    public static ServerResponse error(String msg) {
        return new ServerResponse(ResponseCode.ERROR.getCode(), msg, null);
    }

    public static ServerResponse error(Object data) {
        return new ServerResponse(ResponseCode.ERROR.getCode(), null, data);
    }

    public static ServerResponse error(String msg, Object data) {
        return new ServerResponse(ResponseCode.ERROR.getCode(), msg, data);
    }

}