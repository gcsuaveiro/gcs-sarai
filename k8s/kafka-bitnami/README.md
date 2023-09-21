# Bitnami Kafka

`values.yaml` tem os parâmetros usados na configuração do chart de kafka da bitnami

Documentação [aqui](https://artifacthub.io/packages/helm/bitnami/kafka) ou [aqui](https://github.com/bitnami/charts/blob/main/bitnami/kafka/README.md)

Mini artigo da confluent sobre Kraft (substituto do zookeeper) [aqui](https://developer.confluent.io/learn/kraft/)

Comando criar/atualizar cluster Kafka:
```
helm upgrade --install kafka-bitnami bitnami/kafka -f values.yaml -n gcs-staging-2
```