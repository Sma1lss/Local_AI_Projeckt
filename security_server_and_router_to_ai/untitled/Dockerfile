FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts /app/
COPY gradle /app/gradle
COPY src /app/src
RUN ./gradlew bootJar -x test

FROM eclipse-temurin:17-jre
RUN useradd -u 10001 -m fsss
WORKDIR /app
COPY --from=build /app/build/libs/*.jar /app/app.jar
RUN mkdir -p /fsss-spool && chown -R fsss:fsss /fsss-spool /app
USER fsss
EXPOSE 8080 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
