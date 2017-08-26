FROM jmoger/gitblit

COPY firebase.json /opt/gitblit
COPY data/gitblit.properties /opt/gitblit-data
COPY data/groovy/pre-commit-stash.groovy /opt/gitblit-data/groovy
COPY data/groovy/post-commit-stash.groovy /opt/gitblit-data/groovy
COPY ext/guava-23.0-rc1.jar /opt/gitblit/ext

EXPOSE 8443
EXPOSE 9418
EXPOSE 29418

# java -server -Xmx1024M -Djava.awt.headless=true -jar /opt/gitblit/gitblit.jar --baseFolder /opt/gitblit-data
CMD ["java", "-server", "-Xmx1024M", "-Djava.awt.headless=true", "-jar", "/opt/gitblit/gitblit.jar", "--baseFolder", "/opt/gitblit-data"]
