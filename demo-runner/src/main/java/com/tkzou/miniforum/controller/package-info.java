/**
 * 演示装配侧控制器
 * <p>
 * 本包在 demo-runner 只放 {@code RecommendController}（推荐接口的 web 装配）——
 * 因为它依赖 recommend-server 的 RecommendService，放在聚合模块避免 recommend 模块带 web。
 * 业务控制器在 forum-admin-server 的同名包下；demo 依赖 admin + recommend 后全部扫描装配。
 */
package com.tkzou.miniforum.controller;
