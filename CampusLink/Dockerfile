# imagem do campuslink compila o projeto e prepara a execucao da aplicacao na porta exposta
# compilar aplicacao prepara a etapa de montagem com maven e java
FROM maven:3.9-eclipse-temurin-25 AS build
 
WORKDIR /app
 
# copiar dependencias envia a definicao do projeto antes de baixar os pacotes
COPY pom.xml .
RUN mvn dependency:go-offline -B
 
# copiar codigo inclui os arquivos fonte na etapa de compilacao
COPY src ./src
 
# gerar pacote compila a aplicacao sem executar os testes nesta etapa
RUN mvn clean package -DskipTests
 
# executar aplicacao prepara uma imagem apenas com o ambiente java necessario
FROM eclipse-temurin:25-jre-alpine
 
WORKDIR /app
 
# copiar pacote final leva o arquivo compilado para a imagem de execucao
COPY --from=build /app/target/*.jar app.jar
 
EXPOSE 8080
 
CMD ["java", "-jar", "app.jar"]