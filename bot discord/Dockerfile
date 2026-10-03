# Étape 1 : Compilation du projet avec Maven
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Étape 2 : Exécution de la version fraîchement compilée
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*-with-dependencies.jar app.jar
CMD ["java", "-jar", "app.jar"]
