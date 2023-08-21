#!/bin/sh

mvn clean package
docker build -t eiffel-vici:0.0.1 --build-arg URL=./target/eiffel-vici-0.0.1.war -f src/main/docker/Dockerfile .
docker tag eiffel-vici:0.0.1 localhost:5000/cdevents/cdevents-visi
docker push localhost:5000/cdevents/cdevents-visi:latest

kubectl delete -f cdevents-visi-deployment.yaml
kubectl apply -f cdevents-visi-deployment.yaml
