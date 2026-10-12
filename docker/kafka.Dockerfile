FROM apache/kafka-native:3.9.0
USER root
RUN mkdir -p /var/lib/kafka/data && chown 1000:1000 /var/lib/kafka/data
USER appuser
