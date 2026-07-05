# Diagramme de deploiement - MOTO'DI GO

Ce diagramme montre la repartition physique et logique des artefacts logiciels MOTO'DI GO sur les noeuds d'execution : terminaux mobiles, navigateur web admin, Internet, VPS de production, conteneurs Docker, base PostgreSQL/PostGIS, stockage, services externes et pipeline CI/CD.

## Principes retenus

- Un **noeud de deploiement** represente une ressource d'execution : appareil mobile, navigateur, serveur Linux, moteur Docker, conteneur, base de donnees ou service cloud.
- Un **artefact** represente un livrable deploye : APK/IPA Flutter, bundle web, JAR Spring Boot, application FastAPI, image Docker, schema SQL, fichiers KYC.
- Un **chemin de communication** indique le protocole reseau utilise : HTTPS/TLS, WSS/SSE, TCP, JDBC, Docker bridge, SSH, webhooks, push.
- Le diagramme correspond a une cible MVP/V1 deployable sur un VPS Ubuntu, avec possibilite de separer plus tard la base de donnees, le stockage et les services metier sur des noeuds dedies.

```plantuml
@startuml
title Diagramme de deploiement - MOTO'DI GO
left to right direction
skinparam shadowing false
skinparam componentStyle uml2
skinparam node {
  BackgroundColor #F8FAFC
  BorderColor #334155
}
skinparam database {
  BackgroundColor #ECFDF5
  BorderColor #15803D
}
skinparam cloud {
  BackgroundColor #EFF6FF
  BorderColor #2563EB
}
skinparam artifact {
  BackgroundColor #FFF7ED
  BorderColor #F59E0B
}

actor "Passager" as Passenger
actor "Mototaxi" as MotoDriver
actor "Transporteur / Agence" as Carrier
actor "Administrateur" as Admin

node "Smartphone Android/iOS\n<<device>>" as MobileDevice {
  artifact "MOTO'DI GO Mobile\nFlutter APK/IPA" as FlutterApp
  node "OS mobile\nAndroid / iOS\n<<executionEnvironment>>" as MobileOS {
    artifact "Cache local\nSQLite / fichiers" as LocalCache
    artifact "Cartes offline" as OfflineMaps
    artifact "File offline\noperations differees" as OfflineQueue
    artifact "Jetons NFC signes" as LocalNfcTokens
  }
  node "Capteurs & radios\n<<device>>" as MobileHardware {
    artifact "GPS" as Gps
    artifact "NFC" as NfcChip
    artifact "Push token\nFCM/APNs" as PushToken
  }
}

node "Poste administrateur\n<<device>>" as AdminLaptop {
  node "Navigateur Web\nChrome/Edge/Firefox\n<<executionEnvironment>>" as Browser {
    artifact "Dashboard Admin Web\nbundle JS/CSS" as AdminBundle
  }
}

cloud "Internet public" as Internet {
  artifact "DNS motodigo.cm" as DNS
  artifact "TLS public\nHTTPS 443" as PublicTLS
}

node "VPS Ubuntu Production\n<<device>>\n4 vCPU / 8-16GB RAM / SSD" as VPS {
  node "Reverse Proxy\nNginx / Caddy\n<<executionEnvironment>>" as ReverseProxy {
    artifact "Certificats TLS\nLet's Encrypt" as TLSCerts
    artifact "Routage HTTPS\n/api, /admin, /ws" as RoutingRules
  }

  node "Docker Engine\nDocker Compose network: motodigo_net\n<<executionEnvironment>>" as Docker {
    node "api-gateway container\nSpring Cloud Gateway\n<<container>>" as GatewayContainer {
      artifact "motodi-go-api-gateway.jar" as GatewayJar
      artifact "OpenAPI agregée" as OpenApiArtifact
    }

    node "backend-core container\nSpring Boot\n<<container>>" as CoreContainer {
      artifact "motodi-go-backend-core.jar" as CoreJar
      artifact "modules: auth, profils, KYC,\nSIG, wallet, paiement,\nreservation, support, KPI" as CoreModules
    }

    node "backend-ai container\nFastAPI / Python\n<<container>>" as AiContainer {
      artifact "motodi-go-backend-ai\nFastAPI app" as AiApp
      artifact "modele / fallback\nrecommandation" as AiModel
    }

    node "admin-dashboard container\nAstro/Next.js\n<<container>>" as AdminContainer {
      artifact "motodi-go-admin-dashboard\nSSR/static bundle" as AdminApp
    }

    database "postgres container\nPostgreSQL + PostGIS\n<<container>>" as PostgresContainer {
      artifact "schema metier SQL" as BusinessSchema
      artifact "extension PostGIS" as PostgisExt
      artifact "index spatiaux" as SpatialIndexes
      artifact "ledger wallet" as LedgerTables
      artifact "audit + KPI" as AuditTables
    }

    node "object-storage container\nMinIO / S3 compatible\n<<container>>" as ObjectStorage {
      artifact "documents KYC chiffrés\nAES-256" as KycObjects
      artifact "recus PDF" as ReceiptObjects
    }

    node "observability sidecar\n<<container>>" as Observability {
      artifact "logs applicatifs" as Logs
      artifact "healthchecks" as Healthchecks
      artifact "metriques techniques" as Metrics
    }
  }

  storage "Volumes persistants VPS\n<<storage>>" as PersistentVolumes {
    artifact "pgdata volume" as PgVolume
    artifact "object-storage volume" as ObjectVolume
    artifact "backup snapshots" as Backups
  }
}

cloud "Fournisseurs externes" as ExternalProviders {
  node "MTN MoMo API\n<<external>>" as MTN {
    artifact "callbacks paiement" as MtnCallbacks
  }
  node "Orange Money API\n<<external>>" as Orange {
    artifact "callbacks paiement" as OrangeCallbacks
  }
  node "Agregateur paiement\n<<external>>" as Aggregator {
    artifact "webhooks signes" as AggregatorWebhooks
  }
  node "FCM / APNs\n<<external>>" as PushProvider {
    artifact "push notifications" as PushArtifact
  }
  node "Mapbox / OpenStreetMap\n<<external>>" as MapProvider {
    artifact "tuiles cartes / geocoding" as MapTiles
  }
}

cloud "GitHub Cloud" as GitHub {
  artifact "Repositories\nmobile, gateway, core,\nai, admin, devops" as Repos
  node "GitHub Actions Runner\n<<CI/CD>>" as Actions {
    artifact "build images Docker" as BuildArtifacts
    artifact "tests + lint + scans" as Checks
  }
  artifact "Container Registry" as Registry
}

Passenger --> FlutterApp
MotoDriver --> FlutterApp
Carrier --> FlutterApp
Admin --> AdminBundle

FlutterApp --> Gps : lecture position GPS\nOS permissions
FlutterApp --> NfcChip : lecture/ecriture NFC\nAndroid NFC
FlutterApp --> LocalCache : lecture cache
FlutterApp --> OfflineMaps : cartes telechargees
FlutterApp --> OfflineQueue : operations hors ligne
FlutterApp --> LocalNfcTokens : jetons offline

FlutterApp --> Internet : HTTPS/TLS 1.3\nREST /api/v1
FlutterApp --> Internet : WSS/SSE/long polling\nsuivi temps reel
Browser --> Internet : HTTPS/TLS 1.3\n/admin

Internet --> ReverseProxy : TCP 443\nHTTPS public
ReverseProxy --> GatewayContainer : HTTP interne\nDocker bridge :8080
ReverseProxy --> AdminContainer : HTTP interne\nDocker bridge :3000

GatewayContainer --> CoreContainer : HTTP REST interne\nDocker bridge :8081
GatewayContainer --> AiContainer : HTTP REST interne\nDocker bridge :8000
GatewayContainer --> Observability : logs + correlation-id

CoreContainer --> PostgresContainer : JDBC/TCP 5432\nPostgreSQL/PostGIS
AiContainer --> CoreContainer : HTTP REST interne\nannonces anonymisees
AdminContainer --> GatewayContainer : HTTP interne ou public\n/api/v1 admin

CoreContainer --> ObjectStorage : S3 API interne\nHTTPS/HTTP 9000
CoreContainer --> Observability : logs, metrics, health
AiContainer --> Observability : logs IA, latence
GatewayContainer --> Observability : access logs, rate limits

PostgresContainer --> PgVolume : volume persistant
ObjectStorage --> ObjectVolume : volume persistant
PgVolume --> Backups : sauvegardes planifiees
ObjectVolume --> Backups : sauvegardes objets

CoreContainer --> MTN : HTTPS 443\ninit depot
CoreContainer --> Orange : HTTPS 443\ninit depot
CoreContainer --> Aggregator : HTTPS 443\nfallback paiement
MTN --> GatewayContainer : HTTPS 443\ncallback signe
Orange --> GatewayContainer : HTTPS 443\ncallback signe
Aggregator --> GatewayContainer : HTTPS 443\nwebhook signe

CoreContainer --> PushProvider : HTTPS 443\nsend push
PushProvider --> FlutterApp : push FCM/APNs
FlutterApp --> MapProvider : HTTPS 443\ntuiles + geocoding
CoreContainer --> MapProvider : HTTPS 443\ngeocoding / routage optionnel

Actions --> Repos : git checkout
Actions --> Checks : build/test/lint
Actions --> Registry : push images
Actions --> VPS : SSH 22\npull images + deploy
VPS --> Registry : HTTPS 443\npull images

note bottom of VPS
Deploiement MVP :
un VPS Ubuntu heberge reverse proxy,
Docker Compose, services, PostGIS,
stockage objet et observabilite minimale.
end note

note bottom of PostgresContainer
PostGIS stocke :
positions GPS, points origine/destination,
traces, zones, couches trafic,
index de proximite.
end note

note bottom of ExternalProviders
Les fournisseurs externes restent hors controle :
timeouts, idempotence, signature callbacks
et circuit breakers sont obligatoires.
end note

note bottom of MobileDevice
Le mobile doit fonctionner en reseau degrade :
cache cartes, file offline et NFC local
avant synchronisation serveur.
end note
@enduml
```

## Connexions reseau principales

| Source | Destination | Protocole | Usage |
|---|---|---|---|
| Application mobile | Reverse proxy/API Gateway | HTTPS/TLS 1.3, TCP 443 | REST `/api/v1`, auth, reservation, paiement, support |
| Application mobile | API Gateway | WSS/SSE/long polling sur TLS | Suivi trajet, GPS, statuts temps reel |
| Navigateur admin | Reverse proxy/dashboard | HTTPS/TLS 1.3, TCP 443 | Back-office, moderation, KPI |
| Reverse proxy | Gateway/Admin container | HTTP Docker bridge | Routage interne `/api`, `/admin`, `/ws` |
| Gateway | Backend core | HTTP REST interne | Services metier |
| Gateway | Backend IA | HTTP REST interne | Recommandations IA |
| Backend core | PostgreSQL/PostGIS | JDBC/TCP 5432 | Donnees metier et geospatiales |
| Backend core | Stockage objet | S3 API interne | Documents KYC, recus |
| Backend core | MTN/Orange/agregateur | HTTPS/TLS 443 | Initiation paiement |
| Fournisseurs paiement | API Gateway | HTTPS/TLS 443 | Callbacks/webhooks signes |
| Backend core | FCM/APNs | HTTPS/TLS 443 | Push notifications |
| Mobile/backend | Mapbox/OpenStreetMap | HTTPS/TLS 443 | Cartes, geocoding, routage |
| GitHub Actions | VPS | SSH 22 | Deploiement production |
| VPS | Registry | HTTPS/TLS 443 | Pull images Docker |

## Sources de notation

- [OMG UML 2.5.1](https://www.omg.org/spec/UML/2.5.1/) : specification UML formelle.
- [PlantUML - Deployment Diagram](https://plantuml.com/deployment-diagram) : syntaxe officielle pour `node`, `artifact`, `database`, `cloud`, liens et imbrications.

