package com.opensource.idempotent.service.impl;


import com.opensource.idempotent.common.ResponseCode;
import com.opensource.idempotent.common.ServerResponse;
import com.opensource.idempotent.common.ServiceException;
import com.opensource.idempotent.service.TokenService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * token业务处理，提供token创建、token验证接口
 * Created by double on 2019/7/11.
 */
@Service
public class TokenServiceImpl implements TokenService {

    private static final String TOKEN_NAME = "token";

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    public ServerResponse createToken() {
        //通过UUID来生成token
        String tokenValue = "idempotent:token:" + UUID.randomUUID().toString();
        //将token放入redis中，设置有效期为60S
        stringRedisTemplate.opsForValue().set(tokenValue, "0", 60, TimeUnit.SECONDS);
        return ServerResponse.success(tokenValue);
    }

    /**
     * @param request
     */
    @Override
    public void checkToken(HttpServletRequest request) {
        String token = request.getHeader(TOKEN_NAME);
        if (StringUtils.isBlank(token)) {
            token = request.getParameter(TOKEN_NAME);
            if (StringUtils.isBlank(token)) {
                //没有携带token，抛异常，这里的异常需要全局捕获
                throw new ServiceException(ResponseCode.ILLEGAL_ARGUMENT.getMsg());
            }
        }
        //token不存在，说明token已经被其他请求删除或者是非法的token
        if (!stringRedisTemplate.hasKey(token)) {
            throw new ServiceException(ResponseCode.REPETITIVE_OPERATION.getMsg());
        }
        boolean del = stringRedisTemplate.delete(token);
        if (!del) {
            //token删除失败，说明token已经被其他请求删除
            throw new ServiceException(ResponseCode.REPETITIVE_OPERATION.getMsg());
        }
    }

}