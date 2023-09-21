#Config

Configs comuns a todos podem ser feitas no values.yaml default do chart.

Configs específicas de cada ambiente podem ser feitas 

###Comandos de Helm para instalar/atualizar os filebeats todos:
```
helm upgrade --install -n gcs-staging-2 ingest-checkpoint . --set service.nodePort=30515 --set kafka.topic=ingest-filebeat-checkpoint
helm upgrade --install -n gcs-staging-2 ingest-apache . --set service.nodePort=30516 --set kafka.topic=ingest-filebeat-apache
helm upgrade --install -n gcs-staging-2 ingest-nginx . --set service.nodePort=30517 --set kafka.topic=ingest-filebeat-nginx
helm upgrade --install -n gcs-staging-2 ingest-bind . --set service.nodePort=30518 --set kafka.topic=ingest-filebeat-bind
helm upgrade --install -n gcs-staging-2 ingest-wauth . --set service.nodePort=30519 --set kafka.topic=ingest-filebeat-wauth
helm upgrade --install -n gcs-staging-2 ingest-dc . --set service.nodePort=30520 --set kafka.topic=ingest-filebeat-dc
```

Criar tópico:
```
kubectl -n gcs-staging-2 exec -it kafka-0 -- sh -c "/opt/bitnami/kafka/bin/kafka-topics.sh --create --bootstrap-server kafka-bitnami.gcs-staging-2.svc.cluster.local:9092 --replication-factor 2 --partitions 3 --if-not-exists --topic <topic>"
```

Criar tópicos todos necessários: (Pode ser feito como configuração de init do cluster de kafka também)
```
kubectl -n gcs-staging-2 exec -it kafka-0 -- sh -c "/opt/bitnami/kafka/bin/kafka-topics.sh --create --bootstrap-server kafka-bitnami.gcs-staging-2.svc.cluster.local:9092 --replication-factor 2 --partitions 3 --if-not-exists --topic ingest-filebeat-checkpoint
kubectl -n gcs-staging-2 exec -it kafka-0 -- sh -c "/opt/bitnami/kafka/bin/kafka-topics.sh --create --bootstrap-server kafka-bitnami.gcs-staging-2.svc.cluster.local:9092 --replication-factor 2 --partitions 3 --if-not-exists --topic ingest-filebeat-apache
kubectl -n gcs-staging-2 exec -it kafka-0 -- sh -c "/opt/bitnami/kafka/bin/kafka-topics.sh --create --bootstrap-server kafka-bitnami.gcs-staging-2.svc.cluster.local:9092 --replication-factor 2 --partitions 3 --if-not-exists --topic ingest-filebeat-nginx
kubectl -n gcs-staging-2 exec -it kafka-0 -- sh -c "/opt/bitnami/kafka/bin/kafka-topics.sh --create --bootstrap-server kafka-bitnami.gcs-staging-2.svc.cluster.local:9092 --replication-factor 2 --partitions 3 --if-not-exists --topic ingest-filebeat-bind
kubectl -n gcs-staging-2 exec -it kafka-0 -- sh -c "/opt/bitnami/kafka/bin/kafka-topics.sh --create --bootstrap-server kafka-bitnami.gcs-staging-2.svc.cluster.local:9092 --replication-factor 2 --partitions 3 --if-not-exists --topic ingest-filebeat-wauth
kubectl -n gcs-staging-2 exec -it kafka-0 -- sh -c "/opt/bitnami/kafka/bin/kafka-topics.sh --create --bootstrap-server kafka-bitnami.gcs-staging-2.svc.cluster.local:9092 --replication-factor 2 --partitions 3 --if-not-exists --topic ingest-filebeat-dc
```