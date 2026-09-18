# Build: compila o jar com Maven (mesma versao de Java/Maven usada durante o
# desenvolvimento - ver PROGRESS.md sobre o ambiente sem Maven/JDK local).
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

# Runtime: apenas o JRE, sem Maven/JDK completo, para uma imagem final menor.
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /app/target/credit-engine-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
