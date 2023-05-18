FROM openjdk:18-jdk-slim-buster

MAINTAINER skyline-auth-service

ENV SPRING_OUTPUT_ANSI_ENABLED=ALWAYS \
    SERVER_PORT=8080 \
    TZ=Asia/Shanghai

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

ADD target/*.jar /app.jar

ENTRYPOINT ["sh", "-c", "java $DYNAMIC_JAVA_OPTS -jar /app.jar"]

EXPOSE $SERVER_PORT
