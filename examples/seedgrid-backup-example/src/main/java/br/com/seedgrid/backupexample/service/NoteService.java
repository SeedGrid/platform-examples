package br.com.seedgrid.backupexample.service;

import br.com.seedgrid.backupexample.dto.NoteDTO;
import br.com.seedgrid.backupexample.entity.Note;
import br.com.seedgrid.backupexample.repository.NoteRepository;
import br.com.seedgrid.commons.core.exceptions.EntityNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class NoteService {

    @Inject
    NoteRepository noteRepository;

    public List<Note> listAll() {
        return noteRepository.listAll();
    }

    public Note findById(Long id) {
        return noteRepository.findByIdOptional(id)
                .orElseThrow(() -> new EntityNotFoundException("Note not found: " + id));
    }

    @Transactional
    public Note create(NoteDTO dto) {
        Note note = new Note();
        note.setTitle(dto.title());
        note.setContent(dto.content());
        noteRepository.persist(note);
        return note;
    }
}
