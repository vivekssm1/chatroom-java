FROM openjdk:17

WORKDIR /app

COPY . .

RUN javac ChatRoom.java

CMD ["java", "ChatRoom"]
