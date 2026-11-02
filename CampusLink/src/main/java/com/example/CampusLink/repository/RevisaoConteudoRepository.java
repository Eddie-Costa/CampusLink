package com.example.CampusLink.repository;

import com.example.CampusLink.model.Conteudo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RevisaoConteudoRepository extends JpaRepository<Conteudo, Long> {

    // libera o conteudo que esta aguardando analise
    @Modifying
    @Query(value = """
            UPDATE public."CONTEUDOS"
            SET "status" = 'ativo',
                "comentario_admin" = NULL,
                "revisao_solicitada" = false,
                "revisao_solicitada_em" = NULL
            WHERE "id" = :idConteudo
            AND "status" = 'suspenso_denuncia'
            AND "revisao_solicitada" = true
            """, nativeQuery = true)
    int liberarConteudo(@Param("idConteudo") Long idConteudo);

    // reprova o conteudo que esta aguardando analise
    @Modifying
    @Query(value = """
            UPDATE public."CONTEUDOS"
            SET "status" = 'removido',
                "comentario_admin" = :comentarioAdmin,
                "revisao_solicitada" = false,
                "revisao_solicitada_em" = NULL
            WHERE "id" = :idConteudo
            AND "status" = 'suspenso_denuncia'
            AND "revisao_solicitada" = true
            """, nativeQuery = true)
    int reprovarConteudo(@Param("idConteudo") Long idConteudo,
                         @Param("comentarioAdmin") String comentarioAdmin);

    // marca as denuncias pendentes como liberadas
    @Modifying
    @Query(value = """
            UPDATE public."DENUNCIAS"
            SET "status" = 'analisada_liberada'
            WHERE "id_conteudo" = :idConteudo
            AND "status" = 'pendente'
            """, nativeQuery = true)
    int marcarDenunciasLiberadas(@Param("idConteudo") Long idConteudo);

    // marca as denuncias pendentes como removidas
    @Modifying
    @Query(value = """
            UPDATE public."DENUNCIAS"
            SET "status" = 'analisada_removida'
            WHERE "id_conteudo" = :idConteudo
            AND "status" = 'pendente'
            """, nativeQuery = true)
    int marcarDenunciasRemovidas(@Param("idConteudo") Long idConteudo);
}