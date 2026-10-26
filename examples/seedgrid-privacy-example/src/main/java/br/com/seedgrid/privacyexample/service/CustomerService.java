package br.com.seedgrid.privacyexample.service;

import java.util.Optional;

import br.com.seedgrid.privacy.CryptoProvider;
import br.com.seedgrid.privacy.EncryptedFieldSearch;
import br.com.seedgrid.privacy.SearchNormalization;
import br.com.seedgrid.privacy.masking.PersonalDataSanitizer;
import br.com.seedgrid.privacyexample.dto.CustomerRequest;
import br.com.seedgrid.privacyexample.dto.CustomerResponse;
import br.com.seedgrid.privacyexample.entity.Customer;
import br.com.seedgrid.privacyexample.repository.CustomerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Regras de negocio do exemplo. Mostra os tres usos da lib privacy:
 * <ol>
 *   <li><b>criar</b>: persiste PII — cifrada at-rest de forma transparente pelo converter, e o
 *       EncryptedFieldListener preenche os hashes de busca;</li>
 *   <li><b>buscar por hash</b>: recomputa o hash de busca do valor consultado (com a MESMA
 *       normalizacao usada na gravacao) e faz um lookup indexado, sem descriptografar a tabela;</li>
 *   <li><b>mascarar</b>: usa o {@link PersonalDataSanitizer} (bean da lib) para produzir uma copia
 *       com a PII mascarada, para log/auditoria/telas restritas.</li>
 * </ol>
 */
@ApplicationScoped
public class CustomerService {

    private final CustomerRepository repository;
    private final PersonalDataSanitizer sanitizer;

    public CustomerService(CustomerRepository repository, PersonalDataSanitizer sanitizer) {
        this.repository = repository;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public Customer create(CustomerRequest request) {
        Customer customer = new Customer();
        customer.setName(request.name());
        customer.setEmail(request.email());
        customer.setCpf(request.cpf());
        customer.setPhone(request.phone());
        repository.persist(customer);
        return customer;
    }

    public Optional<Customer> findById(Long id) {
        return Optional.ofNullable(repository.findById(id));
    }

    /** Busca por e-mail via hash deterministico (normalizacao EMAIL: trim + lowercase). */
    public Optional<Customer> findByEmail(String email) {
        EncryptedFieldSearch search = CryptoProvider.getSecureSearch();
        String hash = search.hashForSearch(email, SearchNormalization.EMAIL);
        return repository.findByEmailHash(hash);
    }

    /** Busca por CPF via hash deterministico (normalizacao CANONICAL: ignora pontuacao). */
    public Optional<Customer> findByCpf(String cpf) {
        EncryptedFieldSearch search = CryptoProvider.getSecureSearch();
        String hash = search.hashForSearch(cpf, SearchNormalization.CANONICAL);
        return repository.findByCpfHash(hash);
    }

    /**
     * Devolve uma copia da resposta com a PII mascarada conforme as anotacoes {@link
     * br.com.seedgrid.privacy.masking.SgPersonalData} do {@link CustomerResponse}. O objeto original
     * (e a entidade do banco) nao e' alterado.
     */
    public CustomerResponse mask(CustomerResponse response) {
        return sanitizer.sanitize(response, CustomerResponse.class);
    }
}
