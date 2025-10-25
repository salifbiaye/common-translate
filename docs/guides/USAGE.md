# Usage Guide - common-translate

## Table of Contents
1. [Basic Usage](#basic-usage)
2. [Excluding Fields from Translation](#excluding-fields-from-translation)
3. [Supported Response Types](#supported-response-types)
4. [Language Codes](#language-codes)
5. [Best Practices](#best-practices)
6. [Advanced Usage](#advanced-usage)

---

## Basic Usage

With `common-translate`, translation happens **automatically** without any code changes in your controllers.

### Example Controller

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable String id) {
        // No translation code needed!
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
```

### Client Request

**French Request:**
```bash
curl -H "Accept-Language: fr" http://localhost:8080/api/users/123
```
Response:
```json
{
  "id": "123",
  "firstName": "Salif",
  "lastName": "Biaye",
  "email": "salif@example.com",
  "typeUser": "CLIENT",
  "isActive": true
}
```

**English Request:**
```bash
curl -H "Accept-Language: en" http://localhost:8080/api/users/123
```
Response:
```json
{
  "id": "123",
  "firstName": "Salif",
  "lastName": "Biaye",
  "email": "salif@example.com",
  "typeUser": "Customer",
  "isActive": true
}
```

Notice:
- ✅ `firstName`, `lastName`, `email` are **NOT translated** (automatically excluded)
- ✅ `typeUser` enum is **translated** (CLIENT → Customer)

---

## Excluding Fields from Translation

### Using @NoTranslate Annotation

Mark fields that should never be translated:

```java
import com.crm_bancaire.common.translate.annotation.NoTranslate;

@Entity
public class User {
    @NoTranslate
    private String id;

    @NoTranslate
    private String firstName;

    @NoTranslate
    private String lastName;

    @NoTranslate
    private String email;

    private UserRole typeUser;  // Will be translated

    private String bio;  // Will be translated
}
```

### Automatic Exclusions

These are **automatically excluded** without annotation:

| Pattern | Example | Why |
|---------|---------|-----|
| Email addresses | `john@example.com` | Contains `@` and `.` |
| UUIDs | `550e8400-e29b-41d4-a716-446655440000` | UUID format |
| URLs | `https://example.com` | Starts with `http://` or `https://` |
| Pure numbers | `12345` | Only digits |
| ISO dates | `2024-10-25T10:30:00` | Date format |

### Fields Excluded by Name

These field names are **always excluded**:

```
id, uuid, email, username, password, token
firstName, lastName, fullName, keycloakId
createdAt, updatedAt, dateCreation, dateModification
telephone, phone, url, uri, sub, iss
```

---

## Supported Response Types

### Single Object

```java
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUser(@PathVariable String id) {
    return ResponseEntity.ok(userService.getUserById(id));
}
```

✅ All String fields are automatically translated

### List of Objects

```java
@GetMapping
public ResponseEntity<List<UserResponse>> getAllUsers() {
    return ResponseEntity.ok(userService.getAllUsers());
}
```

✅ Each object in the list is translated

### Paginated Responses

```java
@GetMapping("/paginated")
public ResponseEntity<Page<UserResponse>> getUsers(Pageable pageable) {
    return ResponseEntity.ok(userService.getAllUsers(pageable));
}
```

✅ Content of the page is translated, pagination metadata unchanged

### Plain String

```java
@PutMapping("/{id}")
public ResponseEntity<String> updateUser(@PathVariable String id, @RequestBody UserRequest req) {
    userService.updateUser(id, req);
    return ResponseEntity.ok("Utilisateur mis à jour avec succès");
}
```

✅ String is translated if language differs from source

---

## Language Codes

### Supported Languages

| Language | Code | Example |
|----------|------|---------|
| French | `fr` | `Accept-Language: fr` |
| English | `en` | `Accept-Language: en` |
| Spanish | `es` | `Accept-Language: es` |
| German | `de` | `Accept-Language: de` |
| Italian | `it` | `Accept-Language: it` |
| Portuguese | `pt` | `Accept-Language: pt` |

### Accept-Language Header Formats

All these formats are supported:

```bash
# Simple
Accept-Language: en

# With region
Accept-Language: en-US

# With quality values
Accept-Language: en-US,en;q=0.9,fr;q=0.8

# Multiple languages (first is used)
Accept-Language: es,en;q=0.7,fr;q=0.5
```

The module extracts the **first 2 characters** as the language code.

---

## Best Practices

### 1. Use @NoTranslate for Personal Data

Always mark personal identifiable information:

```java
@NoTranslate
private String firstName;

@NoTranslate
private String lastName;

@NoTranslate
private String email;

@NoTranslate
private String telephone;
```

### 2. Translate Business Content

Let business-related text be translated:

```java
private String description;  // Translate
private String comment;      // Translate
private String notes;        // Translate
private StatusEnum status;   // Translate (enums)
```

### 3. Use Clear Enum Names

For best translation quality, use descriptive enum values:

```java
public enum AccountStatus {
    ACTIVE,      // → "Actif", "Active", "Activo"
    SUSPENDED,   // → "Suspendu", "Suspended", "Suspendido"
    CLOSED       // → "Fermé", "Closed", "Cerrado"
}
```

### 4. Cache Warming

For frequently used translations, warm up the cache on startup:

```java
@Component
@RequiredArgsConstructor
public class TranslationCacheWarmer implements ApplicationRunner {
    private final AutoTranslationService translationService;

    @Override
    public void run(ApplicationArguments args) {
        // Pre-translate common enum values
        translationService.translate("CLIENT", "en");
        translationService.translate("ADMIN", "en");
        translationService.translate("ACTIVE", "en");

        // Repeat for other languages
        translationService.translate("CLIENT", "es");
        translationService.translate("ADMIN", "es");
    }
}
```

### 5. Error Messages

Keep error messages in your source language, they'll be translated automatically:

```java
if (user == null) {
    throw new ResourceNotFoundException("Utilisateur introuvable");
}
```

With `Accept-Language: en` → "User not found"

---

## Advanced Usage

### Manual Translation

If you need to manually translate text:

```java
@Service
@RequiredArgsConstructor
public class MyService {
    private final AutoTranslationService translationService;

    public String getGreeting(String language) {
        String greeting = "Bonjour";
        return translationService.translate(greeting, language);
    }
}
```

### Translate Complex Objects

```java
@Service
@RequiredArgsConstructor
public class MyService {
    private final AutoTranslationService translationService;

    public MyResponse getTranslatedResponse(String targetLang) {
        MyResponse response = buildResponse();
        return translationService.translateObject(response, targetLang);
    }
}
```

### Conditional Translation

Disable translation for specific endpoints:

```java
@GetMapping("/raw-data")
public ResponseEntity<UserResponse> getRawData(@PathVariable String id) {
    // Set Accept-Language to source language to bypass translation
    // Or handle in custom interceptor
    return ResponseEntity.ok(userService.getUserById(id));
}
```

### Custom Translation Configuration

Override configuration per environment:

```yaml
# application-dev.yml
translate:
  enabled: true
  libretranslate:
    url: http://localhost:5000

# application-prod.yml
translate:
  enabled: true
  libretranslate:
    url: http://libretranslate-prod:5000
  cache:
    ttl: 172800  # 48 hours for prod
```

---

## Frontend Integration

### JavaScript/TypeScript

```typescript
const fetchUser = async (userId: string, language: string) => {
  const response = await fetch(`/api/users/${userId}`, {
    headers: {
      'Accept-Language': language,
      'Content-Type': 'application/json'
    }
  });
  return response.json();
};

// Usage
const userFr = await fetchUser('123', 'fr');  // French response
const userEn = await fetchUser('123', 'en');  // English response
```

### React Example

```tsx
import { useState } from 'react';

function UserProfile({ userId }) {
  const [language, setLanguage] = useState('fr');
  const [user, setUser] = useState(null);

  useEffect(() => {
    fetch(`/api/users/${userId}`, {
      headers: { 'Accept-Language': language }
    })
      .then(res => res.json())
      .then(data => setUser(data));
  }, [userId, language]);

  return (
    <div>
      <select onChange={(e) => setLanguage(e.target.value)}>
        <option value="fr">Français</option>
        <option value="en">English</option>
        <option value="es">Español</option>
      </select>
      <p>Type: {user?.typeUser}</p>  {/* Automatically translated */}
    </div>
  );
}
```

---

## Performance Tips

1. **First request is slower** (~300ms): LibreTranslate translates
2. **Subsequent requests are fast** (~10ms): Redis cache hit
3. **Use cache warming** for common values
4. **Adjust TTL** based on your needs (longer = better performance, less up-to-date)

---

## Next Steps

- ⚙️ See [Configuration Options](CONFIGURATION.md)
- 📝 Check [Examples](../examples/)
- 🔧 Read [Troubleshooting](./TROUBLESHOOTING.md)
