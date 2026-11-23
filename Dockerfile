# Receita para colocar o Routify no ar (Render, Railway, Koyeb ou qualquer host com Docker).
# Etapa 1: compila o projeto com Maven e Java 21.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# Etapa 2: imagem leve só com o Java para rodar o .jar.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Planos gratuitos têm pouca memória (~512 MB): o Java usa no máximo 75% dela.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
# O host informa a porta pela variável PORT; localmente fica 8080.
EXPOSE 8080
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
