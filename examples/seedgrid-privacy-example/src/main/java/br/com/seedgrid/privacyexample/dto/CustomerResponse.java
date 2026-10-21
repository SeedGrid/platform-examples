package br.com.seedgrid.privacyexample.dto;

import br.com.seedgrid.privacy.masking.MaskingStrategy;
import br.com.seedgrid.privacy.masking.SgPersonalData;
import br.com.seedgrid.privacyexample.entity.Customer;

/**
 * Resposta da API. Campos publicos (sem getter) — o Jackson do Quarkus os detecta automaticamente,
 * e o {@code PersonalDataResolver} le a anotacao {@link SgPersonalData} direto do campo.
 *
 * <p>As anotacoes {@link SgPersonalData} descrevem COMO mascarar cada PII quando esta resposta passa
 * pelo {@code PersonalDataSanitizer} (ex.: a versao "/masked" do endpoint, ou uma trilha de auditoria).
 * O endpoint normal devolve o objeto SEM sanitizar — ou seja, o valor em claro (decifrado pelo JPA).</p>
 *
 * <p>Usa-se uma classe mutavel (nao record) porque {@code PersonalDataSanitizer.sanitize} faz o
 * round-trip JSON->objeto e todos os campos mascarados sao {@code String}.</p>
 */
public class CustomerResponse {

    public Long id;

    /** PARTIAL "4": revela apenas os ultimos 4 caracteres. Ex.: "Maria Silva" -> "*******ilva". */
    @SgPersonalData(type = MaskingStrategy.PARTIAL, maskWith = "4")
    public String name;

    /** FULL: substitui todo o valor por "***". */
    @SgPersonalData(type = MaskingStrategy.FULL)
    public String email;

    /** FORMAT: preserva o formato do CPF mas esconde os digitos -> "***.***.***-**". */
    @SgPersonalData(type = MaskingStrategy.FORMAT, maskWith = "***.***.***-**")
    public String cpf;

    /** PARTIAL "4": revela apenas os ultimos 4 digitos do telefone. */
    @SgPersonalData(type = MaskingStrategy.PARTIAL, maskWith = "4")
    public String phone;

    public CustomerResponse() {
    }

    public static CustomerResponse from(Customer customer) {
        CustomerResponse dto = new CustomerResponse();
        dto.id = customer.getId();
        dto.name = customer.getName();
        dto.email = customer.getEmail();
        dto.cpf = customer.getCpf();
        dto.phone = customer.getPhone();
        return dto;
    }
}
