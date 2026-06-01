#!/bin/bash

mkdir -p infrastructure/k8s

cat << 'YAML' > infrastructure/k8s/postgres.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
  labels:
    app: postgres
spec:
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
      - name: postgres
        image: postgres:15
        ports:
        - containerPort: 5432
        env:
        - name: POSTGRES_DB
          value: "swiss_db"
        - name: POSTGRES_USER
          value: "postgres"
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: app-secrets
              key: postgres-password
              optional: true
        livenessProbe:
          exec:
            command: ["pg_isready", "-U", "postgres", "-d", "swiss_db"]
          initialDelaySeconds: 15
          periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: postgres
spec:
  ports:
  - port: 5432
  selector:
    app: postgres
YAML

cat << 'YAML' > infrastructure/k8s/redis.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
  labels:
    app: redis
spec:
  replicas: 1
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        ports:
        - containerPort: 6379
        livenessProbe:
          exec:
            command: ["redis-cli", "ping"]
          initialDelaySeconds: 5
          periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: redis
spec:
  ports:
  - port: 6379
  selector:
    app: redis
YAML

cat << 'YAML' > infrastructure/k8s/mongodb.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mongodb
  labels:
    app: mongodb
spec:
  replicas: 1
  selector:
    matchLabels:
      app: mongodb
  template:
    metadata:
      labels:
        app: mongodb
    spec:
      containers:
      - name: mongodb
        image: mongo:6.0
        ports:
        - containerPort: 27017
        resources:
          limits:
            memory: "256Mi"
            cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: mongodb
spec:
  ports:
  - port: 27017
  selector:
    app: mongodb
YAML

cat << 'YAML' > infrastructure/k8s/kafka.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kafka
  labels:
    app: kafka
spec:
  replicas: 1
  selector:
    matchLabels:
      app: kafka
  template:
    metadata:
      labels:
        app: kafka
    spec:
      containers:
      - name: kafka
        image: docker.redpanda.com/redpandadata/redpanda:v23.2.3
        ports:
        - containerPort: 9092
        - containerPort: 29092
        - containerPort: 8081
        - containerPort: 8082
        - containerPort: 8083
        - containerPort: 8084
        - containerPort: 33145
        command:
        - redpanda
        - start
        - --smp
        - "1"
        - --overprovisioned
        - --kafka-addr
        - internal://0.0.0.0:9092,external://0.0.0.0:29092
        - --advertise-kafka-addr
        - internal://kafka:9092,external://localhost:29092
        - --pandaproxy-addr
        - internal://0.0.0.0:8082,external://0.0.0.0:8083
        - --advertise-pandaproxy-addr
        - internal://kafka:8082,external://localhost:8083
        - --schema-registry-addr
        - internal://0.0.0.0:8081,external://0.0.0.0:8084
        - --rpc-addr
        - 0.0.0.0:33145
        - --advertise-rpc-addr
        - kafka:33145
        resources:
          limits:
            memory: "512Mi"
            cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: kafka
spec:
  ports:
  - name: kafka-internal
    port: 9092
  - name: kafka-external
    port: 29092
  - name: pandaproxy-internal
    port: 8082
  - name: pandaproxy-external
    port: 8083
  - name: schema-registry-internal
    port: 8081
  - name: schema-registry-external
    port: 8084
  - name: rpc
    port: 33145
  selector:
    app: kafka
YAML

cat << 'YAML' > infrastructure/k8s/backend.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend
  labels:
    app: backend
spec:
  replicas: 1
  selector:
    matchLabels:
      app: backend
  template:
    metadata:
      labels:
        app: backend
    spec:
      containers:
      - name: backend
        image: swiss-backend:latest
        imagePullPolicy: IfNotPresent
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:postgresql://postgres:5432/swiss_db"
        - name: SPRING_DATASOURCE_USERNAME
          value: "postgres"
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: app-secrets
              key: postgres-password
              optional: true
        - name: SPRING_DATASOURCE_DRIVER_CLASS_NAME
          value: "org.postgresql.Driver"
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: app-secrets
              key: jwt-secret
              optional: true
        - name: SPRING_DATA_REDIS_HOST
          value: "redis"
        - name: SPRING_DATA_REDIS_PORT
          value: "6379"
        - name: SPRING_AI_OPENAI_API_KEY
          valueFrom:
            secretKeyRef:
              name: app-secrets
              key: openai-api-key
              optional: true
        - name: SPRING_AI_OLLAMA_BASE_URL
          value: "http://host.docker.internal:11434"
        - name: JAVA_OPTS
          value: "-Xmx256m -Xms256m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 15
---
apiVersion: v1
kind: Service
metadata:
  name: backend
spec:
  ports:
  - port: 8080
  selector:
    app: backend
YAML

cat << 'YAML' > infrastructure/k8s/bff.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: bff
  labels:
    app: bff
spec:
  replicas: 1
  selector:
    matchLabels:
      app: bff
  template:
    metadata:
      labels:
        app: bff
    spec:
      containers:
      - name: bff
        image: swiss-bff:latest
        imagePullPolicy: IfNotPresent
        ports:
        - containerPort: 8081
        env:
        - name: BACKEND_URL
          value: "http://backend:8080"
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: app-secrets
              key: jwt-secret
              optional: true
        - name: JAVA_OPTS
          value: "-Xmx128m -Xms128m"
        livenessProbe:
          httpGet:
            path: /health
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 15
---
apiVersion: v1
kind: Service
metadata:
  name: bff
spec:
  ports:
  - port: 8081
  selector:
    app: bff
YAML

cat << 'YAML' > infrastructure/k8s/nginx.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx
  labels:
    app: nginx
spec:
  replicas: 1
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: nginx:alpine
        ports:
        - containerPort: 80
        volumeMounts:
        - name: nginx-config
          mountPath: /etc/nginx/nginx.conf
          subPath: nginx.conf
          readOnly: true
        - name: frontend-host
          mountPath: /usr/share/nginx/html/host
          readOnly: true
        - name: frontend-customer
          mountPath: /usr/share/nginx/html/customer
          readOnly: true
        - name: frontend-admin
          mountPath: /usr/share/nginx/html/admin
          readOnly: true
        - name: frontend-rider
          mountPath: /usr/share/nginx/html/rider
          readOnly: true
      volumes:
      - name: nginx-config
        configMap:
          name: nginx-config
      - name: frontend-host
        emptyDir: {}
      - name: frontend-customer
        emptyDir: {}
      - name: frontend-admin
        emptyDir: {}
      - name: frontend-rider
        emptyDir: {}
---
apiVersion: v1
kind: Service
metadata:
  name: nginx
spec:
  type: LoadBalancer
  ports:
  - port: 80
  selector:
    app: nginx
YAML

cat << 'YAML' > infrastructure/k8s/prometheus.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: prometheus
  labels:
    app: prometheus
spec:
  replicas: 1
  selector:
    matchLabels:
      app: prometheus
  template:
    metadata:
      labels:
        app: prometheus
    spec:
      containers:
      - name: prometheus
        image: prom/prometheus:latest
        ports:
        - containerPort: 9090
        volumeMounts:
        - name: prometheus-config
          mountPath: /etc/prometheus/prometheus.yml
          subPath: prometheus.yml
          readOnly: true
      volumes:
      - name: prometheus-config
        configMap:
          name: prometheus-config
---
apiVersion: v1
kind: Service
metadata:
  name: prometheus
spec:
  ports:
  - port: 9090
  selector:
    app: prometheus
YAML

cat << 'YAML' > infrastructure/k8s/grafana.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: grafana
  labels:
    app: grafana
spec:
  replicas: 1
  selector:
    matchLabels:
      app: grafana
  template:
    metadata:
      labels:
        app: grafana
    spec:
      containers:
      - name: grafana
        image: grafana/grafana:latest
        ports:
        - containerPort: 3000
---
apiVersion: v1
kind: Service
metadata:
  name: grafana
spec:
  type: LoadBalancer
  ports:
  - port: 3000
  selector:
    app: grafana
YAML

cat << 'YAML' > infrastructure/k8s/zipkin.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: zipkin
  labels:
    app: zipkin
spec:
  replicas: 1
  selector:
    matchLabels:
      app: zipkin
  template:
    metadata:
      labels:
        app: zipkin
    spec:
      containers:
      - name: zipkin
        image: openzipkin/zipkin:latest
        ports:
        - containerPort: 9411
---
apiVersion: v1
kind: Service
metadata:
  name: zipkin
spec:
  ports:
  - port: 9411
  selector:
    app: zipkin
YAML

