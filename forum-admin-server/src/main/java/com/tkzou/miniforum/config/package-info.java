/**
 * Web 配置与认证拦截器
 * <p>
 * {@link com.tkzou.miniforum.config.AuthInterceptor}：Session 登录认证拦截——
 * 写操作必须登录（401），游客可浏览读路径（帖子/评论/标签/搜索/分类/推荐/用户/热门）。
 * {@code WebConfig} 注册拦截器到 /api/**。
 *
 * 注意：/api/recommend 的 GET 放行游客（详情页"相关推荐"游客可看），但其中的
 * /api/recommend/feed 是强个性化接口，在 RecommendController 内额外守卫 null userId → 401。
 */
package com.tkzou.miniforum.config;
