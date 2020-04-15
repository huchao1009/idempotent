package com.opensource.idempotent.service;

import com.opensource.idempotent.common.ServerResponse;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by double on 2019/7/11.
 */
public interface TokenService {

    ServerResponse createToken();

    void checkToken(HttpServletRequest request) ;
}
