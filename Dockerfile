FROM openjdk:8-alpine

COPY target/uberjar/pcp.jar /pcp/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/pcp/app.jar"]
