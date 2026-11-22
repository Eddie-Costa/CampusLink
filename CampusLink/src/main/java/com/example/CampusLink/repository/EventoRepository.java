// consultas de eventos define o acesso a agenda academica
package com.example.CampusLink.repository;

import com.example.CampusLink.model.Evento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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


    // buscar nomes de varias turmas este metodo devolve codigo e nome de todas as turmas pedidas em uma unica consulta
    @Query(value = """
            SELECT id, nome_turma
            FROM public."TURMAS"
            WHERE id IN (:idsTurmas)
            """, nativeQuery = true)
    List<Object[]> buscarNomesTurmas(@Param("idsTurmas") Collection<Long> idsTurmas);


    // buscar titulos de varios eventos este metodo devolve evento e titulo em ordem alfabetica em uma unica consulta
    @Query(value = """
            SELECT ec.id_evento, c.titulo
            FROM public."EVENTO_CONTEUDO" ec
            INNER JOIN public."CONTEUDOS" c
                ON c.id = ec.id_conteudo
            WHERE ec.id_evento IN (:idsEventos)
            ORDER BY ec.id_evento, c.titulo
            """, nativeQuery = true)
    List<Object[]> buscarConteudosDosEventos(@Param("idsEventos") Collection<Long> idsEventos);

    // buscar eventos das turmas este metodo traz so os eventos das turmas da pessoa, sem carregar a agenda inteira
    @Query("SELECT e FROM Evento e WHERE e.idTurma IN :turmas ORDER BY e.inicio")
    List<Evento> buscarDasTurmas(@Param("turmas") Collection<Long> turmas);

    // buscar eventos para o administrador este metodo filtra por texto tipo e situacao e devolve so uma pagina
    @Query(value = """
            SELECT e.*
            FROM public."EVENTOS" e
            LEFT JOIN public."TURMAS" t ON t.id = e.id_turma
            WHERE (:padrao = '' OR e.titulo ILIKE :padrao OR t.nome_turma ILIKE :padrao)
              AND (:tipo = '' OR e.tipo = :tipo)
              AND (:status = '' OR e.status = :status)
            ORDER BY e.id DESC
            """, countQuery = """
            SELECT COUNT(*)
            FROM public."EVENTOS" e
            LEFT JOIN public."TURMAS" t ON t.id = e.id_turma
            WHERE (:padrao = '' OR e.titulo ILIKE :padrao OR t.nome_turma ILIKE :padrao)
              AND (:tipo = '' OR e.tipo = :tipo)
              AND (:status = '' OR e.status = :status)
            """, nativeQuery = true)
    Page<Evento> buscarParaAdmin(@Param("padrao") String padrao, @Param("tipo") String tipo,
                                 @Param("status") String status, Pageable pagina);

    // buscar eventos das turmas no periodo este metodo traz os eventos que aparecem em algum dia da janela informada
    @Query("SELECT e FROM Evento e WHERE e.idTurma IN :turmas AND e.inicio < :fim AND (e.fim IS NULL OR e.fim >= :inicio) ORDER BY e.inicio")
    List<Evento> buscarDasTurmasNoPeriodo(@Param("turmas") Collection<Long> turmas,
                                          @Param("inicio") LocalDateTime inicio,
                                          @Param("fim") LocalDateTime fim);

    // listar eventos a partir da data este metodo busca eventos das turmas pela situacao em ordem crescente
    List<Evento> findByIdTurmaInAndStatusAndInicioGreaterThanEqualOrderByInicioAsc(
            Collection<Long> idsTurmas,
            String status,
            LocalDateTime inicio
    );

    // listar eventos entre duas datas este metodo busca eventos das turmas pela situacao dentro do horizonte em ordem crescente
    List<Evento> findByIdTurmaInAndStatusAndInicioBetweenOrderByInicioAsc(
            Collection<Long> idsTurmas,
            String status,
            LocalDateTime inicio,
            LocalDateTime fim
    );

    // listar eventos anteriores a data este metodo busca eventos das turmas pela situacao em ordem decrescente
    List<Evento> findByIdTurmaInAndStatusAndInicioLessThanOrderByInicioDesc(
            Collection<Long> idsTurmas,
            String status,
            LocalDateTime inicio
    );
}