# ============ 构建阶段 ============
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# 先拷贝 pom.xml 单独拉依赖，利用 Docker 层缓存
COPY pom.xml .
RUN mvn dependency:go-offline -B
# 拷贝源码并打包（跳过测试以加速镜像构建）
COPY src ./src
RUN mvn package -DskipTests -B

# ============ 运行阶段 ============
FROM eclipse-temurin:17-jre
WORKDIR /app
# 从构建阶段拷贝可执行 jar
COPY --from=build /app/target/mini-forum-1.0.0.jar app.jar
# 数据持久化目录（JSON 文件），可通过 -v 挂载宿主机目录
VOLUME ["/app/data"]
# 应用端口（与 application.yml 一致）
EXPOSE 8090
# 启动
ENTRYPOINT ["java", "-jar", "app.jar"]
