package com.example.userservice;

import com.crm_bancaire.common.translate.annotation.EnableAutoTranslate;
import com.crm_bancaire.common.translate.annotation.NoTranslate;
import com.crm_bancaire.common.translate.annotation.Translatable;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Complete example showing how to use common-translate in a User Service (v1.0.2+).
 *
 * Features demonstrated:
 * - @EnableAutoTranslate activation
 * - @NoTranslate on personal data fields
 * - @Translatable for field metadata exposure
 * - Automatic enum label generation ({fieldName}Label)
 * - Automatic translation of enums and descriptions
 * - Support for single objects, lists, and paginated responses
 * - Field metadata API for forms and tables
 * - Zero translation code in controllers
 */

// ============================================================================
// 1. MAIN APPLICATION CLASS
// ============================================================================

@SpringBootApplication
@EnableAutoTranslate  // ← Enable automatic translation
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}

// ============================================================================
// 2. ENTITY WITH @NoTranslate ANNOTATIONS
// ============================================================================

@Entity
@Table(name = "users")
@Translatable(name = "User", description = "User management entity with automatic field metadata")
@Data
class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @NoTranslate  // IDs should never be translated
    private String id;

    @NoTranslate  // Personal data - keep original
    private String firstName;

    @NoTranslate  // Personal data - keep original
    private String lastName;

    @NoTranslate  // Email addresses are auto-excluded, but annotation is explicit
    private String email;

    @NoTranslate  // Phone numbers should not be translated
    private String telephone;

    @Enumerated(EnumType.STRING)
    private UserRole role;  // ✅ WILL BE TRANSLATED (CLIENT → Customer)

    @Enumerated(EnumType.STRING)
    private UserStatus status;  // ✅ WILL BE TRANSLATED (ACTIF → Active)

    private String bio;  // ✅ WILL BE TRANSLATED (free text description)

    @NoTranslate  // Dates are auto-excluded, but annotation is explicit
    private LocalDateTime createdAt;

    private boolean isActive;
}

// ============================================================================
// 3. ENUMS (AUTOMATICALLY TRANSLATED)
// ============================================================================

enum UserRole {
    CLIENT,        // fr: "Client"      en: "Customer"    es: "Cliente"
    CONSEILLER,    // fr: "Conseiller"  en: "Advisor"     es: "Asesor"
    ADMIN,         // fr: "Admin"       en: "Admin"       es: "Admin"
    SUPER_ADMIN    // fr: "Super Admin" en: "Super Admin" es: "Super Admin"
}

enum UserStatus {
    ACTIF,      // fr: "Actif"      en: "Active"     es: "Activo"
    INACTIF,    // fr: "Inactif"    en: "Inactive"   es: "Inactivo"
    SUSPENDU,   // fr: "Suspendu"   en: "Suspended"  es: "Suspendido"
    BLOQUE      // fr: "Bloqué"     en: "Blocked"    es: "Bloqueado"
}

// ============================================================================
// 4. DTO / RESPONSE OBJECTS
// ============================================================================

@Data
class UserResponse {
    @NoTranslate
    private String id;

    @NoTranslate
    private String firstName;

    @NoTranslate
    private String lastName;

    @NoTranslate
    private String email;

    @NoTranslate
    private String telephone;

    private String role;       // Translated from UserRole enum
    private String status;     // Translated from UserStatus enum
    private String bio;        // Translated if present
    private boolean isActive;

    @NoTranslate
    private LocalDateTime createdAt;
}

// ============================================================================
// 5. CONTROLLER (NO TRANSLATION CODE!)
// ============================================================================

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
class UserController {
    private final UserService userService;

    /**
     * Get single user by ID
     * Response is automatically translated based on Accept-Language header
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable String id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Get all users
     * Each user in the list is automatically translated
     */
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * Get paginated users
     * Page content is automatically translated
     */
    @GetMapping("/paginated")
    public ResponseEntity<Page<UserResponse>> getUsersPaginated(Pageable pageable) {
        return ResponseEntity.ok(userService.getUsersPaginated(pageable));
    }

    /**
     * Create user
     * Success message is automatically translated
     */
    @PostMapping
    public ResponseEntity<String> createUser(@RequestBody UserRequest request) {
        userService.createUser(request);
        return ResponseEntity.ok("Utilisateur créé avec succès");
        // ↑ Automatically translated:
        // en: "User created successfully"
        // es: "Usuario creado con éxito"
    }

    /**
     * Update user
     * Response message is automatically translated
     */
    @PutMapping("/{id}")
    public ResponseEntity<String> updateUser(
            @PathVariable String id,
            @RequestBody UserRequest request) {
        boolean success = userService.updateUser(id, request);
        return success
                ? ResponseEntity.ok("Utilisateur mis à jour avec succès")
                : ResponseEntity.badRequest().body("Échec de la mise à jour");
        // ↑ Both messages automatically translated
    }

    /**
     * Delete user
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable String id) {
        boolean success = userService.deleteUser(id);
        return success
                ? ResponseEntity.ok("Utilisateur supprimé avec succès")
                : ResponseEntity.badRequest().body("Échec de la suppression");
    }
}

// ============================================================================
// 6. SERVICE LAYER (ALSO NO TRANSLATION CODE!)
// ============================================================================

@Service
@RequiredArgsConstructor
class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public Optional<UserResponse> getUserById(String id) {
        return userRepository.findById(id)
                .map(userMapper::toResponse);
        // Response will be automatically translated by the interceptor
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toResponse)
                .toList();
    }

    public Page<UserResponse> getUsersPaginated(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(userMapper::toResponse);
    }

    public void createUser(UserRequest request) {
        User user = userMapper.toEntity(request);
        userRepository.save(user);
    }

    public boolean updateUser(String id, UserRequest request) {
        return userRepository.findById(id)
                .map(user -> {
                    userMapper.updateEntity(user, request);
                    userRepository.save(user);
                    return true;
                })
                .orElse(false);
    }

    public boolean deleteUser(String id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return true;
        }
        return false;
    }
}

// ============================================================================
// 7. EXAMPLE REQUESTS & RESPONSES
// ============================================================================

/*

REQUEST (French):
─────────────────
curl -H "Accept-Language: fr" http://localhost:8080/api/users/123

RESPONSE:
{
  "id": "123",
  "firstName": "Salif",
  "lastName": "Biaye",
  "email": "salif@example.com",
  "telephone": "+221123456789",
  "role": "CLIENT",
  "status": "ACTIF",
  "bio": "Développeur passionné par la technologie",
  "isActive": true,
  "createdAt": "2024-10-25T10:30:00"
}

═══════════════════════════════════════════════════════════════════════════

REQUEST (English):
──────────────────
curl -H "Accept-Language: en" http://localhost:8080/api/users/123

RESPONSE:
{
  "id": "123",                          // ← Not translated
  "firstName": "Salif",                 // ← Not translated (@NoTranslate)
  "lastName": "Biaye",                  // ← Not translated (@NoTranslate)
  "email": "salif@example.com",         // ← Not translated (auto-excluded)
  "telephone": "+221123456789",         // ← Not translated (@NoTranslate)
  "role": "Customer",                   // ← TRANSLATED! (CLIENT → Customer)
  "status": "Active",                   // ← TRANSLATED! (ACTIF → Active)
  "bio": "Developer passionate about technology",  // ← TRANSLATED!
  "isActive": true,
  "createdAt": "2024-10-25T10:30:00"    // ← Not translated (auto-excluded)
}

═══════════════════════════════════════════════════════════════════════════

REQUEST (Spanish):
──────────────────
curl -H "Accept-Language: es" http://localhost:8080/api/users/123

RESPONSE:
{
  "id": "123",
  "firstName": "Salif",
  "lastName": "Biaye",
  "email": "salif@example.com",
  "telephone": "+221123456789",
  "role": "Cliente",                    // ← TRANSLATED! (CLIENT → Cliente)
  "status": "Activo",                   // ← TRANSLATED! (ACTIF → Activo)
  "bio": "Desarrollador apasionado por la tecnología",  // ← TRANSLATED!
  "isActive": true,
  "createdAt": "2024-10-25T10:30:00"
}

═══════════════════════════════════════════════════════════════════════════

CREATE USER REQUEST:
────────────────────
curl -X POST \
  -H "Accept-Language: en" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"John","lastName":"Doe","email":"john@example.com"}' \
  http://localhost:8080/api/users

RESPONSE:
"User created successfully"  // ← Translated from "Utilisateur créé avec succès"

═══════════════════════════════════════════════════════════════════════════

ENUM LABELS (v1.0.2+):
──────────────────────
curl -H "Accept-Language: en" http://localhost:8080/api/users/123

RESPONSE WITH AUTOMATIC ENUM LABELS:
{
  "id": "123",
  "firstName": "Salif",
  "lastName": "Biaye",
  "email": "salif@example.com",
  "telephone": "+221123456789",
  "role": "CLIENT",                   // ← Original enum value (for API logic)
  "roleLabel": "Customer",             // ← AUTO-GENERATED translated label (for UI display)
  "status": "ACTIF",                   // ← Original enum value
  "statusLabel": "Active",             // ← AUTO-GENERATED translated label
  "bio": "Developer passionate about technology",
  "isActive": true,
  "createdAt": "2024-10-25T10:30:00"
}

═══════════════════════════════════════════════════════════════════════════

FIELD METADATA API (v1.0.2+):
──────────────────────────────
GET /api/translate/metadata/User?lang=en

RESPONSE:
{
  "firstName": "First Name",
  "lastName": "Last Name",
  "email": "Email",
  "telephone": "Telephone",
  "role": "Role",
  "status": "Status",
  "bio": "Bio",
  "isActive": "Is Active",
  "createdAt": "Created At"
}

GET /api/translate/metadata/User?lang=fr

RESPONSE:
{
  "firstName": "Prénom",
  "lastName": "Nom",
  "email": "Email",
  "telephone": "Téléphone",
  "role": "Rôle",
  "status": "Statut",
  "bio": "Biographie",
  "isActive": "Est Actif",
  "createdAt": "Créé Le"
}

*/
