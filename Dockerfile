FROM eclipse-temurin:17-jdk-jammy

WORKDIR /usrapp/bin

ENV PORT=6000

COPY /target/classes /usrapp/bin/classes
COPY /target/dependency /usrapp/bin/dependency

CMD ["java", "-cp", "./classes:./dependency/*", "co.edu.escuelaing.arep.app.App"]
