FROM eclipse-temurin:17

WORKDIR /app

COPY . .

RUN javac src/*.java

EXPOSE 9090

CMD ["java", "-cp", "src", "BookServer"]