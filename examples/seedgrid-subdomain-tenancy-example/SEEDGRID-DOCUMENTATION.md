# SeedGrid Libraries - Documentação Completa

Esta documentação descreve todas as bibliotecas SeedGrid utilizadas no projeto, suas configurações, SPIs, templates e como configurar um projeto multitenancy do zero.

---

## Índice

1. [Bibliotecas/Extensions SeedGrid Utilizadas](#bibliotecas-extensions-seedgrid-utilizadas)
2. [Propriedades de Configuração](#propriedades-de-configuração)
3. [SPIs (Service Provider Interfaces)](#spis-service-provider-interfaces)
4. [Templates HTML](#templates-html)
5. [Anotações Customizadas](#anotações-customizadas)
6. [Configurando um Projeto Multitenancy do Zero](#configurando-um-projeto-multitenancy-do-zero)

---

## Bibliotecas/Extensions SeedGrid Utilizadas

### 1. seedgrid-subdomain-tenancy

**Descrição**: Biblioteca para suporte a multi-tenancy baseado em subdomínio e banco de dados separado por tenant.

**Funcionalidades principais**:
- Resolução de tenant por subdomínio (ex: `cliente1.kb.seedgrid.com.br`)
- Datasource master para dados compartilhados (tenants, planos, etc.)
- Datasource dinâmico por tenant
- Validação de tenant por código/link
- Notificações por email para confirmação de tenant
- Integração com OpenAPI/Swagger separando endpoints master/tenant

**Dependência Maven**:
```xml
<dependency>
    <groupId>br.com.seedgrid</groupId>
    <artifactId>seedgrid-subdomain-tenancy</artifactId>
    <version>${seedgrid.version}</version>
</dependency>
```

---

### 2. seedgrid-security

**Descrição**: Biblioteca completa de segurança com autenticação JWT, gerenciamento de usuários, controle de acesso e 2FA.

**Funcionalidades principais**:
- Autenticação JWT com chaves RSA
- Gerenciamento de usuários e roles
- Política de senha configurável
- Confirmação de email
- Recuperação de senha
- Auto-cadastro (self-enter) configurável
- Autenticação de dois fatores (2FA) - EMAIL e TOTP
- Criptografia de campos sensíveis
- Histórico de senhas

**Dependência Maven**:
```xml
<dependency>
    <groupId>br.com.seedgrid</groupId>
    <artifactId>seedgrid-security</artifactId>
    <version>${seedgrid.version}</version>
</dependency>
```

---

### 3. seedgrid-backup

**Descrição**: Sistema de backup e exportação de dados com suporte a múltiplos formatos e storages.

**Funcionalidades principais**:
- Exportação de dados em CSV/ZIP
- Suporte a múltiplos storages (filesystem, S3, Azure Blob, Google Drive, OneDrive, GCS)
- Exportação por tabela ou schema completo
- Sistema de filas para processamento assíncrono
- Notificações por email quando backup finaliza
- Controle de retenção de backups

**Dependência Maven**:
```xml
<dependency>
    <groupId>br.com.seedgrid</groupId>
    <artifactId>seedgrid-backup</artifactId>
    <version>${seedgrid.version}</version>
</dependency>
```

---

### 4. seedgrid-audit

**Descrição**: Sistema de auditoria que registra operações CRUD em banco de dados separado (MongoDB ou JPA).

**Funcionalidades principais**:
- Auditoria automática via interceptor
- Suporte a MongoDB ou JPA como storage
- Captura de dados antes/depois da operação
- Integração com contexto de tenant e usuário
- Anotação `@Audit` para marcar métodos auditáveis

**Dependência Maven**:
```xml
<dependency>
    <groupId>br.com.seedgrid</groupId>
    <artifactId>seedgrid-audit</artifactId>
    <version>${seedgrid.version}</version>
</dependency>
```

---

## Propriedades de Configuração

### seedgrid-subdomain-tenancy

#### Propriedades Gerais

```properties
# Prefixo do produto (usado para nomenclatura de databases)
seedgrid.subdomain-tenancy.product-prefix=kb

# Sufixo de domínio para detecção de tenant
seedgrid.subdomain-tenancy.domain-suffix=kb.seedgrid.com.br

# Rotas públicas que não exigem tenant
seedgrid.subdomain-tenancy.public-routes=/q/health,/q/metrics,/q/swagger-ui,/q/openapi,/public

# Tenant padrão quando não detectado (opcional)
seedgrid.subdomain-tenancy.default-tenant=default
```

#### Datasource Master

```properties
# URL do banco de dados master
seedgrid.subdomain-tenancy.master.datasource.jdbc.url=jdbc:postgresql://localhost:5439/kb_master

# Credenciais do master
seedgrid.subdomain-tenancy.master.datasource.username=postgres
seedgrid.subdomain-tenancy.master.datasource.password=postgres

# Pool de conexões master
seedgrid.subdomain-tenancy.master.datasource.jdbc.min-size=1
seedgrid.subdomain-tenancy.master.datasource.jdbc.max-size=10

# Pacotes de entidades do master
seedgrid.subdomain-tenancy.master.entity-packages=br.com.seedgrid.subdomaintenancyexample.entity.master

# Localização das migrations do master
seedgrid.subdomain-tenancy.master.migrations.location=classpath:db/migration/master
```

#### Datasource Tenant

```properties
# URL base do banco de dados dos tenants (o /{tenant} é adicionado automaticamente)
seedgrid.subdomain-tenancy.tenant.datasource.jdbc.url=jdbc:postgresql://localhost:5439

# Credenciais dos tenants
seedgrid.subdomain-tenancy.tenant.datasource.username=postgres
seedgrid.subdomain-tenancy.tenant.datasource.password=postgres

# Pool de conexões tenant
seedgrid.subdomain-tenancy.tenant.datasource.jdbc.min-size=1
seedgrid.subdomain-tenancy.tenant.datasource.jdbc.max-size=10

# Pacotes de entidades dos tenants
seedgrid.subdomain-tenancy.tenant.entity-packages=br.com.seedgrid.subdomaintenancyexample.entity.tenant

# Localização das migrations dos tenants
seedgrid.subdomain-tenancy.tenant.migrations.location=classpath:db/migration/tenant
```

#### Mailer (Multitenancy)

```properties
# Email remetente para notificações de tenant
seedgrid.subdomain-tenancy.mailer.from=noreply@example.com

# Configurações SMTP
seedgrid.subdomain-tenancy.mailer.host=smtp.gmail.com
seedgrid.subdomain-tenancy.mailer.port=587
seedgrid.subdomain-tenancy.mailer.start-tls=REQUIRED
seedgrid.subdomain-tenancy.mailer.username=seu-email@gmail.com
seedgrid.subdomain-tenancy.mailer.password=sua-senha
```

#### Database Master (auxiliar)

```properties
# Subdomínio do master (derivado do product-prefix)
database.master.subdomain=${PRODUCT_PREFIX}

# Nome do banco master (derivado do subdomain)
database.master.name=${database.master.subdomain}_master
```

---

### seedgrid-security

#### JWT Configuration

```properties
# Emissor do token JWT
seedgrid.security.jwt.issuer=https://seedgrid.com.br

# ID da chave (kid)
seedgrid.security.jwt.kid=seedgrid-key

# Tempo de vida do token (ISO-8601 Duration)
seedgrid.security.jwt.ttl=PT8H

# Localização das chaves RSA
seedgrid.security.jwt.private-key-location=src/main/resources/keys/private.pem
seedgrid.security.jwt.public-key-location=src/main/resources/keys/public.pem
```

#### Política de Senha

```properties
# Tamanho mínimo da senha
seedgrid.security.password.min-size=8

# Requer letra maiúscula
seedgrid.security.password.upper-case-required=true

# Requer letra minúscula
seedgrid.security.password.lower-case-required=true

# Requer caractere especial
seedgrid.security.password.special-character-required=true

# Proíbe caracteres repetidos em sequência
seedgrid.security.password.prohibits-repeated-characters-in-sequence=true

# Dias para expiração da senha
seedgrid.security.password.expiration-days=90

# Máximo de senhas no histórico
seedgrid.security.password.max-history=5

# Força troca de senha no primeiro login
seedgrid.security.password.force-change-on-first-login=true
```

#### Auto-cadastro (Self-enter)

```properties
# Habilita auto-cadastro
seedgrid.security.self-enter.enabled=true

# Role padrão para novos usuários
seedgrid.security.self-enter.default-role=USER
```

#### Autenticação de Dois Fatores (2FA)

```properties
# Habilita 2FA
seedgrid.security.2f.enabled=true

# Métodos permitidos (EMAIL, TOTP)
seedgrid.security.2f.allowed-methods=EMAIL,TOTP
```

#### Mailer (Security)

```properties
# Email remetente
seedgrid.security.mailer.from=noreply@example.com

# URL base da aplicação (para links em emails)
seedgrid.security.mailer.base-url=http://localhost:8080

# Configurações SMTP
seedgrid.security.mailer.host=smtp.gmail.com
seedgrid.security.mailer.port=587
seedgrid.security.mailer.start-tls=REQUIRED
seedgrid.security.mailer.username=seu-email@gmail.com
seedgrid.security.mailer.password=sua-senha
```

#### Criptografia

```properties
# Chave de criptografia (256 bits em hexadecimal)
seedgrid.security.encryption.key=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
```

---

### seedgrid-backup

#### Configuração Geral

```properties
# Dias de retenção de backups
seedgrid.backup.retention-days=7

# Máximo de exportações concorrentes
seedgrid.backup.max-concurrent-exports=1

# Intervalo de polling da fila
seedgrid.backup.queue-poll-interval=30s

# Storage utilizado (filesystem, s3, azure-blob, google-drive, onedrive, gcs)
seedgrid.backup.storage=filesystem
```

#### Notificações

```properties
# Habilita notificações
seedgrid.backup.notification.enabled=true

# Intervalo de polling para notificações
seedgrid.backup.notification.poll-interval=5m

# Máximo de tentativas de envio
seedgrid.backup.notification.max-retries=3

# Delay entre tentativas
seedgrid.backup.notification.retry-delay=PT15M
```

#### Mailer (Backup)

```properties
# Email remetente
seedgrid.backup.mailer.from=noreply@example.com

# Configurações SMTP
seedgrid.backup.mailer.host=smtp.gmail.com
seedgrid.backup.mailer.port=587
seedgrid.backup.mailer.start-tls=REQUIRED
seedgrid.backup.mailer.username=seu-email@gmail.com
seedgrid.backup.mailer.password=sua-senha
```

#### Storage - Filesystem

```properties
seedgrid.backup.filesystem.base-path=/var/seedgrid/files
```

#### Storage - S3

```properties
seedgrid.backup.s3.bucket=meu-bucket
seedgrid.backup.s3.region=us-east-1
seedgrid.backup.s3.access-key=ACCESS_KEY
seedgrid.backup.s3.secret-key=SECRET_KEY
seedgrid.backup.s3.endpoint=https://s3.amazonaws.com
```

#### Storage - Azure Blob

```properties
seedgrid.backup.azure-blob.connection-string=DefaultEndpointsProtocol=https;...
seedgrid.backup.azure-blob.container=backups
seedgrid.backup.azure-blob.account-name=myaccount
seedgrid.backup.azure-blob.account-key=KEY
```

#### Storage - Google Drive

```properties
seedgrid.backup.google-drive.root-folder-id=folder-id
seedgrid.backup.google-drive.credentials-file=/path/to/credentials.json
# OU
seedgrid.backup.google-drive.credentials-json={"type":"service_account",...}
```

#### Storage - OneDrive

```properties
seedgrid.backup.onedrive.client-id=client-id
seedgrid.backup.onedrive.client-secret=client-secret
seedgrid.backup.onedrive.tenant-id=tenant-id
seedgrid.backup.onedrive.root-folder-path=/seedgrid/files
```

#### Storage - Google Cloud Storage (GCS)

```properties
seedgrid.backup.gcs.bucket=meu-bucket
seedgrid.backup.gcs.project-id=meu-projeto
seedgrid.backup.gcs.credentials-file=/path/to/credentials.json
# OU
seedgrid.backup.gcs.credentials-json={"type":"service_account",...}
```

#### CSV Configuration

```properties
# Delimitador CSV
seedgrid.backup.csv.delimiter=,

# Caractere de aspas
seedgrid.backup.csv.quote-char="

# Encoding do arquivo
seedgrid.backup.csv.encoding=UTF-8
```

---

### seedgrid-audit

#### Configuração Geral

```properties
# Pacote base dos repositórios (para encontrar entidades antes de update/delete)
seedgrid.audit.repository.base-package=br.com.seedgrid.subdomaintenancyexample.repository

# Estratégia de persistência (MONGODB ou JPA)
seedgrid.audit.persistence.strategy=MONGODB
```

#### MongoDB

```properties
# String de conexão MongoDB
seedgrid.audit.mongodb.connection-string=mongodb://admin:admin@localhost:27017
```

#### JPA (Alternativa ao MongoDB)

```properties
# Estratégia JPA
seedgrid.audit.persistence.strategy=JPA

# Tipo de banco
seedgrid.audit.jpa.db-kind=postgresql

# Credenciais
seedgrid.audit.jpa.username=audit_user
seedgrid.audit.jpa.password=audit_pass

# URL JDBC
seedgrid.audit.jpa.jdbc.url=jdbc:postgresql://localhost:5432/audit_db
```

---

## SPIs (Service Provider Interfaces)

### seedgrid-subdomain-tenancy

#### 1. EmailTemplateProvider

**Interface**: `br.com.seedgrid.subdomaintenancy.spi.EmailTemplateProvider`

**Propósito**: Customizar HTML e assunto do email de confirmação de tenant.

**Default Bean**: ❌ Não possui

**Métodos**:
- `String buildConfirmationEmailHtml(String tenantName, String validationCode, String validationLink)`
- `String getConfirmationEmailSubject(String tenantName)`

**Exemplo de Implementação**:
```java
@ApplicationScoped
@Alternative
@Priority(1)
public class CustomEmailTemplateProvider implements EmailTemplateProvider {

    @Override
    public String buildConfirmationEmailHtml(String tenantName, String validationCode, String validationLink) {
        return """
            <html>
                <body>
                    <h1>Bem-vindo, %s!</h1>
                    <p>Seu código de validação: <strong>%s</strong></p>
                    <a href="%s">Clique aqui para validar</a>
                </body>
            </html>
            """.formatted(tenantName, validationCode, validationLink);
    }

    @Override
    public String getConfirmationEmailSubject(String tenantName) {
        return "Confirme seu cadastro - " + tenantName;
    }
}
```

---

#### 2. PasswordEncoder

**Interface**: `br.com.seedgrid.subdomaintenancy.spi.PasswordEncoder`

**Propósito**: Codificar senhas de tenant.

**Default Bean**: ✅ `BCryptPasswordEncoder` (BCrypt com 12 salt rounds)

**Métodos**:
- `String encode(String plainPassword)`

**Exemplo de Implementação Customizada**:
```java
@ApplicationScoped
@Alternative
@Priority(1)
public class ArgonPasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(String plainPassword) {
        // Implementação usando Argon2
        return Argon2Factory.create().hash(10, 65536, 1, plainPassword.toCharArray());
    }
}
```

---

#### 3. TemplateMultitenancyProvider

**Interface**: `br.com.seedgrid.subdomaintenancy.spi.TemplateMultitenancyProvider`

**Propósito**: Renderizar template de email de validação de tenant.

**Default Bean**: ✅ `DefaultTemplateMultitenancyProvider` (usa template Qute)

**Métodos**:
- `String buildValidationEmail(String name, String validationCode, String validationLink)`

**Template Padrão**: `templates/multitenancy-default/html/confirmation.html`

**Exemplo de Implementação**:
```java
@ApplicationScoped
@Alternative
@Priority(1)
public class CustomTemplateMultitenancyProvider implements TemplateMultitenancyProvider {

    @Override
    public String buildValidationEmail(String name, String validationCode, String validationLink) {
        return """
            <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto;">
                        <h2>Olá, %s!</h2>
                        <p>Use o código abaixo para validar seu tenant:</p>
                        <div style="background: #f0f0f0; padding: 20px; text-align: center;">
                            <h1>%s</h1>
                        </div>
                        <p>Ou clique no link: <a href="%s">Validar agora</a></p>
                    </div>
                </body>
            </html>
            """.formatted(name, validationCode, validationLink);
    }
}
```

---

#### 4. MultitenancyNotificationService

**Interface**: `br.com.seedgrid.subdomaintenancy.spi.MultitenancyNotificationService`

**Propósito**: Enviar notificações de validação de tenant (email, SMS, WhatsApp, etc.).

**Default Bean**: ✅ `MailerNotificationService` (envia via Quarkus Mailer)

**Métodos**:
- `void sendValidationNotification(TenantValidationNotification notification)`

**Exemplo de Implementação (SMS)**:
```java
@ApplicationScoped
@Alternative
@Priority(1)
public class SmsNotificationService implements MultitenancyNotificationService {

    @Inject
    SmsProvider smsProvider;

    @Override
    public void sendValidationNotification(TenantValidationNotification notification) {
        String message = "Seu código de validação: " + notification.validationCode();
        smsProvider.send(notification.recipient(), message);
    }
}
```

---

#### 5. MongoTenantResolver

**Interface**: `br.com.seedgrid.subdomaintenancy.resolvers.MongoTenantResolver`

**Propósito**: Resolver qual database MongoDB usar para o tenant atual.

**Default Bean**: ✅ `TenantAwareMongoProducer` (produtor interno)

**Métodos**:
- `String resolveDatabase(String databaseName)`
- `boolean isMultitenancyEnabled()`

**Nota**: Este é um SPI interno, geralmente não precisa ser customizado.

---

### seedgrid-security

#### 1. EmailServiceInterface

**Interface**: `br.com.seedgrid.security.spi.EmailServiceInterface`

**Propósito**: Customizar envio de emails de confirmação e recuperação de senha.

**Default Bean**: ✅ `DefaultEmailService`

**Métodos**:
- `void sendEmailConfirmation(String email, String name, String token)`
- `void sendPasswordReset(String email, String name, String token)`
- `String generateToken()`
- `void sendHtmlEmail(String to, String subject, String htmlBody)`

**Exemplo de Implementação**:
```java
@ApplicationScoped
@Alternative
@Priority(1)
public class CustomEmailService implements EmailServiceInterface {

    @Inject
    @MailerName("security")
    Mailer mailer;

    @Inject
    SecurityMailerConfig config;

    @Override
    public void sendEmailConfirmation(String email, String name, String token) {
        String link = config.baseUrl() + "/public/auth/confirm-email?token=" + token;
        String html = buildCustomConfirmationEmail(name, link);
        sendHtmlEmail(email, "Confirme seu email", html);
    }

    @Override
    public void sendPasswordReset(String email, String name, String token) {
        String link = config.baseUrl() + "/public/auth/reset-password?token=" + token;
        String html = buildCustomPasswordResetEmail(name, link);
        sendHtmlEmail(email, "Recuperação de senha", html);
    }

    @Override
    public String generateToken() {
        return UUID.randomUUID().toString();
    }

    @Override
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        mailer.send(Mail.withHtml(to, subject, htmlBody).setFrom(config.from()));
    }

    private String buildCustomConfirmationEmail(String name, String link) {
        // Seu HTML customizado
        return "...";
    }

    private String buildCustomPasswordResetEmail(String name, String link) {
        // Seu HTML customizado
        return "...";
    }
}
```

---

#### 2. JwtTokenCustomizer

**Interface**: `br.com.seedgrid.security.spi.JwtTokenCustomizer`

**Propósito**: Adicionar claims customizados ao token JWT.

**Default Bean**: ✅ `DefaultJwtTokenCustomizer` (retorna HashMap vazio)

**Métodos**:
- `Map<String, Object> customizeClaims(User user, List<String> roles, String hostHeader)`

**Exemplo de Implementação**:
```java
@ApplicationScoped
@Alternative
@Priority(1)
public class CustomJwtTokenCustomizer implements JwtTokenCustomizer {

    @Override
    public Map<String, Object> customizeClaims(User user, List<String> roles, String hostHeader) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("department", user.getDepartment());
        claims.put("employee_id", user.getEmployeeId());
        claims.put("permissions", calculatePermissions(roles));
        return claims;
    }

    private List<String> calculatePermissions(List<String> roles) {
        // Lógica para calcular permissões baseadas nas roles
        return List.of("READ", "WRITE");
    }
}
```

---

#### 3. JwtTokenValidator

**Interface**: `br.com.seedgrid.security.spi.JwtTokenValidator`

**Propósito**: Validar claims customizados do token JWT.

**Default Bean**: ✅ `DefaultJwtTokenValidator` (não faz validação adicional)

**Métodos**:
- `void validateCustomClaims(JWTClaimsSet claims, String hostHeader) throws Exception`

**Exemplo de Implementação**:
```java
@ApplicationScoped
@Alternative
@Priority(1)
public class CustomJwtTokenValidator implements JwtTokenValidator {

    @Override
    public void validateCustomClaims(JWTClaimsSet claims, String hostHeader) throws Exception {
        // Validar que o department existe
        Object department = claims.getClaim("department");
        if (department == null || department.toString().isEmpty()) {
            throw new SecurityException("Department claim is required");
        }

        // Validar que o token não foi emitido para outro host
        String tokenHost = (String) claims.getClaim("host");
        if (tokenHost != null && !tokenHost.equals(hostHeader)) {
            throw new SecurityException("Token was issued for a different host");
        }
    }
}
```

---

#### 4. TemplateSecurityProvider

**Interface**: `br.com.seedgrid.security.spi.TemplateSecurityProvider`

**Propósito**: Prover templates HTML para emails de segurança.

**Default Bean**: ✅ `DefaultTemplateSecurityProvider`

**Métodos**:
- `String emailConfirmation(String name, String confirmationLink)`
- `String passwordReset(String name, String resetLink)`
- `String entryRequestConfirmation(String name, String confirmationLink)`
- `String entryRequestApproved(String name, String email, String loginLink)`

**Templates Padrão**:
- `templates/security-default/html/email-confirmation.html`
- `templates/security-default/html/password-reset.html`
- `templates/security-default/html/entry-request-confirmation.html`
- `templates/security-default/html/entry-request-approved.html`

**Exemplo de Implementação**:
```java
@ApplicationScoped
@Alternative
@Priority(1)
public class CustomTemplateSecurityProvider implements TemplateSecurityProvider {

    @Override
    public String emailConfirmation(String name, String confirmationLink) {
        return """
            <html>
                <body>
                    <h1>Olá, %s!</h1>
                    <p>Confirme seu email clicando no botão abaixo:</p>
                    <a href="%s" style="background: blue; color: white; padding: 10px;">
                        Confirmar Email
                    </a>
                </body>
            </html>
            """.formatted(name, confirmationLink);
    }

    @Override
    public String passwordReset(String name, String resetLink) {
        return """
            <html>
                <body>
                    <h1>Olá, %s!</h1>
                    <p>Clique abaixo para redefinir sua senha:</p>
                    <a href="%s" style="background: red; color: white; padding: 10px;">
                        Redefinir Senha
                    </a>
                </body>
            </html>
            """.formatted(name, resetLink);
    }

    @Override
    public String entryRequestConfirmation(String name, String confirmationLink) {
        // Implementação customizada
        return "...";
    }

    @Override
    public String entryRequestApproved(String name, String email, String loginLink) {
        // Implementação customizada
        return "...";
    }
}
```

---

#### 5. TwoFactorStrategy

**Interface**: `br.com.seedgrid.security.spi.TwoFactorStrategy`

**Propósito**: Implementar diferentes estratégias de 2FA (EMAIL, TOTP, SMS, etc.).

**Default Beans**:
- ✅ `EmailTwoFactorStrategy` (método: EMAIL)
- ✅ `TotpTwoFactorStrategy` (método: TOTP)

**Métodos**:
- `String getMethodCode()`
- `boolean canInitiate(User user)`
- `void initiate(User user)`
- `boolean verify(User user, String code)`

**Exemplo de Implementação (SMS)**:
```java
@ApplicationScoped
public class SmsTwoFactorStrategy implements TwoFactorStrategy {

    @Inject
    SmsProvider smsProvider;

    @Inject
    TwoFactorCodeRepository codeRepo;

    @Override
    public String getMethodCode() {
        return "SMS";
    }

    @Override
    public boolean canInitiate(User user) {
        return user.getPhoneNumber() != null && !user.getPhoneNumber().isEmpty();
    }

    @Override
    public void initiate(User user) {
        String code = generateRandomCode();
        codeRepo.save(user, code, 5); // expira em 5 minutos
        smsProvider.send(user.getPhoneNumber(), "Seu código: " + code);
    }

    @Override
    public boolean verify(User user, String code) {
        return codeRepo.verify(user, code);
    }

    private String generateRandomCode() {
        return String.format("%06d", new Random().nextInt(999999));
    }
}
```

---

#### 6. TwoFactorEmailTemplateProvider

**Interface**: `br.com.seedgrid.security.spi.TwoFactorEmailTemplateProvider`

**Propósito**: Customizar template de email para código 2FA.

**Default Bean**: ✅ `DefaultTwoFactorEmailTemplateProvider`

**Métodos**:
- `EmailTemplate buildCodeEmail(User user, String code, int expiresInMinutes)`

**Inner Class**: `EmailTemplate` (contém `subject`, `htmlBody`, `textBody`)

**Exemplo de Implementação**:
```java
@ApplicationScoped
@Alternative
@Priority(1)
public class CustomTwoFactorEmailTemplateProvider implements TwoFactorEmailTemplateProvider {

    @Override
    public EmailTemplate buildCodeEmail(User user, String code, int expiresInMinutes) {
        String subject = "Seu código de verificação";

        String html = """
            <html>
                <body style="font-family: Arial, sans-serif;">
                    <h2>Olá, %s!</h2>
                    <p>Seu código de verificação em duas etapas:</p>
                    <div style="background: #007bff; color: white; padding: 20px; text-align: center;">
                        <h1 style="margin: 0; font-size: 36px;">%s</h1>
                    </div>
                    <p>Este código expira em %d minutos.</p>
                </body>
            </html>
            """.formatted(user.getName(), code, expiresInMinutes);

        String text = "Olá, " + user.getName() + "! Seu código: " + code;

        return new EmailTemplate(subject, html, text);
    }
}
```

---

#### 7. UserAdditionalFieldsValidator

**Interface**: `br.com.seedgrid.security.spi.UserAdditionalFieldsValidator`

**Propósito**: Validar os campos próprios da aplicação guardados em `User.additionalAttributes`
(coluna `users.additional_attributes`, `jsonb`). Desde o security `2026.09.25` o `User` é concreto: não
existe mais subclasse de usuário nem `UserFactoryInterface`.

**Default Bean**: ✅ `DefaultUserAdditionalFieldsValidator` (sem regras)

**Métodos**:
- `void validate(User user, Map<String, JsonNode> additionalAttributes)` — recebe o mapa final (no
  `PATCH`, já mesclado); lance `InvalidUserAdditionalAttributesException` para rejeitar (HTTP 400).

**Exemplo de Implementação**:
```java
@ApplicationScoped
public class DepartamentoObrigatorioValidator implements UserAdditionalFieldsValidator {

    @Override
    public void validate(User user, Map<String, JsonNode> additionalAttributes) {
        if (additionalAttributes == null || !additionalAttributes.containsKey("departamento")) {
            throw new InvalidUserAdditionalAttributesException("departamento e obrigatorio");
        }
    }
}
```

---

### seedgrid-backup

#### 1. BackupExporter

**Interface**: `br.com.seedgrid.backup.spi.BackupExporter`

**Propósito**: Implementar diferentes formatos de exportação (CSV, SQL, Parquet, etc.).

**Default Bean**: ✅ `CsvZipExporter` (formato: CSV_ZIP)

**Métodos**:
- `ExportFormat format()`
- `void export(ExportContext context) throws Exception`
- `void validate(ExportContext context) throws IllegalArgumentException`

**Exemplo de Implementação (SQL)**:
```java
@ApplicationScoped
public class SqlExporter implements BackupExporter {

    @Override
    public ExportFormat format() {
        return ExportFormat.SQL;
    }

    @Override
    public void validate(ExportContext context) throws IllegalArgumentException {
        if (context.scope() == ExportScope.TABLE && context.options().get("tableName") == null) {
            throw new IllegalArgumentException("tableName is required for TABLE scope");
        }
    }

    @Override
    public void export(ExportContext context) throws Exception {
        String tenant = context.tenantPublicId();
        ExportScope scope = context.scope();

        if (scope == ExportScope.FULL) {
            exportFullDatabase(context);
        } else {
            String tableName = (String) context.options().get("tableName");
            exportTable(context, tableName);
        }
    }

    private void exportFullDatabase(ExportContext context) throws Exception {
        // Implementação de dump completo do banco
    }

    private void exportTable(ExportContext context, String tableName) throws Exception {
        // Implementação de dump de uma tabela
    }
}
```

---

#### 2. ExportNotificationService

**Interface**: `br.com.seedgrid.backup.spi.ExportNotificationService`

**Propósito**: Notificar usuários quando o backup estiver pronto.

**Default Bean**: ✅ `EmailExportNotificationService`

**Métodos**:
- `void notifyReady(ExportJob job)`
- `boolean isEnabled()`

**Exemplo de Implementação (Webhook)**:
```java
@ApplicationScoped
@Alternative
@Priority(1)
public class WebhookExportNotificationService implements ExportNotificationService {

    @ConfigProperty(name = "backup.webhook.url")
    Optional<String> webhookUrl;

    @Inject
    RestClient restClient;

    @Override
    public boolean isEnabled() {
        return webhookUrl.isPresent();
    }

    @Override
    public void notifyReady(ExportJob job) {
        if (!isEnabled()) return;

        Map<String, Object> payload = Map.of(
            "jobId", job.getPublicId(),
            "status", "READY",
            "format", job.getFormat(),
            "sizeBytes", job.getSizeBytes(),
            "finishedAt", job.getFinishedAt()
        );

        restClient.post(webhookUrl.get(), payload);
    }
}
```

---

#### 3. TemplateBackupProvider

**Interface**: `br.com.seedgrid.backup.spi.TemplateBackupProvider`

**Propósito**: Prover template HTML para notificação de backup pronto.

**Default Bean**: ✅ `DefaultTemplateBackupProvider`

**Métodos**:
- `String readyNotification(ExportJob job)`

**Template Padrão**: `templates/backup-default/html/ready-notification.html`

**Exemplo de Implementação**:
```java
@ApplicationScoped
@Alternative
@Priority(1)
public class CustomTemplateBackupProvider implements TemplateBackupProvider {

    @Override
    public String readyNotification(ExportJob job) {
        return """
            <html>
                <body>
                    <h1>Seu backup está pronto!</h1>
                    <table>
                        <tr><td>Job ID:</td><td>%s</td></tr>
                        <tr><td>Formato:</td><td>%s</td></tr>
                        <tr><td>Tamanho:</td><td>%d bytes</td></tr>
                        <tr><td>Concluído em:</td><td>%s</td></tr>
                    </table>
                    <p>Acesse o painel para fazer o download.</p>
                </body>
            </html>
            """.formatted(
                job.getPublicId(),
                job.getFormat(),
                job.getSizeBytes(),
                job.getFinishedAt()
            );
    }
}
```

---

#### 4. TenantProvider

**Interface**: `br.com.seedgrid.backup.spi.TenantProvider`

**Propósito**: Prover contexto de tenant para backup em ambientes multitenancy.

**Default Bean**: ✅ `SingleTenantBackupProvider` (retorna "default")

**Métodos**:
- `Optional<String> getCurrentTenant()`
- `void setCurrentTenant(String tenant)`
- `void clearCurrentTenant()`
- `List<String> getAllTenants()`

**Exemplo de Implementação (Multitenancy)**:
```java
@ApplicationScoped
@Alternative
@Priority(1)
public class MultitenantBackupProvider implements TenantProvider {

    @Inject
    TenantService tenantService;

    @Override
    public Optional<String> getCurrentTenant() {
        return tenantService.getCurrentTenant();
    }

    @Override
    public void setCurrentTenant(String tenant) {
        tenantService.setTenant(tenant);
    }

    @Override
    public void clearCurrentTenant() {
        tenantService.clearTenant();
    }

    @Override
    public List<String> getAllTenants() {
        return tenantService.listAllActiveTenants();
    }
}
```

---

## Templates HTML

### seedgrid-subdomain-tenancy

#### 1. confirmation.html

**Localização**: `runtime/src/main/resources/templates/multitenancy-default/html/confirmation.html`

**Usado por**: `DefaultTemplateMultitenancyProvider`

**Variáveis**:
- `{name}` - Nome do tenant
- `{validationCode}` - Código de validação
- `{validationLink}` - Link de validação

**Como criar seu próprio template**:

1. Crie um arquivo HTML no seu projeto:
```
src/main/resources/templates/custom/tenant-confirmation.html
```

2. Use as variáveis com chaves `{variable}`:
```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Confirmação de Tenant</title>
</head>
<body>
    <h1>Bem-vindo, {name}!</h1>
    <p>Use o código: <strong>{validationCode}</strong></p>
    <p>Ou clique: <a href="{validationLink}">Validar</a></p>
</body>
</html>
```

3. Implemente o SPI `TemplateMultitenancyProvider`:
```java
@ApplicationScoped
@Alternative
@Priority(1)
public class CustomTemplateProvider implements TemplateMultitenancyProvider {

    @Override
    public String buildValidationEmail(String name, String validationCode, String validationLink) {
        String template = loadTemplate("templates/custom/tenant-confirmation.html");
        return template
            .replace("{name}", name)
            .replace("{validationCode}", validationCode)
            .replace("{validationLink}", validationLink);
    }

    private String loadTemplate(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load template: " + path, e);
        }
    }
}
```

---

### seedgrid-security

#### 1. email-confirmation.html

**Localização**: `runtime/src/main/resources/templates/security-default/html/email-confirmation.html`

**Usado por**: `DefaultTemplateSecurityProvider.emailConfirmation()`

**Variáveis**:
- `{name}` - Nome do usuário
- `{confirmationLink}` - Link de confirmação

---

#### 2. password-reset.html

**Localização**: `runtime/src/main/resources/templates/security-default/html/password-reset.html`

**Usado por**: `DefaultTemplateSecurityProvider.passwordReset()`

**Variáveis**:
- `{name}` - Nome do usuário
- `{resetLink}` - Link para redefinir senha

---

#### 3. entry-request-confirmation.html

**Localização**: `runtime/src/main/resources/templates/security-default/html/entry-request-confirmation.html`

**Usado por**: `DefaultTemplateSecurityProvider.entryRequestConfirmation()`

**Variáveis**:
- `{name}` - Nome do usuário
- `{confirmationLink}` - Link de confirmação

---

#### 4. entry-request-approved.html

**Localização**: `runtime/src/main/resources/templates/security-default/html/entry-request-approved.html`

**Usado por**: `DefaultTemplateSecurityProvider.entryRequestApproved()`

**Variáveis**:
- `{name}` - Nome do usuário
- `{email}` - Email do usuário
- `{loginLink}` - Link para fazer login

---

**Como criar seus próprios templates**:

1. Crie seus arquivos HTML:
```
src/main/resources/templates/custom-security/email-confirmation.html
src/main/resources/templates/custom-security/password-reset.html
src/main/resources/templates/custom-security/entry-request-confirmation.html
src/main/resources/templates/custom-security/entry-request-approved.html
```

2. Implemente o SPI `TemplateSecurityProvider`:
```java
@ApplicationScoped
@Alternative
@Priority(1)
public class CustomSecurityTemplateProvider implements TemplateSecurityProvider {

    @Override
    public String emailConfirmation(String name, String confirmationLink) {
        String template = loadTemplate("templates/custom-security/email-confirmation.html");
        return template
            .replace("{name}", name)
            .replace("{confirmationLink}", confirmationLink);
    }

    @Override
    public String passwordReset(String name, String resetLink) {
        String template = loadTemplate("templates/custom-security/password-reset.html");
        return template
            .replace("{name}", name)
            .replace("{resetLink}", resetLink);
    }

    @Override
    public String entryRequestConfirmation(String name, String confirmationLink) {
        String template = loadTemplate("templates/custom-security/entry-request-confirmation.html");
        return template
            .replace("{name}", name)
            .replace("{confirmationLink}", confirmationLink);
    }

    @Override
    public String entryRequestApproved(String name, String email, String loginLink) {
        String template = loadTemplate("templates/custom-security/entry-request-approved.html");
        return template
            .replace("{name}", name)
            .replace("{email}", email)
            .replace("{loginLink}", loginLink);
    }

    private String loadTemplate(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load template: " + path, e);
        }
    }
}
```

---

### seedgrid-backup

#### 1. ready-notification.html

**Localização**: `runtime/src/main/resources/templates/backup-default/html/ready-notification.html`

**Usado por**: `DefaultTemplateBackupProvider`

**Variáveis**:
- `{{jobId}}` - ID do job
- `{{format}}` - Formato do backup
- `{{scope}}` - Escopo (FULL ou TABLE)
- `{{sizeBytes}}` - Tamanho em bytes
- `{{finishedAt}}` - Data/hora de conclusão

**Como criar seu próprio template**:

1. Crie o arquivo HTML:
```
src/main/resources/templates/custom-backup/ready-notification.html
```

2. Use as variáveis com chaves duplas `{{variable}}`:
```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Backup Pronto</title>
</head>
<body>
    <h1>Seu backup está pronto!</h1>
    <ul>
        <li>Job: {{jobId}}</li>
        <li>Formato: {{format}}</li>
        <li>Escopo: {{scope}}</li>
        <li>Tamanho: {{sizeBytes}} bytes</li>
        <li>Concluído: {{finishedAt}}</li>
    </ul>
</body>
</html>
```

3. Implemente o SPI `TemplateBackupProvider`:
```java
@ApplicationScoped
@Alternative
@Priority(1)
public class CustomBackupTemplateProvider implements TemplateBackupProvider {

    @Override
    public String readyNotification(ExportJob job) {
        String template = loadTemplate("templates/custom-backup/ready-notification.html");
        return template
            .replace("{{jobId}}", job.getPublicId())
            .replace("{{format}}", job.getFormat().name())
            .replace("{{scope}}", job.getScope().name())
            .replace("{{sizeBytes}}", String.valueOf(job.getSizeBytes()))
            .replace("{{finishedAt}}", job.getFinishedAt().toString());
    }

    private String loadTemplate(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load template: " + path, e);
        }
    }
}
```

---

## Anotações Customizadas

### seedgrid-subdomain-tenancy

#### @MasterOnly

**Localização**: `br.com.seedgrid.subdomaintenancy.annotations.MasterOnly`

**Propósito**: Marcar endpoints que devem aparecer apenas no OpenAPI/Swagger do domínio master.

**Uso**:
```java
@Path("/api/tenants")
@MasterOnly
public class TenantResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Tenant> listAll() {
        // Este endpoint só aparece no Swagger do master
        return tenantService.listAll();
    }
}
```

**Ou em métodos individuais**:
```java
@Path("/api/users")
public class UserResource {

    @GET
    @Path("/admin/stats")
    @MasterOnly
    public UserStats getAdminStats() {
        // Só aparece no master
        return statsService.getGlobalStats();
    }

    @GET
    public List<User> listUsers() {
        // Aparece em ambos (master e tenant)
        return userService.listAll();
    }
}
```

---

#### @TenantOnly

**Localização**: `br.com.seedgrid.subdomaintenancy.annotations.TenantOnly`

**Propósito**: Marcar endpoints que devem aparecer apenas no OpenAPI/Swagger de tenants.

**Uso**:
```java
@Path("/api/invoices")
@TenantOnly
public class InvoiceResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Invoice> listInvoices() {
        // Este endpoint só aparece no Swagger dos tenants
        return invoiceService.listForCurrentTenant();
    }
}
```

**Combinando com @MasterOnly**:
```java
@Path("/api/reports")
public class ReportResource {

    @GET
    @Path("/tenant-sales")
    @TenantOnly
    public SalesReport getTenantSales() {
        // Só no tenant
        return reportService.getTenantSales();
    }

    @GET
    @Path("/global-sales")
    @MasterOnly
    public SalesReport getGlobalSales() {
        // Só no master
        return reportService.getGlobalSales();
    }

    @GET
    @Path("/my-reports")
    public List<Report> getMyReports() {
        // Aparece em ambos
        return reportService.getReportsForCurrentUser();
    }
}
```

---

### seedgrid-audit

#### @Audit

**Localização**: `br.com.seedgrid.audit.annotations.Audit`

**Propósito**: Marcar métodos ou classes para auditoria automática.

**Parâmetros**:
- `operation`: Tipo da operação (`AuditOperationEnum`: CREATE, UPDATE, DELETE, READ, OTHER)
- `description`: Descrição customizada da operação (opcional)
- `entity`: Nome da entidade sendo auditada (opcional)

**Uso em classe**:
```java
@ApplicationScoped
@Audit(operation = AuditOperationEnum.OTHER)
public class ProductService {

    public void createProduct(Product product) {
        // Será auditado como OTHER
        productRepository.persist(product);
    }
}
```

**Uso em método**:
```java
@ApplicationScoped
public class ProductService {

    @Audit(operation = AuditOperationEnum.CREATE, entity = "Product", description = "Criação de novo produto")
    public void createProduct(Product product) {
        productRepository.persist(product);
    }

    @Audit(operation = AuditOperationEnum.UPDATE, entity = "Product")
    public void updateProduct(Product product) {
        productRepository.merge(product);
    }

    @Audit(operation = AuditOperationEnum.DELETE, entity = "Product")
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }
}
```

**Sem auditoria**:
```java
@ApplicationScoped
public class ProductService {

    public List<Product> listProducts() {
        // Método não auditado
        return productRepository.listAll();
    }
}
```

---

## Configurando um Projeto Multitenancy do Zero

### Passo 1: Adicionar Dependências

Adicione as dependências SeedGrid no `pom.xml`:

```xml
<properties>
    <seedgrid.version>1.0.0</seedgrid.version>
</properties>

<dependencies>
    <!-- SeedGrid Multitenancy -->
    <dependency>
        <groupId>br.com.seedgrid</groupId>
        <artifactId>seedgrid-subdomain-tenancy</artifactId>
        <version>${seedgrid.version}</version>
    </dependency>

    <!-- SeedGrid Security -->
    <dependency>
        <groupId>br.com.seedgrid</groupId>
        <artifactId>seedgrid-security</artifactId>
        <version>${seedgrid.version}</version>
    </dependency>

    <!-- SeedGrid Backup (opcional) -->
    <dependency>
        <groupId>br.com.seedgrid</groupId>
        <artifactId>seedgrid-backup</artifactId>
        <version>${seedgrid.version}</version>
    </dependency>

    <!-- SeedGrid Audit (opcional) -->
    <dependency>
        <groupId>br.com.seedgrid</groupId>
        <artifactId>seedgrid-audit</artifactId>
        <version>${seedgrid.version}</version>
    </dependency>
</dependencies>
```

---

### Passo 2: Configurar application.properties

Crie o arquivo `src/main/resources/application.properties`:

```properties
# ========================================
# APLICAÇÃO GERAL
# ========================================
quarkus.application.name=meu-app
quarkus.http.port=8080

# ========================================
# SEEDGRID MULTITENANCY
# ========================================
seedgrid.subdomain-tenancy.product-prefix=meuapp
seedgrid.subdomain-tenancy.domain-suffix=meuapp.com.br
seedgrid.subdomain-tenancy.public-routes=/q/health,/q/metrics,/q/swagger-ui,/q/openapi,/public

# DATASOURCE MASTER
seedgrid.subdomain-tenancy.master.datasource.jdbc.url=${MASTER_DB_URL:jdbc:postgresql://localhost:5432/${database.master.name}}
seedgrid.subdomain-tenancy.master.datasource.username=${MASTER_DB_USERNAME:postgres}
seedgrid.subdomain-tenancy.master.datasource.password=${MASTER_DB_PASSWORD:postgres}
seedgrid.subdomain-tenancy.master.datasource.jdbc.min-size=1
seedgrid.subdomain-tenancy.master.datasource.jdbc.max-size=10
seedgrid.subdomain-tenancy.master.entity-packages=com.meuapp.entity.master

# DATASOURCE TENANT
seedgrid.subdomain-tenancy.tenant.datasource.jdbc.url=${TENANT_DB_URL:jdbc:postgresql://localhost:5432}
seedgrid.subdomain-tenancy.tenant.datasource.username=${TENANT_DB_USERNAME:postgres}
seedgrid.subdomain-tenancy.tenant.datasource.password=${TENANT_DB_PASSWORD:postgres}
seedgrid.subdomain-tenancy.tenant.datasource.jdbc.min-size=1
seedgrid.subdomain-tenancy.tenant.datasource.jdbc.max-size=10
seedgrid.subdomain-tenancy.tenant.entity-packages=com.meuapp.entity.tenant

# MAILER MULTITENANCY
seedgrid.subdomain-tenancy.mailer.from=noreply@meuapp.com.br
seedgrid.subdomain-tenancy.mailer.host=smtp.gmail.com
seedgrid.subdomain-tenancy.mailer.port=587
seedgrid.subdomain-tenancy.mailer.start-tls=REQUIRED
seedgrid.subdomain-tenancy.mailer.username=${SMTP_USERNAME}
seedgrid.subdomain-tenancy.mailer.password=${SMTP_PASSWORD}

# ========================================
# SEEDGRID SECURITY
# ========================================
seedgrid.security.jwt.issuer=https://meuapp.com.br
seedgrid.security.jwt.kid=meuapp-key
seedgrid.security.jwt.ttl=PT8H
seedgrid.security.jwt.private-key-location=src/main/resources/keys/private.pem
seedgrid.security.jwt.public-key-location=src/main/resources/keys/public.pem

seedgrid.security.password.min-size=8
seedgrid.security.password.upper-case-required=true
seedgrid.security.password.lower-case-required=true
seedgrid.security.password.special-character-required=true
seedgrid.security.password.expiration-days=90

seedgrid.security.mailer.from=noreply@meuapp.com.br
seedgrid.security.mailer.base-url=http://localhost:8080
seedgrid.security.mailer.host=smtp.gmail.com
seedgrid.security.mailer.port=587
seedgrid.security.mailer.start-tls=REQUIRED
seedgrid.security.mailer.username=${SMTP_USERNAME}
seedgrid.security.mailer.password=${SMTP_PASSWORD}

seedgrid.security.encryption.key=${ENCRYPTION_KEY}

# ========================================
# SEEDGRID AUDIT
# ========================================
seedgrid.audit.repository.base-package=com.meuapp.repository
seedgrid.audit.persistence.strategy=MONGODB
seedgrid.audit.mongodb.connection-string=${MONGODB_URI:mongodb://admin:admin@localhost:27017}

# ========================================
# SEEDGRID BACKUP
# ========================================
seedgrid.backup.retention-days=7
seedgrid.backup.storage=filesystem
seedgrid.backup.filesystem.base-path=/var/meuapp/backups
seedgrid.backup.mailer.from=noreply@meuapp.com.br

# ========================================
# DATABASE MASTER (auxiliar)
# ========================================
database.master.subdomain=${PRODUCT_PREFIX:meuapp}
database.master.name=${database.master.subdomain}_master
```

---

### Passo 3: Criar Estrutura de Entidades

#### Entidades Master

Localização: `src/main/java/com/meuapp/entity/master/`

**Tenant.java** (já fornecido pela lib):
```java
// Fornecido por seedgrid-subdomain-tenancy
import br.com.seedgrid.subdomaintenancy.entities.Tenant;
```

**PaymentPlan.java** (exemplo customizado):
```java
package com.meuapp.entity.master;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "payment_plans")
public class PaymentPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private BigDecimal price;
    private Integer maxUsers;
    private Integer maxStorage;

    // getters e setters
}
```

---

#### Entidades Tenant

Localização: `src/main/java/com/meuapp/entity/tenant/`

**Product.java**:
```java
package com.meuapp.entity.tenant;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private BigDecimal price;

    // getters e setters
}
```

**Customer.java**:
```java
package com.meuapp.entity.tenant;

import jakarta.persistence.*;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String phone;

    // getters e setters
}
```

---

### Passo 4: Criar Repositórios

#### Repositório Master

**IMPORTANTE**: Deve ter anotação `@PersistenceUnit("master")`

```java
package com.meuapp.repository.master;

import com.meuapp.entity.master.PaymentPlan;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@PersistenceUnit("master")  // ← OBRIGATÓRIO para repositórios master
public class PaymentPlanRepository implements PanacheRepository<PaymentPlan> {

    public PaymentPlan findByName(String name) {
        return find("name", name).firstResult();
    }
}
```

**TenantRepository** (fornecido pela lib):
```java
import br.com.seedgrid.subdomaintenancy.repositories.TenantRepository;
import jakarta.inject.Inject;

@ApplicationScoped
public class MyService {

    @Inject
    TenantRepository tenantRepository;  // Já tem @PersistenceUnit("master")

    public void doSomething() {
        List<Tenant> tenants = tenantRepository.listAll();
    }
}
```

---

#### Repositório Tenant

**IMPORTANTE**: NÃO deve ter anotação `@PersistenceUnit` (usa o datasource padrão/tenant)

```java
package com.meuapp.repository.tenant;

import com.meuapp.entity.tenant.Product;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
// ← SEM @PersistenceUnit (usa datasource padrão que é o tenant)
public class ProductRepository implements PanacheRepository<Product> {

    public List<Product> findByPriceRange(BigDecimal min, BigDecimal max) {
        return find("price >= ?1 and price <= ?2", min, max).list();
    }
}
```

```java
package com.meuapp.repository.tenant;

import com.meuapp.entity.tenant.Customer;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CustomerRepository implements PanacheRepository<Customer> {

    public Customer findByEmail(String email) {
        return find("email", email).firstResult();
    }
}
```

---

### Passo 5: Criar Migrations

#### Migrations Master

Localização: `src/main/resources/db/migration/master/`

**V1__create_payment_plans.sql**:
```sql
CREATE TABLE payment_plans (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    max_users INTEGER,
    max_storage INTEGER
);

INSERT INTO payment_plans (name, price, max_users, max_storage)
VALUES ('Basic', 29.90, 5, 10),
       ('Pro', 99.90, 20, 100),
       ('Enterprise', 299.90, 100, 1000);
```

**Nota**: As migrations de `Tenant`, `User`, `Role`, etc. já são fornecidas pelas bibliotecas SeedGrid.

---

#### Migrations Tenant

Localização: `src/main/resources/db/migration/tenant/`

**V1__create_products.sql**:
```sql
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL
);
```

**V2__create_customers.sql**:
```sql
CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    phone VARCHAR(20)
);
```

---

### Passo 6: Criar Resources (Controllers)

#### Resource Master

```java
package com.meuapp.resource.master;

import br.com.seedgrid.subdomaintenancy.annotations.MasterOnly;
import com.meuapp.entity.master.PaymentPlan;
import com.meuapp.repository.master.PaymentPlanRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/payment-plans")
@MasterOnly  // ← Aparece só no Swagger do master
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentPlanResource {

    @Inject
    PaymentPlanRepository planRepository;

    @GET
    public List<PaymentPlan> listAll() {
        return planRepository.listAll();
    }

    @GET
    @Path("/{id}")
    public PaymentPlan getById(@PathParam("id") Long id) {
        return planRepository.findById(id);
    }

    @POST
    public PaymentPlan create(PaymentPlan plan) {
        planRepository.persist(plan);
        return plan;
    }
}
```

---

#### Resource Tenant

```java
package com.meuapp.resource.tenant;

import br.com.seedgrid.subdomaintenancy.annotations.TenantOnly;
import com.meuapp.entity.tenant.Product;
import com.meuapp.repository.tenant.ProductRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/products")
@TenantOnly  // ← Aparece só no Swagger dos tenants
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductResource {

    @Inject
    ProductRepository productRepository;

    @GET
    public List<Product> listAll() {
        return productRepository.listAll();
    }

    @GET
    @Path("/{id}")
    public Product getById(@PathParam("id") Long id) {
        return productRepository.findById(id);
    }

    @POST
    public Product create(Product product) {
        productRepository.persist(product);
        return product;
    }

    @PUT
    @Path("/{id}")
    public Product update(@PathParam("id") Long id, Product product) {
        Product existing = productRepository.findById(id);
        existing.setName(product.getName());
        existing.setDescription(product.getDescription());
        existing.setPrice(product.getPrice());
        productRepository.persist(existing);
        return existing;
    }

    @DELETE
    @Path("/{id}")
    public void delete(@PathParam("id") Long id) {
        productRepository.deleteById(id);
    }
}
```

---

### Passo 7: Gerar Chaves RSA para JWT

Execute os comandos:

```bash
# Criar diretório
mkdir -p src/main/resources/keys

# Gerar chave privada
openssl genrsa -out src/main/resources/keys/private.pem 2048

# Gerar chave pública
openssl rsa -in src/main/resources/keys/private.pem -pubout -out src/main/resources/keys/public.pem
```

---

### Passo 8: Criar Arquivo .env

Crie o arquivo `.env` na raiz do projeto:

```bash
# ========================================
# APLICAÇÃO
# ========================================
APP_NAME=meu-app
HTTP_PORT=8080

# ========================================
# MULTITENANCY
# ========================================
PRODUCT_PREFIX=meuapp
DOMAIN_SUFFIX=meuapp.com.br

# ========================================
# DATABASE MASTER
# ========================================
MASTER_DB_URL=jdbc:postgresql://localhost:5432/meuapp_master
MASTER_DB_USERNAME=postgres
MASTER_DB_PASSWORD=postgres

# ========================================
# DATABASE TENANT
# ========================================
TENANT_DB_URL=jdbc:postgresql://localhost:5432
TENANT_DB_USERNAME=postgres
TENANT_DB_PASSWORD=postgres

# ========================================
# SMTP
# ========================================
SMTP_USERNAME=seu-email@gmail.com
SMTP_PASSWORD=sua-senha-app

# ========================================
# SECURITY
# ========================================
ENCRYPTION_KEY=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef

# ========================================
# AUDIT
# ========================================
MONGODB_URI=mongodb://admin:admin@localhost:27017
```

---

### Passo 9: Executar a Aplicação

```bash
# Modo desenvolvimento
./mvnw quarkus:dev

# Ou com Docker Compose (se tiver)
docker-compose up -d postgres mongodb
./mvnw quarkus:dev
```

---

### Passo 10: Testar Multitenancy

#### 1. Criar um Tenant via API Master

**Request** (POST para domínio master: `http://meuapp.com.br:8080/api/tenants`):
```json
{
  "subdomain": "cliente1",
  "companyName": "Cliente 1 Ltda",
  "email": "contato@cliente1.com",
  "paymentPlan": {
    "id": 1
  }
}
```

**Response**:
```json
{
  "publicId": "uuid-do-tenant",
  "subdomain": "cliente1",
  "companyName": "Cliente 1 Ltda",
  "email": "contato@cliente1.com",
  "status": "PENDING_VALIDATION",
  "validationCode": "ABC123"
}
```

#### 2. Validar o Tenant

**Request** (POST para `http://meuapp.com.br:8080/api/tenants/validate`):
```json
{
  "code": "ABC123"
}
```

**Response**:
```json
{
  "message": "Tenant validated successfully",
  "tenant": {
    "publicId": "uuid-do-tenant",
    "subdomain": "cliente1",
    "status": "ACTIVE"
  }
}
```

#### 3. Acessar via Subdomínio Tenant

**Request** (GET para `http://cliente1.meuapp.com.br:8080/api/products`):
```json
[
  {
    "id": 1,
    "name": "Produto A",
    "price": 100.00
  }
]
```

---

### Passo 11: Anotações Importantes

#### Resumo de Anotações Obrigatórias

| Contexto | Anotação | Obrigatória? | Descrição |
|----------|----------|--------------|-----------|
| Repositório Master | `@PersistenceUnit("master")` | ✅ SIM | Usa datasource master |
| Repositório Tenant | Nenhuma | ✅ NÃO usar | Usa datasource padrão (tenant) |
| Resource Master | `@MasterOnly` | ❌ Opcional | Para OpenAPI/Swagger |
| Resource Tenant | `@TenantOnly` | ❌ Opcional | Para OpenAPI/Swagger |
| Método Auditável | `@Audit(...)` | ❌ Opcional | Para auditoria automática |

---

## Resumo de SPIs e Defaults

### seedgrid-subdomain-tenancy

| SPI | Default Bean | Tem Default? |
|-----|--------------|--------------|
| `EmailTemplateProvider` | - | ❌ NÃO |
| `PasswordEncoder` | `BCryptPasswordEncoder` | ✅ SIM |
| `TemplateMultitenancyProvider` | `DefaultTemplateMultitenancyProvider` | ✅ SIM |
| `MultitenancyNotificationService` | `MailerNotificationService` | ✅ SIM |
| `MongoTenantResolver` | `TenantAwareMongoProducer` | ✅ SIM |

---

### seedgrid-security

| SPI | Default Bean | Tem Default? |
|-----|--------------|--------------|
| `EmailServiceInterface` | `DefaultEmailService` | ✅ SIM |
| `JwtTokenCustomizer` | `DefaultJwtTokenCustomizer` | ✅ SIM |
| `JwtTokenValidator` | `DefaultJwtTokenValidator` | ✅ SIM |
| `TemplateSecurityProvider` | `DefaultTemplateSecurityProvider` | ✅ SIM |
| `TwoFactorStrategy` (EMAIL) | `EmailTwoFactorStrategy` | ✅ SIM |
| `TwoFactorStrategy` (TOTP) | `TotpTwoFactorStrategy` | ✅ SIM |
| `TwoFactorEmailTemplateProvider` | `DefaultTwoFactorEmailTemplateProvider` | ✅ SIM |
| `UserAdditionalFieldsValidator` | `DefaultUserAdditionalFieldsValidator` | ✅ SIM |

---

### seedgrid-backup

| SPI | Default Bean | Tem Default? |
|-----|--------------|--------------|
| `BackupExporter` (CSV_ZIP) | `CsvZipExporter` | ✅ SIM |
| `ExportNotificationService` | `EmailExportNotificationService` | ✅ SIM |
| `TemplateBackupProvider` | `DefaultTemplateBackupProvider` | ✅ SIM |
| `TenantProvider` | `SingleTenantBackupProvider` | ✅ SIM |

---

### seedgrid-audit

| SPI | Default Bean | Tem Default? |
|-----|--------------|--------------|
| Nenhum | - | - |

---

## Referências

- **Código-fonte**: `c:\empreendedorismo\seedgrid\multitenant\`
- **Projeto exemplo**: `c:\empreendedorismo\seedgrid\multitenant\seedgrid-test-quarkus\`
- **Documentação Quarkus**: https://quarkus.io/guides/
- **MicroProfile Config**: https://github.com/eclipse/microprofile-config

---

**Versão**: 1.0.0
**Data**: 2026-01-04
**Autor**: SeedGrid Team
