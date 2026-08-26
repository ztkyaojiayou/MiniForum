/**
 * REST 控制器（业务侧，admin）
 * <p>
 * 对外暴露 HTTP 接口（帖子/用户/评论/关注/搜索/热搜/通知/私信/收藏/分类/看板/语录/转盘/健康）。
 * 统一返回 {@code Result<T>} 包装，需登录路径由 AuthInterceptor 拦截（写操作必须登录，
 * 游客可浏览读路径）。所有接口依赖 service 层，不直接碰 repository。
 *
 * 推荐接口 RecommendController 在 demo-runner（web 装配侧），与业务控制器分开——见该模块。
 */
package com.tkzou.miniforum.controller;
