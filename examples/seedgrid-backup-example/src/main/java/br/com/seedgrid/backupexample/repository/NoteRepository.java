package br.com.seedgrid.backupexample.repository;

import br.com.seedgrid.backupexample.entity.Note;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NoteRepository implements PanacheRepository<Note> {
}
