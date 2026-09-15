package com.example.CampusLink.repository;

import com.example.CampusLink.model.Disponibilidade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DisponibilidadeRepository
        extends JpaRepository<Disponibilidade, Long> {

    Optional<Disponibilidade> findByIdAlunoAndData(
            Long idAluno,
            LocalDate data
    );

    List<Disponibilidade> findAllByIdAlunoOrderByDataAsc(
            Long idAluno
    );
}