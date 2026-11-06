// banco de denuncias centraliza relatos e contagens de participantes
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.DenunciaDTO;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

// persistencia de relatos esta classe grava motivos identifica repeticoes e atualiza decisoes de analise
@Repository
public class DenunciaRepository {

    private final JdbcTemplate jdbcTemplate;

    // preparar consulta de denuncias este construtor recebe o acesso ao banco
    public DenunciaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // cadastrar denuncia no banco este metodo grava o motivo e os envolvidos e recupera os dados do registro
    public void inserir(DenunciaDTO denuncia) throws SQLException {

        String sql = """
                INSERT INTO public."DENUNCIAS"
                ( "id_conteudo", "id_aluno", "id_professor", "motivo")
                VALUES (?, ?, ?, ?)
                RETURNING "id", "status", "created_at"
                """;

        // gravar denuncia prepara conteudo aluno professor e motivo e converte falhas do banco
        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {
                stmt.setLong(1, denuncia.getIdConteudo());
                stmt.setLong(2, denuncia.getIdAluno());
                stmt.setLong(3, denuncia.getIdProfessor());
                stmt.setString(4, denuncia.getMotivo());

                // ler denuncia criada recupera os dados devolvidos pelo cadastro e fecha a consulta
                try (ResultSet rs = stmt.executeQuery()) {

                    // preencher denuncia salva guarda codigo situacao e data quando o registro foi devolvido
                    if (rs.next()) {

                        denuncia.setId(rs.getLong("id"));
                        denuncia.setStatus(rs.getString("status"));
                        Timestamp createdAt = rs.getTimestamp("created_at");

                        // preencher data da denuncia converte a data apenas quando ela existe
                        if (createdAt != null) {
                            denuncia.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
                        }
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // verificar denuncia repetida este metodo confere se o aluno ja denunciou o material
    public boolean alunoJaDenunciou(Long idConteudo, Long idAluno) throws SQLException {

        String sql = """
                SELECT COUNT(*) AS quantidade
                FROM public."DENUNCIAS"
                WHERE "id_conteudo" = ?
                AND "id_aluno" = ?
                """;

        // conferir denuncia anterior consulta aluno e conteudo e converte falhas do banco
        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Boolean>) stmt -> {

                stmt.setLong(1, idConteudo);
                stmt.setLong(2, idAluno);

                // ler denuncias anteriores recupera a contagem e fecha a consulta
                try (ResultSet rs = stmt.executeQuery()) {

                    // identificar denuncia repetida confirma quando a quantidade encontrada e maior que zero
                    if (rs.next()) {
                        return rs.getInt("quantidade") > 0;
                    }
                }
                return false;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // contar denuncias pendentes do conteudo este metodo conta alunos distintos com relatos ainda nao analisados
    public int contarDenunciasPendentes(Long idConteudo) throws SQLException {

        String sql = """
                SELECT COUNT(DISTINCT "id_aluno") AS quantidade
                FROM public."DENUNCIAS"
                WHERE "id_conteudo" = ?
                AND "status" = 'pendente'
                """;

        // consultar total de denuncias pendentes busca pelo conteudo e converte falhas do banco
        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Integer>) stmt -> {

                stmt.setLong(1, idConteudo);

                // ler total de denuncias recupera a quantidade pendente e fecha a consulta
                try (ResultSet rs = stmt.executeQuery()) {

                    // devolver denuncias contadas usa a quantidade apenas quando a consulta possui resultado
                    if (rs.next()) {
                        return rs.getInt("quantidade");
                    }
                }
                return 0;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // contar alunos para denuncia este metodo consulta a quantidade de participantes distintos da turma
    public int contarAlunosDaTurma(
            Long idTurma
    ) throws SQLException {

        String sql = """
                SELECT COUNT(DISTINCT "id_aluno") AS quantidade
                FROM public."ALUNO_TURMA"
                WHERE "id_turma" = ?
                """;

        // consultar tamanho da turma conta os alunos vinculados e converte falhas do banco
        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Integer>) stmt -> {

                stmt.setLong(1, idTurma);

                // ler total de alunos recupera a quantidade encontrada e fecha a consulta
                try (ResultSet rs = stmt.executeQuery()) {

                    // devolver alunos contados usa a quantidade apenas quando a consulta possui resultado
                    if (rs.next()) {
                        return rs.getInt("quantidade");
                    }
                }
                return 0;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // listar autores das denuncias este metodo consulta os nomes dos alunos sem repeticao
    public List<String> listarNomesAlunosPorConteudo(Long idConteudo) throws SQLException {

        List<String> alunos = new ArrayList<>();

        String sql = """
                SELECT DISTINCT u."nome"
                FROM public."DENUNCIAS" d
                INNER JOIN public."ALUNOS" a
                    ON a."id" = d."id_aluno"
                INNER JOIN public."USUARIOS" u
                    ON u."id" = a."id_usuario"
                WHERE d."id_conteudo" = ?
                ORDER BY u."nome"
                """;

        // consultar autores das denuncias busca os nomes pelo conteudo e converte falhas
        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setLong(1, idConteudo);

                // ler nomes dos denunciantes preenche a lista de alunos e fecha a consulta
                try (ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        alunos.add(rs.getString("nome"));
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }

        return alunos;
    }

    // listar denuncias do material este metodo consulta motivos situacoes e nomes dos alunos
    public List<DenunciaDTO> listarPorConteudo(Long idConteudo) throws SQLException {

        List<DenunciaDTO> denuncias = new ArrayList<>();

        String sql = """
                SELECT d.*, u."nome" AS nome_aluno
                FROM public."DENUNCIAS" d
                LEFT JOIN public."ALUNOS" a
                    ON a."id" = d."id_aluno"
                LEFT JOIN public."USUARIOS" u
                    ON u."id" = a."id_usuario"
                WHERE d."id_conteudo" = ?
                ORDER BY d."created_at" DESC
                """;

        // consultar relatos do conteudo busca as denuncias associadas e converte falhas do banco
        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setLong(1, idConteudo);

                // ler relatos encontrados monta denuncias com autor motivo e situacao e fecha a consulta
                try (ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {

                        DenunciaDTO denuncia = new DenunciaDTO();
                        denuncia.setId(rs.getLong("id"));
                        denuncia.setIdConteudo(rs.getLong("id_conteudo"));
                        denuncia.setIdAluno(rs.getLong("id_aluno"));
                        denuncia.setIdProfessor(rs.getLong("id_professor"));
                        denuncia.setMotivo(rs.getString("motivo"));
                        denuncia.setStatus(rs.getString("status"));
                        denuncia.setNomeAluno(rs.getString("nome_aluno"));
                        Timestamp createdAt = rs.getTimestamp("created_at");

                        // preencher data do relato inclui a data de criacao quando ela existe
                        if (createdAt != null) {
                            denuncia.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
                        }

                        denuncias.add(denuncia);
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }

        return denuncias;
    }

    // atualizar denuncias pendentes este metodo aplica a decisao de analise aos relatos do conteudo
    public void atualizarStatusPorConteudo(Long idConteudo, String status) throws SQLException {

        String sql = """
                UPDATE public."DENUNCIAS"
                SET "status" = ?
                WHERE "id_conteudo" = ?
                AND "status" = 'pendente'
                """;

        // atualizar situacao das denuncias grava o novo estado pelo conteudo e converte falhas
        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {
                stmt.setString(1, status);
                stmt.setLong(2, idConteudo);
                stmt.executeUpdate();
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }


    // tratar erro do banco de denuncias este metodo recupera a falha original ou cria uma falha de consulta
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
