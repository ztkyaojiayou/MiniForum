package com.tkzou.miniforum.config;

import com.tkzou.miniforum.exception.UnauthorizedException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 登录认证拦截器
 * <p>
 * 拦截受保护路径。未登录用户允许"浏览类 GET"（帖子/评论/标签/搜索/分类/推荐/用户主页/热门），
 * 写操作（发帖/点赞/评论/收藏/转发/关注/打点等）必须登录——参考微博首页设计：
 * 游客可看热门与广场内容，互动需登录。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String SESSION_USER = "userId";

    /** 游客可浏览的 GET 路径前缀 */
    private static final String[] PUBLIC_READ_PREFIXES = {
            "/api/posts", "/api/comments", "/api/tags", "/api/search",
            "/api/categories", "/api/recommend", "/api/users", "/api/hot"
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 放行预检请求（CORS）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 游客可浏览（GET 读操作），写操作仍需登录
        if ("GET".equalsIgnoreCase(request.getMethod()) && isPublicReadPath(request.getRequestURI())) {
            return true;
        }
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SESSION_USER) == null) {
            throw new UnauthorizedException("未登录或登录已过期");
        }
        return true;
    }

    /** 是否为游客可浏览的读路径（GET） */
    private boolean isPublicReadPath(String uri) {
        for (String prefix : PUBLIC_READ_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
