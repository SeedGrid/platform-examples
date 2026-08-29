package br.com.seedgrid.privacyexample.repository;

import java.util.Optional;

import br.com.seedgrid.privacyexample.entity.Customer;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Busca por hash: as colunas {@code email_hash}/{@code cpf_hash} sao indexadas e guardam o hash
 * HMAC-SHA256 deterministico do valor normalizado — entao a busca por igualdade e' um lookup
 * indexado normal, sem precisar descriptografar nenhuma linha.
 */
@ApplicationScoped
public class CustomerRepository implements PanacheRepository<Customer> {

    public Optional<Customer> findByEmailHash(String emailHash) {
        return find("emailHash", emailHash).firstResultOptional();
    }

    public Optional<Customer> findByCpfHash(String cpfHash) {
        return find("cpfHash", cpfHash).firstResultOptional();
    }
}
