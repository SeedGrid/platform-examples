package br.com.seedgrid.backupexample.entity;

import br.com.seedgrid.commons.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Anotacao pessoal do usuario logado — o dado de dominio que este exemplo faz o usuario exportar
 * via seedgrid-backup. O dono real (createUserPublicId) vem de graca do BaseEntity/commons
 * (BaseEntityAuditListener via CurrentUserProvider), sem nenhum codigo extra aqui.
 */
@Entity
@Table(name = "notes")
@Getter
@Setter
@NoArgsConstructor
public class Note extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String content;
}
