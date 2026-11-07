// banco de revisoes define as alteracoes de materiais e denuncias apos analise
package com.example.CampusLink.repository;

import com.example.CampusLink.model.Conteudo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// decisoes de moderacao esta interface atualiza o conteudo e a situacao dos relatos pendentes
public interface RevisaoConteudoRepository extends JpaRepository<Conteudo, Long> {


    // liberar material em revisao no banco este metodo reativa o conteudo e limpa os dados da analise
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


    // reprovar material em revisao no banco este metodo marca o conteudo como removido e guarda o motivo
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


    // resolver denuncias com liberacao este metodo marca os relatos pendentes como analisados e liberados
    @Modifying
    @Query(value = """
            UPDATE public."DENUNCIAS"
            SET "status" = 'analisada_liberada'
            WHERE "id_conteudo" = :idConteudo
            AND "status" = 'pendente'
            """, nativeQuery = true)
    int marcarDenunciasLiberadas(@Param("idConteudo") Long idConteudo);


    // resolver denuncias com remocao este metodo marca os relatos pendentes como analisados e removidos
    @Modifying
    @Query(value = """
            UPDATE public."DENUNCIAS"
            SET "status" = 'analisada_removida'
            WHERE "id_conteudo" = :idConteudo
            AND "status" = 'pendente'
            """, nativeQuery = true)
    int marcarDenunciasRemovidas(@Param("idConteudo") Long idConteudo);
}