// consultas de eventos define o acesso a agenda academica
package com.example.CampusLink.repository;

import com.example.CampusLink.model.Evento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// busca da agenda esta interface permite consultar eventos por turmas situacao e periodo
public interface EventoRepository extends JpaRepository<Evento, Long> {


    // buscar nome da turma do evento este metodo consulta o nome pelo identificador da turma
    @Query(value = """
            SELECT nome_turma
            FROM public."TURMAS"
            WHERE id = :idTurma
            """, nativeQuery = true)
    Optional<String> buscarNomeTurma(@Param("idTurma") Long idTurma);


    // buscar titulos do evento este metodo lista em ordem alfabetica os materiais associados
    @Query(value = """
            SELECT c.titulo
            FROM public."EVENTO_CONTEUDO" ec
            INNER JOIN public."CONTEUDOS" c
                ON c.id = ec.id_conteudo
            WHERE ec.id_evento = :idEvento
            ORDER BY c.titulo
            """, nativeQuery = true)
    List<String> buscarConteudosDoEvento(@Param("idEvento") Long idEvento);

    // listar eventos a partir da data este metodo busca eventos das turmas pela situacao em ordem crescente
    List<Evento> findByIdTurmaInAndStatusAndInicioGreaterThanEqualOrderByInicioAsc(
            Collection<Long> idsTurmas,
            String status,
            LocalDateTime inicio
    );

    // listar eventos anteriores a data este metodo busca eventos das turmas pela situacao em ordem decrescente
    List<Evento> findByIdTurmaInAndStatusAndInicioLessThanOrderByInicioDesc(
            Collection<Long> idsTurmas,
            String status,
            LocalDateTime inicio
    );
}