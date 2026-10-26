# SeedGrid Audit Test

Este projeto existe para mostrar, de forma simples, como usar o módulo de auditoria da SeedGrid em uma aplicação Quarkus.

Se você quer entender:

- como auditar `CREATE`, `READ`, `UPDATE` e `DELETE`
- como mascarar ou esconder dados sensíveis no log de auditoria
- como configurar a persistência do audit
- como testar isso com uma API real

este README foi escrito para você.

## 1. O que este projeto demonstra

Aqui existe uma API de `customers` com CRUD completo.

Cada operação da API é auditada:

- listar clientes
- buscar um cliente
- criar um cliente
- atualizar um cliente
- excluir um cliente

Além disso, os campos sensíveis da entidade `Customer` recebem tratamento antes de ir para a auditoria:

- alguns são mascarados
- alguns são parcialmente escondidos
- alguns viram hash
- alguns são removidos do registro de auditoria

Ou seja: você consegue rastrear o que aconteceu sem vazar informação demais.

## 2. Como o audit funciona neste projeto

O audit está distribuído em 3 pontos principais:

1. Configuração da estratégia de persistência no `application.properties`
2. Marcação das operações no controller com `@SgAudit`
3. Marcação da entidade e dos campos sensíveis com `@SgAudit`, `@SgMaskRule` e `@SgPersonalData`

### 2.1 Estratégia de persistência

No arquivo [application.properties](C:/empreendedorismo/seedgrid/seedgrid-audit-example/src/main/resources/application.properties:1) a estratégia configurada é:

```properties
seedgrid.audit.persistence.strategy=${AUDIT_STRATEGY:JPA}
```

Isso significa:

- por padrão, o audit grava usando `JPA`
- se você quiser trocar em ambiente de execução, pode definir a variável `AUDIT_STRATEGY`

As opções mostradas no projeto são:

- `JPA`: auditoria persistida em banco relacional
- `MONGODB`: auditoria persistida no MongoDB

### 2.2 Auditoria das operações da API

No arquivo [CustomerController.java](C:/empreendedorismo/seedgrid/seedgrid-audit-example/src/main/java/br/com/seedgrid/auditexample/controllers/CustomerController.java:1), cada endpoint recebe a anotação `@SgAudit`.

Exemplo:

```java
@POST
@SgAudit(operation = AuditOperationEnum.CREATE, entityFqcn = CUSTOMER_FQCN, description = "Create customer")
public Customer create(Customer customer) {
    return customerService.create(customer);
}
```

Em português claro, isso quer dizer:

- esta operação deve ser auditada
- o tipo da ação é `CREATE`
- a entidade relacionada é `Customer`
- a descrição humana do evento será `"Create customer"`

O mesmo foi feito para:

- `READ` no listar
- `READ` no buscar por `publicId`
- `CREATE` no criar
- `UPDATE` no atualizar
- `DELETE` no excluir

### 2.3 Auditoria da entidade

No arquivo [Customer.java](C:/empreendedorismo/seedgrid/seedgrid-audit-example/src/main/java/br/com/seedgrid/auditexample/entities/Customer.java:1), a própria entidade também está anotada com `@SgAudit`.

Exemplo simplificado:

```java
@SgAudit(
    entityFqcn = "br.com.seedgrid.auditexample.entities.Customer",
    description = "Customer entity audit",
    options = {
        ...
    }
)
```

Aqui você está dizendo ao módulo:

- esta entidade pode participar do processo de auditoria
- o FQCN dela é esse
- existem regras extras de tratamento de campos

## 3. O que cada recurso do audit faz

Esta é a parte mais importante.

### 3.1 `@SgAudit` no controller

Use no endpoint quando você quiser registrar a ação executada pelo usuário.

Serve para responder perguntas como:

- quem criou um registro?
- quem alterou?
- quando isso aconteceu?
- qual operação foi feita?

Sem isso, a sua API funciona, mas o módulo de auditoria não sabe que aquela ação precisa ser registrada.

### 3.2 `operation = AuditOperationEnum.*`

É o tipo da operação auditada.

Neste projeto foram usados:

- `READ`
- `CREATE`
- `UPDATE`
- `DELETE`

Isso ajuda a classificar o evento corretamente no histórico.

### 3.3 `entityFqcn`

É o nome completo da classe Java da entidade.

Exemplo:

```java
br.com.seedgrid.auditexample.entities.Customer
```

O audit usa isso para saber exatamente qual entidade está envolvida.

### 3.4 `description`

É a descrição humana do evento.

Exemplos do projeto:

- `List customers`
- `Get customer by public id`
- `Create customer`
- `Update customer`
- `Delete customer`

Isso facilita muito a leitura posterior dos eventos.

### 3.5 `@SgAudit` na entidade

Quando usada na entidade, essa anotação ajuda a concentrar as regras de auditoria daquele objeto de negócio.

Pense assim:

- no controller você diz "audite esta ação"
- na entidade você diz "e quando auditar essa entidade, trate os campos deste jeito"

### 3.6 `@SgMaskRule`

É uma regra declarada no bloco `options` da anotação `@SgAudit` da entidade.

Ela serve para definir como cada campo deve ser tratado na auditoria.

Exemplo:

```java
@SgMaskRule(field = "cpf", type = MaskingStrategy.FORMAT, maskWith = "***.***.***-**")
```

Em linguagem simples:

- o campo é `cpf`
- o tipo da proteção é `FORMAT`
- o valor registrado no audit deve seguir a máscara `***.***.***-**`

### 3.7 `@SgPersonalData`

É a anotação colocada diretamente no atributo da entidade.

Exemplo:

```java
@SgPersonalData(type = MaskingStrategy.HASH)
public LocalDate birthDate;
```

Ela deixa explícito, no próprio campo, que aquele valor precisa de tratamento especial.

Na prática, ela é útil porque:

- melhora a leitura do código
- reduz a chance de alguém esquecer que o campo é sensível
- documenta a intenção no lugar mais óbvio possível

## 4. Tipos de proteção usados neste projeto

O projeto mostra vários tipos de proteção. Abaixo está a tradução “for dummies” de cada uma.

### 4.1 `FORMAT`

Mantém o formato visual da informação, mas esconde o valor real.

Exemplo com CPF:

- valor real: `123.456.789-00`
- audit: `***.***.***-**`

Use quando o formato importa, mas o conteúdo não pode aparecer.

### 4.2 `TEMPLATE`

Usa um modelo de máscara customizado.

Exemplo:

- nome real: `Mariana`
- máscara: `##**************`

Isso normalmente preserva só parte da informação e esconde o resto.

Use quando você quer mostrar “um pedaço” do valor sem expor tudo.

### 4.3 `FULL`

Troca o valor por um texto fixo.

Exemplo:

- ocupação real: `Engenheira de Dados`
- audit: `[OCCUPATION]`

Use quando você não quer mostrar nada do valor original, só quer deixar claro qual campo existia.

### 4.4 `PARTIAL`

Preserva apenas uma pequena parte do dado.

No projeto:

```java
@SgPersonalData(type = MaskingStrategy.PARTIAL, maskWith = "3")
```

Isso indica que apenas uma parte do conteúdo deve continuar visível, conforme a regra implementada pelo módulo.

Use quando o time precisa reconhecer o dado parcialmente, mas não pode ver o valor inteiro.

### 4.5 `HASH`

Transforma o valor em hash.

Exemplo de uso no projeto:

- `birthDate`
- `internalCode`

Use quando você precisa garantir que o valor original não apareça no audit, mas ainda quer permitir comparação técnica entre registros.

Em outras palavras:

- o dado original some
- sobra uma representação irreversível

### 4.6 `REMOVE`

Remove o campo da auditoria.

Exemplo no projeto:

- `address`
- `postalCode` em uma das regras declaradas

Use quando aquele dado não deve aparecer de jeito nenhum no evento auditado.

## 5. Como cada campo do `Customer` é tratado

Resumo prático da entidade [Customer.java](C:/empreendedorismo/seedgrid/seedgrid-audit-example/src/main/java/br/com/seedgrid/auditexample/entities/Customer.java:1):

| Campo | Regra | O que acontece no audit |
|---|---|---|
| `cpf` | `FORMAT` | CPF aparece mascarado |
| `name` | `TEMPLATE` | nome aparece parcialmente escondido |
| `occupation` | `FULL` | valor vira `[OCCUPATION]` |
| `job` | `PARTIAL` | só parte da informação fica visível |
| `birthDate` | `HASH` | data original não aparece |
| `address` | `REMOVE` | endereço é removido |
| `postalCode` | máscara fixa / remoção nas regras declaradas | não deve expor o valor real |
| `city` | `TEMPLATE` ou máscara fixa nas regras declaradas | cidade não aparece em texto puro |
| `comment` | `TEMPLATE` | comentário é mascarado |
| `internalCode` | `HASH` | código interno não aparece em texto puro |

Observação importante:

O projeto mostra regras tanto no nível do campo (`@SgPersonalData`) quanto no nível da entidade (`@SgMaskRule`). Isso é ótimo para demonstração, porque deixa claro que o módulo suporta mais de uma forma de declarar a proteção. Em projeto real, vale manter um padrão de equipe para não gerar dúvida.

## 6. Estrutura da tabela de negócio

A migration [V1000__create_customers_table.sql](C:/empreendedorismo/seedgrid/seedgrid-audit-example/src/main/resources/db/migration/app/V1000__create_customers_table.sql:1) cria a tabela `customers`.

Além dos campos do cliente, ela já inclui colunas úteis para rastreabilidade da entidade, como:

- `public_id`
- `created_at`
- `updated_at`
- `create_user_public_id`
- `create_user_email_snapshot`
- `create_user_whatsapp_snapshot`
- `update_user_public_id`
- `update_user_email_snapshot`
- `update_user_whatsapp_snapshot`

Traduzindo:

- você não rastreia só o evento de audit
- você também mantém metadados operacionais dentro do próprio registro

Isso ajuda em telas administrativas, suporte e investigação.

## 7. Como subir o projeto

### 7.1 Pré-requisitos

Você precisa ter:

- Java
- Maven
- PostgreSQL

### 7.2 Banco de dados

O projeto espera um PostgreSQL em:

```properties
jdbc:postgresql://localhost:5432/testeaudit
```

com:

```properties
username=postgres
password=postgres
```

Se quiser mudar isso, ajuste as variáveis:

- `SEEDGRID_DB_URL`
- `SEEDGRID_DB_USERNAME`
- `SEEDGRID_DB_PASSWORD`

### 7.3 Configurações importantes

No [application.properties](C:/empreendedorismo/seedgrid/seedgrid-audit-example/src/main/resources/application.properties:1), os pontos mais importantes para o audit são:

```properties
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db/migration/audit,classpath:db/migration/app
seedgrid.audit.persistence.strategy=${AUDIT_STRATEGY:JPA}
```

Isso significa:

- as migrations sobem automaticamente ao iniciar
- as migrations do audit e da aplicação são executadas
- a estratégia padrão do audit é JPA

### 7.4 Subindo em modo desenvolvimento

Use:

```bash
./mvnw quarkus:dev
```

ou, no Windows:

```powershell
.\mvnw.cmd quarkus:dev
```

Por padrão, a aplicação sobe na porta:

```text
http://localhost:8085
```

## 8. Como usar a API

Todos os endpoints estão em [CustomerController.java](C:/empreendedorismo/seedgrid/seedgrid-audit-example/src/main/java/br/com/seedgrid/auditexample/controllers/CustomerController.java:1).

### 8.1 Criar cliente

`POST /customers`

Exemplo de body:

```json
{
  "cpf": "123.456.789-00",
  "name": "Maria da Silva",
  "occupation": "Engenheira",
  "job": "Analista de Sistemas",
  "birthDate": "1990-05-10",
  "address": "Rua das Flores, 100",
  "postalCode": "01000-000",
  "city": "Sao Paulo",
  "comment": "Cliente VIP desde 2020",
  "internalCode": "CLI-INT-0001"
}
```

O que acontece:

- o cliente é persistido
- a operação `CREATE` é auditada
- os dados sensíveis são protegidos antes de serem gravados no histórico de audit

### 8.2 Listar clientes

`GET /customers`

O que acontece:

- a API devolve a lista de clientes
- a operação `READ` é auditada com descrição `List customers`

### 8.3 Buscar um cliente

`GET /customers/{publicId}`

O que acontece:

- a API busca o cliente pelo `publicId`
- a operação `READ` também fica registrada no audit

### 8.4 Atualizar cliente

`PUT /customers/{publicId}`

Body igual ao de criação.

O que acontece:

- o registro é alterado
- a operação `UPDATE` é auditada
- o módulo consegue registrar o antes/depois conforme a implementação da estratégia de audit

### 8.5 Excluir cliente

`DELETE /customers/{publicId}`

O que acontece:

- o registro é excluído
- a operação `DELETE` é auditada

## 9. Passo a passo de teste manual

Se você nunca testou audit, faça exatamente nesta ordem.

### Passo 1

Suba a aplicação.

### Passo 2

Crie um cliente com `POST /customers`.

### Passo 3

Liste os clientes com `GET /customers`.

### Passo 4

Pegue o `publicId` retornado e faça `GET /customers/{publicId}`.

### Passo 5

Atualize o cliente com `PUT /customers/{publicId}` mudando alguns campos.

### Passo 6

Exclua o cliente com `DELETE /customers/{publicId}`.

### Resultado esperado

Você deve ter eventos auditados para:

- criação
- leitura da lista
- leitura do item
- atualização
- exclusão

E os dados sensíveis não devem aparecer em texto puro no armazenamento de auditoria.

## 10. Como aplicar isso na sua própria entidade

Se você quiser repetir esse padrão em outra entidade, siga esta receita:

### 10.1 Anote a entidade

```java
@Entity
@SgAudit(
    entityFqcn = "com.suaempresa.suaapp.entities.SuaEntidade",
    description = "Audit da entidade SuaEntidade"
)
public class SuaEntidade {
}
```

### 10.2 Marque os campos sensíveis

```java
@SgPersonalData(type = MaskingStrategy.HASH)
public String documento;
```

ou:

```java
@SgPersonalData(type = MaskingStrategy.REMOVE)
public String observacaoInterna;
```

### 10.3 Audite os endpoints

```java
@POST
@SgAudit(operation = AuditOperationEnum.CREATE, entityFqcn = "com.suaempresa.suaapp.entities.SuaEntidade", description = "Create entity")
public SuaEntidade create(SuaEntidade input) {
    ...
}
```

Faça isso também para `READ`, `UPDATE` e `DELETE` quando fizer sentido.

### 10.4 Configure a persistência

No mínimo:

```properties
seedgrid.audit.persistence.strategy=JPA
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db/migration/audit,classpath:db/migration/app
```

## 11. Cuidados importantes

### 11.1 Multitenancy

O próprio `application.properties` já deixa um aviso importante:

- não configure `seedgrid.audit.jpa.*` em projeto multitenancy
- essa configuração é para cenário standalone

Se o seu projeto for multi-tenant, siga a estratégia suportada pelo módulo e não copie configuração de standalone sem entender.

### 11.2 Não use audit para vazar dados

O objetivo do audit é rastrear ações, não expor dados sensíveis.

Então:

- CPF deve ser mascarado
- datas ou códigos internos podem virar hash
- campos críticos podem ser removidos

Se você auditar tudo “cru”, você cria um histórico perigoso.

### 11.3 Mantenha consistência

Se a equipe escolher usar regras no campo com `@SgPersonalData`, mantenha isso como padrão.

Se preferir centralizar no `@SgAudit(options = {...})`, mantenha isso como convenção.

Misturar abordagens é bom para exemplo, mas em projeto real pode confundir.

## 12. Resumo rápido

Este projeto mostra um uso completo e fácil de entender do SeedGrid Audit:

- configuração por propriedade
- auditoria de CRUD no controller
- proteção de dados sensíveis na entidade
- uso de Flyway para subir esquema de app e audit
- suporte a estratégia `JPA` e exemplo comentado para `MONGODB`

Se você quiser aprender audit “vendo funcionar”, este projeto é um bom ponto de partida.
