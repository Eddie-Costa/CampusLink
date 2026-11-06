// consultas de disponibilidade define o acesso aos dias livres dos alunos
package com.example.CampusLink.repository;

import com.example.CampusLink.model.Disponibilidade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// busca de horas livres esta interface permite consultar a disponibilidade por aluno data ou grupo
public interface DisponibilidadeRepository
        extends JpaRepository<Disponibilidade, Long> {

    // buscar disponibilidade do dia este metodo consulta as horas livres de um aluno na data informada
    Optional<Disponibilidade> findByIdAlunoAndData(
            Long idAluno,
            LocalDate data
    );

    // listar disponibilidade do aluno este metodo consulta os dias livres em ordem de data
    List<Disponibilidade> findAllByIdAlunoOrderByDataAsc(
            Long idAluno
    );

    // listar disponibilidade de varios alunos este metodo consulta as horas livres dos participantes informados
    List<Disponibilidade> findAllByIdAlunoIn(
            Collection<Long> idsAlunos
    );
}