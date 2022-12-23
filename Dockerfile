FROM archlinux:latest
RUN pacman --noconfirm -Syu && pacman --noconfirm -S jre17-openjdk-headless

RUN mkdir /app
COPY build/libs/all-in-one-jar-0.1-SNAPSHOT.jar /app

EXPOSE 8080
VOLUME /workdir
WORKDIR /workdir
ENTRYPOINT ["java", "-jar", "/app/all-in-one-jar-0.1-SNAPSHOT.jar"]