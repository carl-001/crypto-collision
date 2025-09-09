FROM openjdk:8

WORKDIR /game

# 更新系统并安装泰语字体
RUN yum update -y && yum install -y thai-fonts

ARG JAR_FILE=target/block-api-1.0.jar
COPY ${JAR_FILE} app.jar

EXPOSE 22222

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms512m -Xmx1024m -Djava.security.egd=file:/dev/./urandom -Dlog.path=/home/logs"

CMD sleep 60; java $JAVA_OPTS -jar app.jar
