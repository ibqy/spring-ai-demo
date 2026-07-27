# S08-mcp-server-basic-auth：MCP Server 鉴权

## 一、问题定位

| 维度 | 说明 |
|------|------|
| **技术问题** | MCP Server 暴露后任何人都能调用，如何添加简单的鉴权保护 |
| **适用场景** | 内部工具服务保护、防止未授权访问、API 配额控制 |
| **前置知识** | S07 MCP Server、Servlet Filter、HTTP 认证基础 |

## 二、标准处理流程

```
┌─────────────────────────────────────────────────────────┐
│  1. 创建 Servlet Filter 拦截 MCP 端点                    │
│  2. 从请求头提取 Authorization 字段                       │
│  3. 根据认证方式（Bearer/Basic）校验凭证                  │
│  4. 校验通过 → 放行；失败 → 返回 401                     │
│  5. 客户端连接时携带认证头                                │
└─────────────────────────────────────────────────────────┘
```

## 三、核心知识点（⭐ 重点）

### ⭐ 1. Servlet Filter 拦截 MCP 端点

```java
@WebFilter(asyncSupported = true)  // ⚠️ SSE 必须开启异步支持
public class ReqFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        String url = request.getRequestURI();
        String auth = request.getHeader("Authorization");

        // 只拦截 MCP 相关端点
        if (url.equals("/sse") || url.equals("/mcp/messages")) {
            if (auth == null) {
                throw new RuntimeException("missing authorization");
            }
            if (auth.startsWith("Bearer ")) {
                // Bearer Token 校验
            } else if (auth.startsWith("Basic ")) {
                // Basic Auth 校验
            }
        }
        chain.doFilter(req, res);
    }
}
```

### ⭐ 2. Bearer Token 鉴权

```java
String token = auth.substring(7);  // "Bearer " 长度为 7
if (!EXPECTED_TOKEN.equals(token)) {
    throw new RuntimeException("token error");
}
```

请求头格式：`Authorization: Bearer yihuihui-blog`

### ⭐ 3. Basic Auth 鉴权

```java
String encoded = auth.substring(6);  // "Basic " 长度为 6
String decoded = new String(Base64.getDecoder().decode(encoded));
String[] credentials = decoded.split(":", 2);  // [username, password]
// 校验 credentials[0] 和 credentials[1]
```

请求头格式：`Authorization: Basic eWlodWk6MTIzNDU2Nzg=`（Base64 编码的 `username:password`）

### 4. 启用 Filter 注册

```java
@SpringBootApplication
@ServletComponentScan  // 关键：自动扫描 @WebFilter 注解
public class S08Application {
    public static void main(String[] args) {
        SpringApplication.run(S08Application.class, args);
    }
}
```

### 5. 关键注意事项

| 要点 | 说明 |
|------|------|
| `asyncSupported = true` | **必须开启**，否则 SSE 长连接被 Filter 阻断 |
| 精确拦截路径 | 只拦截 `/sse` 和 `/mcp/messages`，不影响其他接口 |
| 生产环境 | 本方案仅适合开发/测试，生产应使用 Spring Security |

## 四、完整示例代码

```java
@WebFilter(asyncSupported = true)
public class ReqFilter implements Filter {

    private static final String TOKEN = "yihuihui-blog";
    private static final String USERNAME = "yihui";
    private static final String PASSWORD = "12345678";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        String url = request.getRequestURI();
        String auth = request.getHeader("Authorization");

        if ("/sse".equals(url) || "/mcp/messages".equals(url)) {
            if (auth == null || auth.isBlank()) {
                ((HttpServletResponse) res).sendError(401, "Unauthorized");
                return;
            }
            if (auth.startsWith("Bearer ")) {
                if (!TOKEN.equals(auth.substring(7))) {
                    ((HttpServletResponse) res).sendError(403, "Invalid token");
                    return;
                }
            } else if (auth.startsWith("Basic ")) {
                String decoded = new String(Base64.getDecoder().decode(auth.substring(6)));
                String[] parts = decoded.split(":", 2);
                if (!USERNAME.equals(parts[0]) || !PASSWORD.equals(parts[1])) {
                    ((HttpServletResponse) res).sendError(403, "Invalid credentials");
                    return;
                }
            }
        }
        chain.doFilter(req, res);
    }
}
```

## 五、同类需求的其他实现方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| **Spring Security（推荐）** | 完整的认证授权框架，支持 OAuth2/JWT | 生产环境首选 |
| **API Gateway 鉴权** | 在网关层统一校验（如 Spring Cloud Gateway） | 微服务架构 |
| **mTLS 双向证书** | 传输层安全，客户端也需证书 | 高安全要求场景 |
| **IP 白名单** | 只允许特定 IP 访问 | 内网固定调用方 |

## 六、常见坑点

| 问题 | 原因 | 解决 |
|------|------|------|
| SSE 连接立即断开 | Filter 未设置 `asyncSupported = true` | 添加异步支持 |
| Filter 未生效 | 缺少 `@ServletComponentScan` | 启动类添加注解 |
| 所有接口都被拦截 | 未做路径判断 | 精确匹配 MCP 端点 |
| 客户端认证失败 | MCP Client 未配置 Authorization 头 | 客户端配置中添加 headers |

## 七、本节小结

```
核心收获：
✅ 掌握 Servlet Filter 实现轻量级鉴权
✅ 理解 Bearer Token 和 Basic Auth 两种认证方式
✅ 牢记 asyncSupported = true 对 SSE 的必要性
✅ 了解生产环境应升级到 Spring Security

下一步学习：S09-chat-client → 全面掌握 ChatClient 高级 API
```
