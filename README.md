# Taller de Modularización con Virtualización e Introducción a Docker

**Autor:** Juan Sebastian Buitrago Piñeros  
**Materia:** Arquitecturas Empresariales (AREP)

## Resumen

Aplicación web construida con un framework HTTP propio (sin Spring), que soporta concurrencia mediante un pool de hilos (`ExecutorService`) y apagado elegante (`graceful shutdown`) mediante un shutdown hook de la JVM. La aplicación se despliega en contenedores Docker y puede ejecutarse localmente o en AWS EC2.

## Arquitectura

```
┌─────────────────────────────────────────────────────┐
│                    Cliente (Browser)                 │
└──────────────────────┬──────────────────────────────┘
                       │ HTTP
                       ▼
┌─────────────────────────────────────────────────────┐
│              Docker Container (web)                  │
│  ┌───────────────────────────────────────────────┐  │
│  │            WebServer (puerto 6000)             │  │
│  │  ┌─────────────────────────────────────────┐  │  │
│  │  │     ExecutorService (Thread Pool)        │  │  │
│  │  │  ┌────────┐ ┌────────┐ ┌────────┐      │  │  │
│  │  │  │Thread 1│ │Thread 2│ │Thread N│      │  │  │
│  │  │  └────────┘ └────────┘ └────────┘      │  │  │
│  │  └─────────────────────────────────────────┘  │  │
│  │                                               │  │
│  │  Rutas registradas:                           │  │
│  │  GET /           → Página principal           │  │
│  │  GET /hello      → Hello World                │  │
│  │  GET /greeting   → Saludo personalizado       │  │
│  │  GET /api/greeting → JSON greeting            │  │
│  │  GET /api/health → Health check               │  │
│  └───────────────────────────────────────────────┘  │
│                      Puerto mapeado: 8087 / 34000   │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│            Docker Container (db) - MongoDB           │
│                    Puerto: 27017                     │
└─────────────────────────────────────────────────────┘
```

## Diseño de Clases

### Framework (`co.edu.escuelaing.arep.framework`)

| Clase            | Responsabilidad                                                                                                                                                                                            |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **WebServer**    | Servidor HTTP concurrente. Acepta conexiones en un `ServerSocket` y despacha cada petición a un hilo del `ExecutorService`. Registra rutas GET/POST, sirve archivos estáticos y soporta shutdown elegante. |
| **HttpRequest**  | Parsea la petición HTTP cruda desde el socket: método, ruta, query params, headers y body.                                                                                                                 |
| **HttpResponse** | Construye y envía la respuesta HTTP con status code, headers y body a través del socket.                                                                                                                   |
| **RouteHandler** | Interfaz funcional `(HttpRequest, HttpResponse) -> void` para definir handlers de rutas.                                                                                                                   |

### Aplicación (`co.edu.escuelaing.arep.app`)

| Clase   | Responsabilidad                                                                                                                         |
| ------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| **App** | Punto de entrada. Configura las rutas y arranca el `WebServer` en el puerto definido por la variable de entorno `PORT` (default: 5000). |

### Diagrama de Clases

```
┌──────────────────────┐
│     <<interface>>     │
│     RouteHandler      │
│──────────────────────│
│ + handle(req, res)   │
└──────────┬───────────┘
           │ usa
┌──────────┴───────────┐       ┌─────────────┐       ┌──────────────┐
│      WebServer        │──────▶│  HttpRequest │       │  HttpResponse │
│──────────────────────│       │─────────────│       │──────────────│
│ - port: int           │       │ - method     │       │ - statusCode  │
│ - threadPool          │       │ - path       │       │ - headers     │
│ - getRoutes: Map      │       │ - queryParams│       │ - body        │
│ - running: AtomicBool │       │ - headers    │       │──────────────│
│──────────────────────│       │ - body       │       │ + status()    │
│ + get(path, handler)  │       │─────────────│       │ + body()      │
│ + post(path, handler) │       │ + getMethod() │       │ + send()      │
│ + start()             │       │ + getPath()   │       │ + sendJson()  │
│ + stop()              │       │ + getQueryParam()│    └──────────────┘
└──────────────────────┘       └─────────────┘
           │
           │ instancia
┌──────────┴───────────┐
│        App            │
│──────────────────────│
│ + main(args)          │
│ - getPort()           │
└──────────────────────┘
```

## Concurrencia

El servidor utiliza un `ExecutorService` con un pool fijo de hilos (`availableProcessors * 2`). Cada conexión entrante se delega a un hilo del pool, permitiendo atender múltiples peticiones simultáneamente sin bloquear el hilo principal.

```java
threadPool = Executors.newFixedThreadPool(threadPoolSize);
// ...
Socket clientSocket = serverSocket.accept();
threadPool.submit(() -> handleClient(clientSocket));
```

Las rutas se almacenan en `ConcurrentHashMap` para acceso seguro desde múltiples hilos.

## Apagado Elegante (Graceful Shutdown)

Se registra un **shutdown hook** que al recibir `SIGTERM` o `SIGINT`:

1. Pone `running` en `false` (AtomicBoolean) para detener el loop de aceptación.
2. Llama a `threadPool.shutdown()` para dejar que se completen las peticiones en curso.
3. Espera hasta 30 segundos (`awaitTermination`) antes de forzar el cierre.
4. Cierra el `ServerSocket`.

Esto es especialmente importante en Docker, donde `docker stop` envía `SIGTERM` al proceso.

## Prerrequisitos

- Java 17+
- Maven 3.8+
- Docker y Docker Compose

## Compilar el Proyecto

```bash
mvn clean install
```

## Ejecutar Localmente (sin Docker)

```bash
java -cp "target/classes:target/dependency/*" co.edu.escuelaing.arep.app.App
```

En Windows usar `;` en lugar de `:`:

```bash
java -cp "target/classes;target/dependency/*" co.edu.escuelaing.arep.app.App
```

Acceder a: http://localhost:5000/

## Generar la Imagen Docker

### 1. Compilar el proyecto

```bash
mvn clean install
```

### 2. Construir la imagen

```bash
docker build --tag dockerarep .
```

### 3. Verificar la imagen

```bash
docker images
```

### 4. Ejecutar contenedores

```bash
docker run -d -p 34000:6000 --name container1 dockerarep
docker run -d -p 34001:6000 --name container2 dockerarep
docker run -d -p 34002:6000 --name container3 dockerarep
```

Acceder a:

- http://localhost:34000/hello
- http://localhost:34001/hello
- http://localhost:34002/hello

### 5. Usar Docker Compose

```bash
docker-compose up -d
```

Esto crea el servicio web en el puerto 8087 y una instancia de MongoDB en el puerto 27017.

Acceder a: http://localhost:8087/hello

### 6. Subir imagen a Docker Hub

```bash
docker tag dockerarep <tu-usuario>/dockerarep
docker login
docker push <tu-usuario>/dockerarep:latest
```

## Despliegue en AWS EC2

### 1. Conectarse a la instancia EC2

```bash
ssh -i "tu-llave.pem" ec2-user@<ip-publica>
```

### 2. Instalar Docker

```bash
sudo yum update -y
sudo yum install docker
sudo service docker start
sudo usermod -a -G docker ec2-user
```

Desconectarse y reconectarse para que tome efecto.

### 3. Ejecutar el contenedor

```bash
docker run -d -p 42000:6000 --name webapp <tu-usuario>/dockerarep
```

### 4. Abrir puertos

En la consola de AWS, editar el Security Group de la instancia EC2 para permitir tráfico entrante en el puerto 42000.

### 5. Acceder

```
http://<dns-publico-ec2>:42000/hello
```

## Endpoints

| Método | Ruta                   | Descripción                                      |
| ------ | ---------------------- | ------------------------------------------------ |
| GET    | `/`                    | Página principal con links a todos los endpoints |
| GET    | `/hello`               | Retorna "Hello World!"                           |
| GET    | `/greeting?name=X`     | Saludo personalizado en HTML                     |
| GET    | `/api/greeting?name=X` | Saludo en formato JSON                           |
| GET    | `/api/health`          | Health check (JSON con status y timestamp)       |

## Pruebas de Despliegue

### Local con Docker

https://ucatolicaeduco-my.sharepoint.com/:v:/g/personal/jsbuitrago37_ucatolica_edu_co/IQADGWvlZYMsTaVm_SSO6ZWQAXzfSR2qsurcqFNvu51VWrw?e=AXA6vM

### AWS EC2

![AWS deployment](docs/aws-deployment.png)
