# ============================================================================
# 后端一体化镜像：多阶段构建（Maven 打包 → JRE 运行），实现"任意 Docker 主机可运行"
# 构建期使用阿里云 Maven 镜像（deploy/maven-settings.xml）加速国内下载
# ============================================================================
FROM maven:3.8-eclipse-temurin-8 AS build
WORKDIR /app
# 镜像加速配置（先于 pom 拷贝，利用构建缓存）
COPY deploy/maven-settings.xml /root/.settings.xml
COPY pom.xml .
RUN mvn -q -s /root/.settings.xml dependency:go-offline || true
COPY src ./src
RUN mvn -q -s /root/.settings.xml clean package -Dmaven.test.skip=true

FROM eclipse-temurin:8-jre
WORKDIR /app
# 时区与编码
ENV TZ=Asia/Shanghai
RUN ln -sf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
COPY --from=build /app/target/hm-dianping-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
# JVM 内存限制（容器友好）
ENTRYPOINT ["java", "-Xms512m", "-Xmx512m", "-jar", "app.jar"]
