package br.com.seedgrid.apikeysexample.service;

import br.com.seedgrid.apikeysexample.dto.ReportDTO;
import br.com.seedgrid.apikeysexample.entity.Report;
import br.com.seedgrid.apikeysexample.repository.ReportRepository;
import br.com.seedgrid.commons.core.exceptions.EntityNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class ReportService {

    @Inject
    ReportRepository reportRepository;

    public List<Report> listAll() {
        return reportRepository.listAll();
    }

    public Report findById(Long id) {
        return reportRepository.findByIdOptional(id)
                .orElseThrow(() -> new EntityNotFoundException("Report not found: " + id));
    }

    @Transactional
    public Report create(ReportDTO dto) {
        Report report = new Report();
        report.setTitle(dto.title());
        report.setContent(dto.content());
        reportRepository.persist(report);
        return report;
    }
}
