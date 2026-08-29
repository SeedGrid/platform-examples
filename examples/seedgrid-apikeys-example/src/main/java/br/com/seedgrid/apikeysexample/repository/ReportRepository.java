package br.com.seedgrid.apikeysexample.repository;

import br.com.seedgrid.apikeysexample.entity.Report;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ReportRepository implements PanacheRepository<Report> {
}
