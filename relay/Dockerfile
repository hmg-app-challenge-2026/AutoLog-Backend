# 먼저 루트에서 ./gradlew :relay:bootJar 로 jar 를 만든 뒤 빌드한다.
# (컨테이너 안에서 Gradle을 돌리면 :app 설정 때문에 Android SDK가 필요해지므로 jar만 복사)
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/relay.jar relay.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "relay.jar"]
