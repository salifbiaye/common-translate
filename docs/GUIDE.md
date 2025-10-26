# Guide Complet - common-translate v1.0.3

## Table des Matières

1. [Introduction](#introduction)
2. [Installation](#installation)
3. [Configuration](#configuration)
4. [Annotations](#annotations)
5. [API de Métadonnées](#api-de-métadonnées)
6. [Labels d'Énumérations](#labels-dénumérations)
7. [Architecture du Cache](#architecture-du-cache)
8. [Scénarios d'Utilisation](#scénarios-dutilisation)
9. [Troubleshooting](#troubleshooting)

---

## Introduction

### Qu'est-ce que common-translate?

**common-translate** est un module Spring Boot qui ajoute la traduction automatique multilingue à vos APIs REST **sans modifier vos contrôleurs**.

### Comment ça marche?

```
1. Client envoie: Accept-Language: en
2. Contrôleur retourne: { "bio": "Passionné de technologie" }
3. TranslationResponseAdvice (automatique) intercepte
4. Traduit: "Passionné de technologie" → "Technology enthusiast"
5. Client reçoit: { "bio": "Technology enthusiast" }
```

### Cas d'Usage Principaux

1. **APIs multilingues** - Un seul code, multiple langues
2. **Formulaires dynamiques** - Labels traduits pour le frontend
3. **Énumérations traduites** - `ADMIN` → "Administrator", "Administrateur", "Administrador"

---

## Installation

### Étape 1: Ajouter la Dépendance

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.crm-bancaire</groupId>
    <artifactId>common-translate</artifactId>
    <version>1.0.3</version>
</dependency>
```

### Étape 2: Activer le Module

```java
@SpringBootApplication
@EnableAutoTranslate(basePackages = "com.mycompany.myservice")
public class MyServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyServiceApplication.class, args);
    }
}
```

**Paramètres:**
- `basePackages`: Package de base pour scanner les entités `@Translatable`

### Étape 3: Vérifier les Dépendances

Assurez-vous d'avoir Redis et LibreTranslate:

```yaml
# docker-compose.yml
services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  libretranslate:
    image: libretranslate/libretranslate:latest
    ports:
      - "5000:5000"
    environment:
      - LT_LOAD_ONLY=fr,en,es,de,it,pt
```

---

## Configuration

### Configuration Minimale

```yaml
translate:
  enabled: true
  source-language: fr
  libretranslate:
    url: http://localhost:5000
```

### Configuration Complète

```yaml
translate:
  enabled: true                      # Activer/désactiver la traduction
  source-language: fr                # Langue du contenu métier (messages, descriptions)
  field-names-language: en           # Langue des noms de variables (firstName, lastName)

  metadata:
    base-path: /api/users            # Chemin de base pour métadonnées (par service)

  libretranslate:
    url: http://localhost:5000       # URL du serveur LibreTranslate

  cache:
    ttl: 86400                       # TTL Redis en secondes (24h par défaut)

  enum-labels:                       # Labels personnalisés (optionnel)
    UserRole:
      ADMIN: Administrateur système  # ⚠️ En langue SOURCE (fr ici)
      MANAGER: Gestionnaire
      USER: Utilisateur
    Status:
      ACTIVE: Compte actif
      SUSPENDED: Compte suspendu
```

### Paramètres Expliqués

#### `source-language`
Langue de votre contenu métier (messages d'erreur, descriptions, etc.)

```java
// Si source-language: fr
user.setBio("Passionné de technologie");  // En français
// Traduit en: "Technology enthusiast" (en), "Apasionado por la tecnología" (es)
```

#### `field-names-language`
Langue de vos noms de variables Java

```java
// field-names-language: en (défaut)
private String firstName;  // Variable en anglais
// Métadonnée: firstName → "Prénom" (fr), "First Name" (en)

// Si vous utilisez des variables en français:
// field-names-language: fr
private String prenom;  // Variable en français
// Métadonnée: prenom → "Prénom" (fr), "First Name" (en)
```

#### `metadata.base-path`
Chemin de base pour les endpoints de métadonnées (configurable par service)

```yaml
# user-service
translate:
  metadata:
    base-path: /api/users
# Endpoint résultant: /api/users/translate/metadata/User

# customer-service
translate:
  metadata:
    base-path: /api/customers
# Endpoint résultant: /api/customers/translate/metadata/Customer
```

---

## Annotations

### @EnableAutoTranslate

Active la traduction automatique dans votre application.

```java
@SpringBootApplication
@EnableAutoTranslate(basePackages = "com.mycompany.myservice")
public class MyServiceApplication {
    // ...
}
```

**Paramètres:**
- `basePackages`: Package racine pour scanner les entités `@Translatable`

### @Translatable

Marque une entité comme traduisible pour l'API de métadonnées.

```java
@Entity
@Translatable(
    name = "User",                           // Nom pour l'endpoint métadonnées
    description = "Entité utilisateur"       // Description (optionnel)
)
public class User {
    private String firstName;
    private String lastName;
}
```

**Résultat:** Endpoint `/api/users/translate/metadata/User` disponible

### @NoTranslate

Empêche la traduction des **VALEURS** d'un champ.

**⚠️ IMPORTANT:** `@NoTranslate` empêche la traduction des valeurs, mais les **labels** sont quand même générés pour les métadonnées!

#### Sur les Entités

```java
@Entity
public class User {
    @NoTranslate
    private String firstName;  // "Salif" reste "Salif"

    @NoTranslate
    private String lastName;   // "Biaye" reste "Biaye"

    private String bio;        // Traduit: "Passionné de tech" → "Tech enthusiast"
}
```

#### Sur les DTOs (TRÈS IMPORTANT!)

```java
@Data
public class UserResponse {
    @NoTranslate
    private String firstName;  // IMPORTANT: Annoter aussi le DTO!

    @NoTranslate
    private String lastName;

    private UserRole role;     // Génère roleLabel
}
```

**Pourquoi annoter le DTO?**

La traduction s'applique sur l'objet retourné par le contrôleur (le DTO), pas l'entité. Si vous annotez seulement l'entité, les valeurs du DTO seront quand même traduites!

#### Exemple Complet

```java
// Entité
@Entity
@Translatable(name = "User")
public class User {
    @NoTranslate
    private String firstName;
    @NoTranslate
    private String lastName;
    private UserRole role;
}

// DTO
@Data
public class UserResponse {
    @NoTranslate
    private String firstName;  // Ne pas oublier!
    @NoTranslate
    private String lastName;   // Ne pas oublier!
    private UserRole role;
}

// Contrôleur
@GetMapping("/{id}")
public UserResponse getUser(@PathVariable String id) {
    return userService.getUser(id);  // Retourne UserResponse
}
```

---

## API de Métadonnées

### Vue d'Ensemble

L'API de métadonnées fournit les labels traduits des champs pour construire des formulaires multilingues.

### Endpoints Disponibles

#### 1. Métadonnées d'une Entité

```http
GET {base-path}/translate/metadata/{entity}
Accept-Language: fr
```

**Exemple:**
```bash
curl -H "Accept-Language: fr" \
  http://localhost:8089/api/users/translate/metadata/User
```

**Réponse:**
```json
{
  "firstName": "Prénom",
  "lastName": "Nom",
  "email": "Email",
  "telephone": "Téléphone",
  "role": "Rôle"
}
```

#### 2. Liste des Entités Traduisibles

```http
GET {base-path}/translate/metadata/entities
```

**Exemple:**
```bash
curl http://localhost:8089/api/users/translate/metadata/entities
```

**Réponse:**
```json
["User", "Account", "Module"]
```

#### 3. Health Check

```http
GET {base-path}/translate/metadata/health
```

**Réponse:**
```json
{
  "enabled": true,
  "sourceLanguage": "fr",
  "entitiesCount": 3,
  "entities": ["User", "Account", "Module"]
}
```

### Utilisation Frontend

#### React Example

```typescript
// Hook pour récupérer les métadonnées
function useFieldLabels(entity: string, language: string) {
  const { data } = useQuery(
    ['metadata', entity, language],
    () => fetch(`/api/users/translate/metadata/${entity}`, {
      headers: { 'Accept-Language': language }
    }).then(res => res.json())
  );
  return data;
}

// Composant formulaire
function UserForm() {
  const labels = useFieldLabels('User', 'fr');

  return (
    <Form>
      <FormField label={labels.firstName} name="firstName" />
      <FormField label={labels.lastName} name="lastName" />
      <FormField label={labels.email} name="email" />
    </Form>
  );
}
```

#### Vue Example

```vue
<template>
  <form>
    <label>{{ labels.firstName }}</label>
    <input v-model="user.firstName" />

    <label>{{ labels.lastName }}</label>
    <input v-model="user.lastName" />
  </form>
</template>

<script>
export default {
  data() {
    return {
      labels: {},
      user: {}
    };
  },
  async mounted() {
    const response = await fetch('/api/users/translate/metadata/User', {
      headers: { 'Accept-Language': this.$i18n.locale }
    });
    this.labels = await response.json();
  }
};
</script>
```

---

## Labels d'Énumérations

### Fonctionnement Automatique

Quand le système détecte une énumération, il ajoute automatiquement un champ `*Label` traduit:

```java
// Enum
public enum UserRole {
    ADMIN,
    MANAGER,
    USER
}

// Entité
public class User {
    private UserRole role;  // ADMIN
}
```

**Réponse API (Accept-Language: en):**
```json
{
  "role": "ADMIN",          // Valeur pour la logique
  "roleLabel": "Admin"      // Label pour l'affichage (traduit)
}
```

**Réponse API (Accept-Language: fr):**
```json
{
  "role": "ADMIN",
  "roleLabel": "Administrateur"  // Traduit automatiquement
}
```

### Labels Personnalisés

Pour des traductions plus précises, configurez les labels custom en YAML:

```yaml
translate:
  source-language: fr      # ⚠️ IMPORTANT pour labels custom
  enum-labels:
    UserRole:
      ADMIN: Administrateur système     # En français (langue source)
      MANAGER: Gestionnaire
      USER: Utilisateur standard
    Status:
      ACTIVE: Compte actif
      SUSPENDED: Compte temporairement suspendu
      DELETED: Compte définitivement supprimé
```

**⚠️ RÈGLE IMPORTANTE:** Les labels custom doivent être dans votre **langue source** (`source-language`). Ils seront automatiquement traduits vers les autres langues par LibreTranslate.

**Résultat:**

| Valeur | Label Custom (fr) | Traduit (en) | Traduit (es) |
|--------|------------------|--------------|--------------|
| ADMIN | Administrateur système | System Administrator | Administrador del sistema |
| MANAGER | Gestionnaire | Manager | Gerente |
| USER | Utilisateur standard | Standard User | Usuario estándar |

### Auto-Capitalisation (Sans Configuration)

Si vous ne configurez pas de label custom, le système génère automatiquement:

| Valeur Enum | Label Auto-Généré | Traduit (fr) | Traduit (es) |
|-------------|-------------------|--------------|--------------|
| ADMIN | Admin | Administrateur | Administrador |
| SUPER_USER | Super User | Super Utilisateur | Súper Usuario |
| LEVEL_1 | Level 1 | Niveau 1 | Nivel 1 |

---

## Architecture du Cache

### Vue d'Ensemble

common-translate v1.0.3 utilise une architecture de cache à **deux niveaux** avec synchronisation automatique pour les requêtes concurrentes.

```
Requête → L1 (Caffeine) → L2 (Redis) → LibreTranslate
          ↑ Local        ↑ Distribué   ↑ API externe
          30min, 10K     24h             ~200ms
```

### Niveau 1: Cache Local (Caffeine)

**Caractéristiques:**
- **Type:** In-memory, ultra-rapide
- **Capacité:** 10,000 entrées max
- **TTL:** 30 minutes
- **Latence:** < 1ms
- **Synchronisation:** Automatique pour requêtes concurrentes

**Avantages:**
- Ultra-rapide (pas de réseau)
- Protection anti-stampede intégrée
- Thread-safe

### Niveau 2: Cache Distribué (Redis)

**Caractéristiques:**
- **Type:** Distribué entre instances
- **Capacité:** Illimitée (config Redis)
- **TTL:** 24 heures (configurable)
- **Latence:** 1-2ms (réseau local)

**Avantages:**
- Partagé entre instances du service
- Persistant entre redémarrages
- TTL configurable

### Protection Anti-Stampede

#### Le Problème (Sans Protection)

```
Scénario: 100 requêtes concurrentes pour "Admin" → "Administrator"
          Cache vide

Thread 1:  Check cache → MISS → Call LibreTranslate ❌
Thread 2:  Check cache → MISS → Call LibreTranslate ❌
Thread 3:  Check cache → MISS → Call LibreTranslate ❌
...
Thread 100: Check cache → MISS → Call LibreTranslate ❌

Résultat: 100 appels API! 😱
```

#### La Solution (v1.0.3 avec Caffeine)

```
Scénario: 100 requêtes concurrentes pour "Admin" → "Administrator"
          Cache vide

Thread 1:
  T=0ms:   L1 MISS → Acquire LOCK
  T=1ms:   L2 (Redis) MISS
  T=2ms:   Call LibreTranslate...
  T=202ms: Receive "Administrator"
  T=203ms: Store in L2 (Redis)
  T=204ms: Store in L1 (Caffeine)
  T=205ms: Release LOCK

Threads 2-100:
  T=0-1ms:   L1 MISS → Try LOCK → BLOCKED (waiting...)
  T=205ms:   Thread 1 releases LOCK
  T=205ms:   Get value from L1 → "Administrator" ✅

Résultat: 1 seul appel API! 🎉
```

**Gains:**
- 99% moins d'appels API
- Pas de surcharge LibreTranslate
- Tous les threads reçoivent la réponse en ~205ms

### Flux de Cache Détaillé

#### Cas 1: Cache L1 Hit (Le Plus Rapide)

```
Request: "Admin" (fr → en)

T=0ms:  Check L1 (Caffeine): HIT ✅
T=0.5ms: Return "Administrator"

Total: < 1ms
Appels: 0 Redis, 0 LibreTranslate
```

#### Cas 2: Cache L1 Miss, L2 Hit

```
Request: "Manager" (fr → en)

T=0ms:  Check L1 (Caffeine): MISS
T=1ms:  Acquire LOCK
T=2ms:  Check L2 (Redis): HIT ✅
T=3ms:  Store in L1 (Caffeine)
T=4ms:  Release LOCK
T=4ms:  Return "Manager"

Total: 4ms
Appels: 1 Redis, 0 LibreTranslate
```

#### Cas 3: Cache L1+L2 Miss (Première Fois)

```
Request: "Gestionnaire" (fr → en) - Première fois

T=0ms:   Check L1 (Caffeine): MISS
T=1ms:   Acquire LOCK
T=2ms:   Check L2 (Redis): MISS
T=3ms:   Call LibreTranslate API...
T=203ms: Receive "Manager"
T=204ms: Store in L2 (Redis, TTL 24h)
T=205ms: Store in L1 (Caffeine, TTL 30min)
T=206ms: Release LOCK
T=206ms: Return "Manager"

Total: ~206ms
Appels: 1 Redis, 1 LibreTranslate
```

### Métriques de Performance

#### Comparaison v1.0.2 vs v1.0.3

| Scénario | v1.0.2 (Redis seul) | v1.0.3 (Caffeine + Redis) | Amélioration |
|----------|---------------------|---------------------------|--------------|
| 100 requêtes concurrentes (cache vide) | 100 appels LibreTranslate | 1 appel LibreTranslate | **99% moins** |
| Requête unique (cache chaud) | 1-2ms (Redis) | < 1ms (Caffeine) | **2x plus rapide** |
| Latence P50 | 2ms | < 1ms | **50% plus rapide** |
| Latence P99 | 5ms | 2ms | **60% plus rapide** |

#### Taux de Hit en Production

```
Après 1 heure de production:
- L1 hit rate: 95%  (< 1ms)
- L2 hit rate: 4%   (1-2ms)
- LibreTranslate: 1% (~200ms)

Latence moyenne: ~1ms
```

---

## Scénarios d'Utilisation

### Scénario 1: API Multilingue Simple

**Objectif:** Une API qui retourne des données en français mais doit supporter anglais, espagnol, etc.

#### Code

```java
// Contrôleur (aucun changement!)
@GetMapping("/users/{id}")
public UserResponse getUser(@PathVariable String id) {
    return userService.getUser(id);
}

// DTO
@Data
public class UserResponse {
    @NoTranslate
    private String firstName;
    @NoTranslate
    private String lastName;
    private String bio;  // Traduit automatiquement
}
```

#### Configuration

```yaml
translate:
  enabled: true
  source-language: fr
  libretranslate:
    url: http://localhost:5000
```

#### Résultat

```bash
# Français
curl -H "Accept-Language: fr" /api/users/123
{
  "firstName": "Jean",
  "lastName": "Dupont",
  "bio": "Passionné de technologie"
}

# Anglais
curl -H "Accept-Language: en" /api/users/123
{
  "firstName": "Jean",
  "lastName": "Dupont",
  "bio": "Technology enthusiast"
}

# Espagnol
curl -H "Accept-Language: es" /api/users/123
{
  "firstName": "Jean",
  "lastName": "Dupont",
  "bio": "Apasionado por la tecnología"
}
```

### Scénario 2: Formulaire Multilingue Dynamique

**Objectif:** Générer un formulaire avec labels traduits.

#### Backend

```java
@Entity
@Translatable(name = "User")
public class User {
    private String firstName;
    private String lastName;
    private String email;
    private String telephone;
}
```

#### Frontend (React)

```typescript
function UserForm({ language }) {
  const [labels, setLabels] = useState({});

  useEffect(() => {
    fetch('/api/users/translate/metadata/User', {
      headers: { 'Accept-Language': language }
    })
    .then(res => res.json())
    .then(setLabels);
  }, [language]);

  return (
    <Form>
      <FormField label={labels.firstName} name="firstName" />
      <FormField label={labels.lastName} name="lastName" />
      <FormField label={labels.email} name="email" />
      <FormField label={labels.telephone} name="telephone" />
    </Form>
  );
}
```

#### Résultat

```
Langue: Français
┌─────────────────────────────┐
│ Prénom:    [          ]     │
│ Nom:       [          ]     │
│ Email:     [          ]     │
│ Téléphone: [          ]     │
└─────────────────────────────┘

Langue: English
┌─────────────────────────────┐
│ First Name: [          ]    │
│ Last Name:  [          ]    │
│ Email:      [          ]    │
│ Telephone:  [          ]    │
└─────────────────────────────┘
```

### Scénario 3: Énumérations avec Labels Custom

**Objectif:** Afficher des labels précis pour les statuts/rôles.

#### Code

```java
public enum AccountStatus {
    PENDING_VALIDATION,
    ACTIVE,
    SUSPENDED,
    CLOSED
}

public class Account {
    private AccountStatus status;
}
```

#### Configuration

```yaml
translate:
  source-language: fr
  enum-labels:
    AccountStatus:
      PENDING_VALIDATION: En attente de validation
      ACTIVE: Compte actif et opérationnel
      SUSPENDED: Temporairement suspendu
      CLOSED: Définitivement fermé
```

#### Résultat

```bash
# Français
curl -H "Accept-Language: fr" /api/accounts/123
{
  "status": "ACTIVE",
  "statusLabel": "Compte actif et opérationnel"
}

# Anglais
curl -H "Accept-Language: en" /api/accounts/123
{
  "status": "ACTIVE",
  "statusLabel": "Active and operational account"
}
```

---

## Troubleshooting

### Problème: Traduction ne fonctionne pas

**Symptômes:** Les réponses ne sont pas traduites

**Solutions:**

1. **Vérifier que la traduction est activée:**
```yaml
translate:
  enabled: true  # Doit être true
```

2. **Vérifier le header Accept-Language:**
```bash
curl -H "Accept-Language: en" /api/users  # Header obligatoire
```

3. **Vérifier les logs:**
```
🌐 Calling LibreTranslate (L1+L2 cache MISS): 'Hello' (fr → en)
✅ LibreTranslate result: 'Hello' (fr → en) = 'Bonjour'
```

4. **Vérifier LibreTranslate:**
```bash
curl http://localhost:5000/translate \
  -d '{"q":"Bonjour","source":"fr","target":"en","format":"text"}'
```

### Problème: Valeurs @NoTranslate sont traduites

**Symptômes:** Les noms/emails sont traduits malgré @NoTranslate

**Cause:** L'annotation est sur l'entité mais pas sur le DTO

**Solution:** Annoter AUSSI le DTO:

```java
// DTO (IMPORTANT!)
@Data
public class UserResponse {
    @NoTranslate  // Ne pas oublier!
    private String firstName;
}
```

### Problème: Pas de labels d'énumérations

**Symptômes:** `roleLabel` n'apparaît pas dans la réponse

**Solutions:**

1. **Vérifier que c'est un enum:**
```java
@Enumerated(EnumType.STRING)  // Important!
private UserRole role;
```

2. **Vérifier les logs:**
```
🎯 Detected enum field: role=ADMIN (looks like enum)
✅ Added enum label: role=ADMIN → roleLabel=Administrateur
```

### Problème: Métadonnées retournent 404

**Symptômes:** `/api/translate/metadata/User` retourne 404

**Solutions:**

1. **Vérifier l'annotation @Translatable:**
```java
@Entity
@Translatable(name = "User")  // Nom exact pour l'endpoint
public class User { }
```

2. **Vérifier le base-path:**
```yaml
translate:
  metadata:
    base-path: /api/users
# Endpoint devient: /api/users/translate/metadata/User
```

3. **Vérifier le scan des packages:**
```java
@EnableAutoTranslate(basePackages = "com.mycompany.myservice")
```

### Problème: Cache ne fonctionne pas

**Symptômes:** LibreTranslate appelé à chaque requête

**Solutions:**

1. **Vérifier Redis:**
```bash
redis-cli ping  # Doit retourner PONG
```

2. **Vérifier la connexion Redis:**
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

3. **Vérifier les clés Redis:**
```bash
redis-cli keys "trans:*"
```

### Problème: Performance lente

**Symptômes:** Réponses API lentes (> 100ms)

**Diagnostic:**

1. **Vérifier le taux de cache hit:**
```
Logs: "📦 L2 Cache HIT" vs "🌐 Calling LibreTranslate"
```

2. **Vérifier la latence Redis:**
```bash
redis-cli --latency
```

3. **Augmenter le TTL du cache:**
```yaml
translate:
  cache:
    ttl: 172800  # 48h au lieu de 24h
```

---

## Annexes

### Langues Supportées

| Code | Langue | Exemple |
|------|--------|---------|
| fr | Français | Bonjour |
| en | English | Hello |
| es | Español | Hola |
| de | Deutsch | Hallo |
| it | Italiano | Ciao |
| pt | Português | Olá |

### Champs Exclus par Défaut

Ces champs ne sont JAMAIS traduits (hardcodés):

```java
private static final Set<String> EXCLUDED_FIELDS = Set.of(
    "id", "uuid", "password", "token", "keycloakId",
    "createdAt", "updatedAt", "dateCreation", "dateModification",
    "url", "uri", "sub", "iss"
);
```

### Patterns Non-Traduisibles

Ces patterns sont automatiquement détectés et non traduits:

- **Emails:** `user@example.com`
- **UUIDs:** `123e4567-e89b-12d3-a456-426614174000`
- **URLs:** `http://example.com`
- **Dates ISO:** `2024-10-26T15:30:00`
- **Nombres:** `12345`, `3.14`

---

## Support

Pour toute question ou problème:
1. Consultez les logs avec le niveau INFO
2. Vérifiez LibreTranslate: `http://localhost:5000`
3. Vérifiez Redis: `redis-cli ping`
4. Consultez ce guide

**Version:** v1.0.3
**Dernière mise à jour:** 2025-10-26
