package br.com.seedgrid.privacyexample.entity;

import br.com.seedgrid.privacy.EncryptedField;
import br.com.seedgrid.privacy.EncryptedFieldAttributeConverter;
import br.com.seedgrid.privacy.EncryptedFieldListener;
import br.com.seedgrid.privacy.SearchNormalization;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cliente com PII (LGPD) cifrada at-rest. Cada campo sensivel combina DUAS coisas da lib privacy:
 *
 * <ul>
 *   <li>{@code @Convert(EncryptedFieldAttributeConverter.class)} — cifra/decifra o valor de forma
 *       transparente na ida/volta ao banco (AES-256-GCM). No banco fica {@code v1:iv:ciphertext}.</li>
 *   <li>{@code @EncryptedField(searchField=..., normalize=...)} — instrui o
 *       {@link EncryptedFieldListener} (registrado no nivel da entidade via {@code @EntityListeners})
 *       a preencher, no {@code @PrePersist}/{@code @PreUpdate}, o campo IRMAO de hash de busca
 *       (HMAC-SHA256 deterministico) — permitindo busca por igualdade sem descriptografar a tabela.</li>
 * </ul>
 *
 * <p>{@code name} e {@code phone} sao cifrados mas NAO pesquisaveis: usam {@code @EncryptedField}
 * SEM {@code searchField}, entao o listener nao gera hash nenhum (comportamento desde 2026.07.08).
 * {@code email} e {@code cpf} sao cifrados E pesquisaveis por hash. A normalizacao do hash difere:
 * {@link SearchNormalization#EMAIL} para e-mail (trim+lowercase) e {@link SearchNormalization#CANONICAL}
 * para CPF (ignora pontuacao), para que a busca ache o registro independente de formatacao.</p>
 */
@Entity
@Table(name = "customers")
@EntityListeners(EncryptedFieldListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Cifrado at-rest, mas NAO pesquisavel: @EncryptedField sem searchField nao gera hash
    // nenhum (comportamento desde 2026.07.08) — nao precisa de campo-irmao "<campo>Hash".
    // Quem cifra continua sendo o @Convert.
    @Convert(converter = EncryptedFieldAttributeConverter.class)
    @EncryptedField
    @Column(nullable = false, length = 512)
    private String name;

    @Convert(converter = EncryptedFieldAttributeConverter.class)
    @EncryptedField(searchField = "emailHash", normalize = SearchNormalization.EMAIL)
    @Column(nullable = false, length = 512)
    private String email;

    /** Campo IRMAO de busca preenchido pelo EncryptedFieldListener. Nao cifrado — e' o hash HMAC. */
    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Convert(converter = EncryptedFieldAttributeConverter.class)
    @EncryptedField(searchField = "cpfHash", normalize = SearchNormalization.CANONICAL)
    @Column(nullable = false, length = 512)
    private String cpf;

    /** Campo IRMAO de busca preenchido pelo EncryptedFieldListener. Nao cifrado — e' o hash HMAC. */
    @Column(name = "cpf_hash", length = 64)
    private String cpfHash;

    // Cifrado at-rest, mas NAO pesquisavel: sem searchField nenhum hash e' gerado (ver 'name').
    @Convert(converter = EncryptedFieldAttributeConverter.class)
    @EncryptedField
    @Column(length = 512)
    private String phone;
}
