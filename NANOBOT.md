# NANOBOT.md

## 项目概述

一个基于 Spring Boot 2.7 的简单用户管理系统，使用内存存储，提供用户 CRUD 操作。

## 技术栈

- **Java 17**
- **Spring Boot 2.7.18**
- **Spring Web** - REST API
- **Spring Validation** - 参数校验
- **Spring Boot Test** - 单元测试
- **Maven** - 构建工具

## 项目结构

```
src/main/java/com/example/usermanagement/
  ├── UserManagementApplication.java  # 启动类
  └── (controller/service/model 等包按需添加)

src/main/resources/
  ├── application.yml    # 应用配置
  └── static/
      ├── index.html     # 首页
      └── login.html     # 登录页
```

## 构建和运行命令

```bash
# 构建
mvn clean package

# 运行
mvn spring-boot:run

# 测试
mvn test

# 生成可执行 jar 并运行
java -jar target/user-management-1.0.0.jar
```

## 编码约定

- 包名基础：`com.example.usermanagement`
- 使用标准的 Spring Boot 分层架构（Controller → Service → Repository）
- 使用 JSR-303 注解进行参数校验
- 启动类使用 `@SpringBootApplication` 注解，保持精简
- 类注释使用中文 Javadoc

## 关键设计决策

- **内存存储**：使用 `ConcurrentHashMap` 存储用户数据，无持久化，适合演示和测试
- **静态页面**：通过 `static/` 目录提供简单的前端页面，不引入前端框架
- **无数据库依赖**：仅使用 `spring-boot-starter-web` 和 `validation`，保持轻量
- **Java 17**：使用较新的 LTS 版本，支持现代语法特性