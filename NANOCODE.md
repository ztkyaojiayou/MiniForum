# NANOCODE.md

## 项目概述

mini-forum 是一个基于 Spring Boot 的轻量级微博/论坛系统，使用内存 + JSON 文件持久化，通过 REST API 与静态页面提供服务。

## 技术栈

- Java 17
- Spring Boot 2.7.18（web、validation、test）
- Maven 3.9+ 构建
- Docker 多阶段构建（`maven:3.9-eclipse-temurin-17` → `eclipse-temurin:17-jre`）
- 数据存储：内存 + JSON 文件（`data/` 目录）
- Python 脚本用于数据灌入与验证

## 项目结构（Maven 多模块）

```
pom.xml                       # 父 POM（forum-parent，聚合 6 模块）
forum-core/                   # 共享域：实体/仓库/DTO/feed/行为日志/事件接口/PostAssembler
forum-admin-server/           # 主业务：帖子/用户/评论/关注/feed/搜索/热搜/通知/私信
forum-recommend-server/       # 推荐核心：召回/排序/重排/冷启动/画像/AB/配置 + 生产适配
forum-offline-job/            # 离线层：离线评估 + OfflineJobApplication
forum-flink-nearline/         # 近线层：Flink 实时特征作业（-Pprod 才构建）
demo-runner/                  # 演示启动器：聚合 admin+recommend 单进程
  src/main/resources/
    application.yml           # 端口 8090
    banner.txt
    static/                   # 静态页面（前端页面）
      index.html  hot.html  login.html  message.html
      my.html  notification.html  post.html  quote.html
      user.html  detail.html  wheel.html

scripts/
  start.bat / start.sh / stop.bat / restart.bat   # 启停脚本
  seed_users.py / seed_categories.py / seed_posts.py / seed_interactions.py  # 数据灌入
  restore_miniforum.py / verify_data.py           # 数据恢复与校验

docs/
  API.md                          # 接口文档
  功能迭代规划.md / 微博化改版规划.md / 第四期需求规划.md  # 迭代计划
  系统功能全景.md / 代码规范审查报告.md
```

## 构建和运行命令

```bash
# 本地运行
mvn spring-boot:run

# 打包（跳过测试）
mvn clean package -DskipTests

# 运行 jar
java -jar target/mini-forum-1.0.0.jar

# Docker 构建与运行
docker build -t mini-forum .
docker run -p 8090:8090 -v $(pwd)/data:/app/data mini-forum

# 项目自带脚本（Windows / Linux）
scripts/start.bat
scripts/start.sh
scripts/stop.bat

# 数据灌入与校验（按依赖顺序执行）
python scripts/seed_users.py
python scripts/seed_categories.py
python scripts/seed_posts.py
python scripts/seed_interactions.py
python scripts/verify_data.py
```

## 编码约定

- 包名：`com.tkzou.miniforum`，遵循 Spring Boot 标准分层
- 注释：Javadoc 使用中文描述类的作用
- 数据模型：JSON 持久化到 `data/` 目录，不提交到 Git（已在 .gitignore 中排除）
- 接口：REST API，静态页面通过 AJAX 调用（详见 `docs/API.md`）
- 脚本：Python 脚本统一放在 `scripts/`，命名以 `seed_`/`verify_` 前缀区分职责

## 关键设计决策

- **内存 + JSON 文件存储**：系统无数据库依赖，数据以 JSON 落盘到 `data/` 目录，Docker 部署时通过 VOLUME 挂载宿主机目录实现持久化
- **定时任务**：启动类启用 `@EnableScheduling`，后台任务用于数据维护（如热度计算等）
- **前后端一体**：静态页面置于 `src/main/resources/static/`，由 Spring Boot 直接托管，无需独立前端工程
- **Docker 分层缓存**：Dockerfile 先单独拷贝 `pom.xml` 执行 `dependency:go-offline` 拉取依赖，再拷源码打包，充分利用层缓存加速镜像构建
- **脚本化数据管理**：通过 Python 脚本完成种子数据灌入（用户、分类、帖子、互动）和校验，便于演示环境快速初始化
- **端口约定**：应用固定监听 `8090` 端口，与 Dockerfile EXPOSE、application.yml 保持一致