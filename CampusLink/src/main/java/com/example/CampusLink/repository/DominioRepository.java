// banco de dominios centraliza pedidos e autorizacoes de sites
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.DominioDTO;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

// persistencia de dominios esta classe registra solicitacoes e consulta enderecos aprovados
@Repository
public class DominioRepository {

    private final JdbcTemplate jdbcTemplate;

    // preparar consulta de dominios este construtor recebe o acesso ao banco
    public DominioRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }



    // cadastrar pedido de dominio no banco este metodo grava endereco professor e justificativa
    public void inserir(DominioDTO dominio) throws SQLException {

        String sql = """
                INSERT INTO public."DOMINIOS" ("dominio", "id_professor", "justificativa")
                VALUES (?, ?, ?)
                RETURNING "id", "status", "created_at"
                """;

        // gravar pedido de dominio prepara endereco professor e justificativa e converte falhas
        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setString(1, dominio.getDominio());
                stmt.setLong(2, dominio.getIdProfessor());
                stmt.setString(3, dominio.getJustificativa());

                // ler dominio solicitado recupera os dados da gravacao e fecha a consulta
                try (ResultSet rs = stmt.executeQuery()) {
                    // preencher dominio cadastrado guarda codigo situacao e data quando o banco devolve o registro
                    if (rs.next()) {
                        dominio.setId(rs.getLong("id"));
                        dominio.setStatus(rs.getString("status"));

                        Timestamp createdAt = rs.getTimestamp("created_at");
                        // preencher data do pedido converte a criacao do dominio apenas quando ela existe
                        if (createdAt != null) {
                            dominio.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
                        }
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // listar dominios do professor no banco este metodo consulta seus pedidos do mais recente ao mais antigo
    public List<DominioDTO> listarPorProfessor(Long idProfessor) throws SQLException {

        String sql = """
                SELECT *
                FROM public."DOMINIOS"
                WHERE "id_professor" = ?
                ORDER BY "created_at" DESC
                """;

        List<DominioDTO> dominios = new ArrayList<>();

        // consultar dominios do professor busca os pedidos pelo solicitante e converte falhas
        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setLong(1, idProfessor);

                // ler pedidos pessoais monta a lista de dominios e fecha a consulta
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        dominios.add(montar(rs));
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }

        return dominios;
    }



    public static final int DOMINIOS_POR_PAGINA = 10;

    // listar dominios pendentes no banco este metodo consulta uma pagina de pedidos por ordem de chegada com o nome do professor
    public Page<DominioDTO> listarPendentes(int pagina) throws SQLException {
        return paginar("""
                SELECT d.*, u."nome" AS nome_professor
                FROM public."DOMINIOS" d
                LEFT JOIN public."PROFESSORES" p ON p."id" = d."id_professor"
                LEFT JOIN public."USUARIOS" u ON u."id" = p."id_usuario"
                WHERE d."status" = 'pendente'
                ORDER BY d."created_at" ASC, d."id" ASC
                """, "pendente", pagina, true);
    }

    // listar dominios aprovados no banco este metodo consulta uma pagina dos enderecos permitidos em ordem alfabetica
    public Page<DominioDTO> listarAprovados(int pagina) throws SQLException {
        return paginar("""
                SELECT d.*
                FROM public."DOMINIOS" d
                WHERE d."status" = 'aprovado'
                ORDER BY d."dominio" ASC, d."id" ASC
                """, "aprovado", pagina, false);
    }

    // paginar dominios este metodo conta os registros da situacao e busca so a pagina pedida
    private Page<DominioDTO> paginar(String sql, String status, int pagina, boolean comProfessor) throws SQLException {
        // consultar pagina de dominios conta o total da situacao e monta apenas as linhas exibidas
        try {
            long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.\"DOMINIOS\" WHERE \"status\" = ?", Long.class, status);
            int paginaValida = Math.min(Math.max(0, pagina), (int) Math.max(0, (total - 1) / DOMINIOS_POR_PAGINA));
            List<DominioDTO> dominios = jdbcTemplate.query(sql + " LIMIT " + DOMINIOS_POR_PAGINA + " OFFSET ?", (rs, linha) -> {
                DominioDTO dominio = montar(rs);
                // incluir professor do pedido preenche o nome quando a consulta traz o autor
                if (comProfessor) {
                    dominio.setNomeProfessor(rs.getString("nome_professor"));
                }
                return dominio;
            }, paginaValida * DOMINIOS_POR_PAGINA);
            return new PageImpl<>(dominios, PageRequest.of(paginaValida, DOMINIOS_POR_PAGINA), total);
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }


    // analisar dominio no banco este metodo grava a decisao o comentario e a data de um pedido pendente
    public boolean analisar(Long id, String novoStatus, String comentarioAdmin) throws SQLException {

        String sql = """
                UPDATE public."DOMINIOS"
                SET "status" = ?,
                    "comentario_admin" = ?,
                    "analisado_em" = now()
                WHERE "id" = ?
                AND "status" = 'pendente'
                """;

        // gravar analise do dominio atualiza situacao e comentario do pedido e converte falhas
        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Boolean>) stmt -> {

                stmt.setString(1, novoStatus);
                stmt.setString(2, comentarioAdmin);
                stmt.setLong(3, id);

                return stmt.executeUpdate() > 0;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }




    // verificar pedido de dominio existente este metodo procura um endereco pendente ou ja aprovado
    public boolean existePendenteOuAprovado(String dominio) throws SQLException {

        String sql = """
                SELECT 1
                FROM public."DOMINIOS"
                WHERE lower("dominio") = lower(?)
                AND "status" IN ('pendente', 'aprovado')
                LIMIT 1
                """;

        // consultar dominio repetido verifica se o endereco ja esta pendente ou aprovado e converte falhas
        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Boolean>) stmt -> {

                stmt.setString(1, dominio);

                // ler existencia do dominio confirma se encontrou um registro e fecha a consulta
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }



    // verificar endereco autorizado no banco este metodo aceita um dominio aprovado ou seus subdominios
    public boolean hostAutorizado(String host) throws SQLException {

        String sql = """
                SELECT 1
                FROM public."DOMINIOS"
                WHERE "status" = 'aprovado'
                AND (
                    lower(?) = lower("dominio")
                    OR right(lower(?), length("dominio") + 1) = '.' || lower("dominio")
                )
                LIMIT 1
                """;

        // consultar endereco autorizado verifica o servidor informado e converte falhas do banco
        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Boolean>) stmt -> {

                stmt.setString(1, host);
                stmt.setString(2, host);

                // ler autorizacao do endereco confirma se encontrou uma permissao e fecha a consulta
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }



    // montar dados do dominio este metodo organiza o pedido consultado com datas e decisao administrativa
    private DominioDTO montar(ResultSet rs) throws SQLException {

        DominioDTO dominio = new DominioDTO();

        dominio.setId(rs.getLong("id"));
        dominio.setDominio(rs.getString("dominio"));
        dominio.setStatus(rs.getString("status"));

        long idProfessor = rs.getLong("id_professor");
        dominio.setIdProfessor(rs.wasNull() ? null : idProfessor);

        dominio.setJustificativa(rs.getString("justificativa"));
        dominio.setComentarioAdmin(rs.getString("comentario_admin"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        // preencher criacao do dominio converte a data apenas quando ela existe
        if (createdAt != null) {
            dominio.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
        }

        Timestamp analisadoEm = rs.getTimestamp("analisado_em");
        // preencher analise do dominio converte a data apenas quando o pedido ja foi analisado
        if (analisadoEm != null) {
            dominio.setAnalisadoEm(analisadoEm.toInstant().atOffset(ZoneOffset.UTC));
        }

        return dominio;
    }


    // tratar erro do banco de dominios este metodo recupera a falha original ou cria uma falha de consulta
    private SQLException converterErro(DataAccessException erro) {
        Throwable causa = erro;
        while (causa != null) {
            // recuperar falha do banco devolve a causa original quando ela e uma falha de consulta
            if (causa instanceof SQLException sqlException) {
                return sqlException;
            }
            causa = causa.getCause();
        }
        return new SQLException(erro.getMessage(), erro);
    }
}
