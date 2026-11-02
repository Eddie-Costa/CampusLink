package com.example.CampusLink.repository;

import com.example.CampusLink.model.Evento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    // busca o nome da turma
    @Query(value = """
            SELECT nome_turma
            FROM public."TURMAS"
            WHERE id = :idTurma
            """, nativeQuery = true)
    Optional<String> buscarNomeTurma(@Param("idTurma") Long idTurma);

    // busca os titulos dos conteudos ligados ao evento
    @Query(value = """
            SELECT c.titulo
            FROM public."EVENTO_CONTEUDO" ec
            INNER JOIN public."CONTEUDOS" c
                ON c.id = ec.id_conteudo
            WHERE ec.id_evento = :idEvento
            ORDER BY c.titulo
            """, nativeQuery = true)
    List<String> buscarConteudosDoEvento(@Param("idEvento") Long idEvento);

    List<Evento> findByIdTurmaInAndStatusAndInicioGreaterThanEqualOrderByInicioAsc(
            Collection<Long> idsTurmas,
            String status,
            LocalDateTime inicio
    );

    List<Evento> findByIdTurmaInAndStatusAndInicioLessThanOrderByInicioDesc(
            Collection<Long> idsTurmas,
            String status,
            LocalDateTime inicio
    );
}