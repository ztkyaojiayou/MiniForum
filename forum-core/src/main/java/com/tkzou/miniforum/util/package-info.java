/**
 * 工具类
 * <p>
 * {@link com.tkzou.miniforum.util.PasswordEncoder}：密码加盐哈希（SHA-256 + 随机盐），
 * 校验/注册/改密统一入口。生产化可换 BCrypt（见 docs/生产化落地开发清单 P3.1）。
 */
package com.tkzou.miniforum.util;
