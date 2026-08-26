# ============ 构建阶段 ============
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# 先拷贝所有 pom.xml 单独拉依赖，利用 Docker 层缓存
COPY pom.xml .
COPY forum-core/pom.xml forum-core/
COPY forum-admin-server/pom.xml forum-admin-server/
COPY forum-recommend-server/pom.xml forum-recommend-server/
COPY forum-offline-job/pom.xml forum-offline-job/
COPY forum-flink-nearline/pom.xml forum-flink-nearline/
COPY demo-runner/pom.xml demo-runner/
RUN mvn dependency:go-offline -B
# 拷贝源码并打包（跳过测试以加速镜像构建；默认构建不含 flink 模块，演示轻量零中间件）
COPY forum-core forum-core
COPY forum-admin-server forum-admin-server
COPY forum-recommend-server forum-recommend-server
COPY forum-offline-job forum-offline-job
COPY forum-flink-nearline forum-flink-nearline
COPY demo-runner demo-runner
RUN mvn package -DskipTests -B

# ============ 运行阶段 ============
FROM eclipse-temurin:17-jre
WORKDIR /app
# 从构建阶段拷贝演示启动器可执行 jar（聚合 admin + recommend 单进程）
COPY --from=build /app/demo-runner/target/demo-runner-1.0.0.jar app.jar
# 数据持久化目录（JSON 文件），可通过 -v 挂载宿主机目录
VOLUME ["/app/data"]
# 应用端口（与 application.yml 一致）
EXPOSE 8090
# 启动
ENTRYPOINT ["java", "-jar", "app.jar"]
