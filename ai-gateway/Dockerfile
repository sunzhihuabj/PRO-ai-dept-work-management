# =====================
# AI 网关多阶段构建：Maven 构建 -> 运行时（T-105）
# =====================
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/ai-gateway.jar app.jar
EXPOSE 8090
ENV TZ=Asia/Shanghai
ENTRYPOINT ["java", "-jar", "app.jar"]
