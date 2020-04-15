### 接口幂等性校验



#### 一、概念

幂等（idempotent、idempotence）是一个数学与计算机学概念，常见于抽象代数中。

在编程中.一个幂等操作的特点是其任意多次执行所产生的影响均与一次执行的影响相同。幂等函数，或幂等方法，是指可以使用相同参数重复执行，并能获得相同结果的函数。

这些函数不会影响系统状态，也不用担心重复执行会对系统造成改变。例如，“getUsername()和setTrue()”函数就是一个幂等函数.

幂等性, 通俗的说就是一个接口, 多次发起同一个请求, 必须保证操作只能执行一次，比如:

- 订单接口, 不能多次创建订单
- 支付接口, 重复支付同一笔订单只能扣一次钱
- 支付宝回调接口, 可能会多次回调, 必须处理重复回调
- 普通表单提交接口, 因为网络超时等原因多次点击提交, 只能成功一次
  等等

&nbsp;

#### 二、常见解决方案

- 唯一索引 -- 防止新增脏数据

- token机制 -- 防止页面重复提交

- 悲观锁 -- 获取数据的时候加锁(锁表或锁行)

- 乐观锁 -- 基于版本号version实现, 在更新数据那一刻校验数据

- 分布式锁 -- redis(jedis、redisson)或zookeeper实现

- 状态机 -- 状态变更, 更新数据时判断状态

&nbsp;

#### 三、本文实现方案

本文采用第2种方式实现, 即通过redis + token机制实现接口幂等性校验

为需要保证幂等性的每一次请求创建一个唯一标识token, 先获取token, 并将此token存入redis, 请求接口时, 将此token放到header或者作为请求参数请求接口, 后端接口判断redis中是否存在此token，如果存在, 正常处理业务逻辑, 并从redis中删除此token, 那么, 如果是重复请求, 由于token已被删除, 则不能通过校验, 返回请勿重复操作提示，如果不存在, 说明参数不合法或者是重复请求, 返回提示即可

&nbsp;

#### 四、核心代码

依赖库

```groovy
dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-data-redis'
	implementation 'org.springframework.boot:spring-boot-starter-web'
	implementation group: 'org.apache.commons', name: 'commons-lang3', version: '3.9'
	implementation 'org.projectlombok:lombok'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

自定义注解

```java
/**
 * 在需要保证 接口幂等性 的Controller的方法上使用此注解
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiIdempotent {
}
```

TokenServiceImpl

```java
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
```

IdempotentTokenInterceptor

```java
/**
 * 接口幂等性校验拦截器
 * Created by double on 2019/7/11.
 */
public class IdempotentTokenInterceptor implements HandlerInterceptor {

    @Autowired
    private TokenService tokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        //幂等性校验, 校验通过则放行, 校验失败则抛出异常, 并通过统一异常处理返回友好提示
        ApiIdempotent apiIdempotent = handlerMethod.getMethod().getAnnotation(ApiIdempotent.class);
        if (apiIdempotent != null) {
            tokenService.checkToken(request);
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, ModelAndView modelAndView) throws Exception {
    }

    @Override
    public void afterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, Exception e) throws Exception {
    }
}
```



完成代码参考我的github：https://github.com/huchao1009/idempotent.git

&nbsp;

#### 五、测试

测试接口Controller

```java
/**
 * 幂等性测试接口
 * Created by double on 2019/7/11.
 */
@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    private TestService testService;

    @ApiIdempotent
    @PostMapping("testIdempotent")
    public ServerResponse testIdempotent() {
        return testService.testIdempotence();
    }

}
```

&nbsp;

##### 1、获取token

```shell
http://localhost:8081/token
```



```json
{
	"status": 0,
	"msg": "idempotent:token:80dd47ed-fa63-4b30-82fa-f3ee4fd64a50",
	"data": null
}
```

 &nbsp;

##### 2、验证接口安全性

```shell
http://localhost:8081/test/testIdempotent?token=idempotent:token:b9ae797d-ed1a-4dbc-a94f-b7e45897f0f5
```

 

第一次请求

```json
{
	"status": 0,
	"msg": "test idempotent success",
	"data": null
}
```

&nbsp;

重复请求

```json
{
	"status": 1,
	"msg": "请勿重复操作",
	"data": null
}
```

&nbsp; 

##### 3、利用jmeter测试工具模拟50个并发请求, 获取一个新的token作为参数

设置50个线程请求一次

![image-20200415170757239](https://tva1.sinaimg.cn/large/007S8ZIlgy1gdukcko83sj322m0ok489.jpg)

 设置请求IP、Path、参数等信息![image-20200415170807474](https://tva1.sinaimg.cn/large/007S8ZIlgy1gdukcr6j21j321i0lqdpv.jpg)

查看执行结果，可以看到只有一个请求成功，其他的请求都返回错误 

![image-20200415170713970](https://tva1.sinaimg.cn/large/007S8ZIlgy1gdukbu7sdyj31h30u0ast.jpg)

![image-20200415170741939](https://tva1.sinaimg.cn/large/007S8ZIlgy1gdukcbem5pj31fy0u04gs.jpg)