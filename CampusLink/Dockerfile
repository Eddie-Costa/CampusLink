####### STAGE 1: build #######
FROM maven:3.9-eclipse-temurin-25 AS build
 
WORKDIR /app
 
# Copia só o pom.xml
COPY pom.xml .
RUN mvn dependency:go-offline -B
 
# Agora copia o código-fonte
COPY src ./src
 
# Build sem rodar os testes
RUN mvn clean package -DskipTests
 
####### STAGE 2: runtime #######
FROM eclipse-temurin:25-jre-alpine
 
WORKDIR /app
 
# Jar (nome dinâmico via wildcard, não trava na versão)
COPY --from=build /app/target/*.jar app.jar
 
EXPOSE 8080
 
CMD ["java", "-jar", "app.jar"]