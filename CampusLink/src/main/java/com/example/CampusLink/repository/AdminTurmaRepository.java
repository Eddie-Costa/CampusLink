package com.example.CampusLink.repository;

import com.example.CampusLink.dto.Admin.cadastrarTurmaAdminDTO;
import com.example.CampusLink.dto.Admin.editarTurmaAdminDTO;
import com.example.CampusLink.dto.Admin.turmaAdminDTO;
import com.example.CampusLink.dto.Admin.usuarioAdminDTO;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class AdminTurmaRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transacao;

    public AdminTurmaRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transacao = new TransactionTemplate(transactionManager);
        // preserva a transacao independente que cada gravacao abria no dao
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public List<turmaAdminDTO> listarTurmas() throws SQLException {

        String sql = """
                SELECT
                    t.id,
                    t.nome_turma,
                    t.descricao,
                    t.id_proprietario,
                    u.nome AS nome_proprietario,
                    (
                        SELECT COUNT(*)
                        FROM public."ALUNO_TURMA" at
                        WHERE at.id_turma = t.id
                    ) AS quantidade_alunos,
                    (
                        SELECT COUNT(*)
                        FROM public."PROFESSOR_TURMA" pt
                        WHERE pt.id_turma = t.id
                    ) AS quantidade_professores
                FROM public."TURMAS" t
                LEFT JOIN public."PROFESSORES" p ON p.id = t.id_proprietario
                LEFT JOIN public."USUARIOS" u ON u.id = p.id_usuario
                ORDER BY t.nome_turma
                """;

        try {
            return jdbcTemplate.query(sql, (rs, linha) -> {
                turmaAdminDTO turma = new turmaAdminDTO();
                turma.setId(rs.getLong("id"));
                turma.setNomeTurma(rs.getString("nome_turma"));
                turma.setDescricao(rs.getString("descricao"));
                turma.setIdProprietario(rs.getObject("id_proprietario", Long.class));
                turma.setNomeProprietario(rs.getString("nome_proprietario"));
                turma.setQuantidadeAlunos(rs.getInt("quantidade_alunos"));
                turma.setQuantidadeProfessores(rs.getInt("quantidade_professores"));
                return turma;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public List<usuarioAdminDTO> listarProfessoresAtivos() throws SQLException {

        String sql = """
                SELECT
                    p.id AS id_professor,
                    u.id AS id_usuario,
                    u.nome,
                    u.email,
                    p.matricula,
                    u.status
                FROM public."PROFESSORES" p
                INNER JOIN public."USUARIOS" u ON u.id = p.id_usuario
                WHERE u.status = true
                ORDER BY u.nome
                """;

        try {
            return jdbcTemplate.query(sql, (rs, linha) -> {
                usuarioAdminDTO professor = new usuarioAdminDTO();
                professor.setIdUsuario(rs.getLong("id_usuario"));
                professor.setIdPerfil(rs.getLong("id_professor"));
                professor.setNome(rs.getString("nome"));
                professor.setEmail(rs.getString("email"));
                professor.setIdentificador(rs.getString("matricula"));
                professor.setPerfil("Professor");
                professor.setStatus(rs.getBoolean("status"));
                return professor;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public Long cadastrarTurma(cadastrarTurmaAdminDTO turma) throws SQLException {

        String sqlProfessor = """
                SELECT 1
                FROM public."PROFESSORES" p
                INNER JOIN public."USUARIOS" u ON u.id = p.id_usuario
                WHERE p.id = ?
                  AND u.status = true
                """;

        String sqlTurma = """
                INSERT INTO public."TURMAS"
                    (nome_turma, descricao, id_proprietario, id_professores)
                VALUES (?, ?, ?, ARRAY[CAST(? AS bigint)])
                RETURNING id
                """;

        String sqlProfessorTurma = """
                INSERT INTO public."PROFESSOR_TURMA"
                    (id_professor, id_turma)
                VALUES (?, ?)
                """;

        try {
            return transacao.execute(status -> {
                if (!jdbcTemplate.query(sqlProfessor, rs -> { return rs.next(); }, turma.getIdProprietario())) {
                    throw new DataIntegrityViolationException("Professor responsável não encontrado ou está inativo.");
                }

                List<Long> ids = jdbcTemplate.query(sqlTurma, (rs, linha) -> rs.getLong("id"),
                        turma.getNomeTurma().trim(), turma.getDescricao(),
                        turma.getIdProprietario(), turma.getIdProprietario());
                if (ids.isEmpty()) {
                    throw new DataIntegrityViolationException("Não foi possível cadastrar a turma.");
                }

                Long idTurma = ids.getFirst();
                if (jdbcTemplate.update(sqlProfessorTurma, turma.getIdProprietario(), idTurma) == 0) {
                    throw new DataIntegrityViolationException("Não foi possível relacionar o professor à turma.");
                }
                return idTurma;
            });
        } catch (DataAccessException | TransactionException e) {
            throw converterErro(e);
        }
    }

    public editarTurmaAdminDTO buscarTurmaPorId(Long idTurma) throws SQLException {

        String sql = """
                SELECT
                    id,
                    nome_turma,
                    descricao,
                    id_proprietario
                FROM public."TURMAS"
                WHERE id = ?
                """;

        try {
            List<editarTurmaAdminDTO> turmas = jdbcTemplate.query(sql, (rs, linha) -> {
                editarTurmaAdminDTO turma = new editarTurmaAdminDTO();
                turma.setId(rs.getLong("id"));
                turma.setNomeTurma(rs.getString("nome_turma"));
                turma.setDescricao(rs.getString("descricao"));
                turma.setIdProprietario(rs.getObject("id_proprietario", Long.class));
                return turma;
            }, idTurma);
            return turmas.isEmpty() ? null : turmas.getFirst();
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public boolean atualizarTurma(editarTurmaAdminDTO turma) throws SQLException {

        String sqlProfessor = """
                SELECT 1
                FROM public."PROFESSORES" p
                INNER JOIN public."USUARIOS" u ON u.id = p.id_usuario
                WHERE p.id = ?
                  AND u.status = true
                """;

        String sqlBuscarResponsavelAtual = """
                SELECT id_proprietario
                FROM public."TURMAS"
                WHERE id = ?
                """;

        String sqlAtualizar = """
                UPDATE public."TURMAS"
                SET nome_turma = ?,
                    descricao = ?,
                    id_proprietario = ?
                WHERE id = ?
                """;

        String sqlAdicionarProfessorArray = """
                UPDATE public."TURMAS"
                SET id_professores =
                    CASE
                        WHEN ? = ANY(COALESCE(id_professores, ARRAY[]::bigint[]))
                        THEN COALESCE(id_professores, ARRAY[]::bigint[])
                        ELSE array_append(COALESCE(id_professores, ARRAY[]::bigint[]), ?)
                    END
                WHERE id = ?
                """;

        String sqlVerificarProfessorTurma = """
                SELECT 1
                FROM public."PROFESSOR_TURMA"
                WHERE id_professor = ?
                  AND id_turma = ?
                """;

        String sqlAdicionarProfessorTurma = """
                INSERT INTO public."PROFESSOR_TURMA"
                    (id_professor, id_turma)
                VALUES (?, ?)
                """;

        try {
            return transacao.execute(status -> {
                if (!jdbcTemplate.query(sqlProfessor, rs -> { return rs.next(); }, turma.getIdProprietario())) {
                    throw new DataIntegrityViolationException("Professor responsável não encontrado ou está inativo.");
                }

                List<Long> responsaveis = jdbcTemplate.query(sqlBuscarResponsavelAtual,
                        (rs, linha) -> rs.getObject("id_proprietario", Long.class), turma.getId());
                if (responsaveis.isEmpty()) {
                    status.setRollbackOnly();
                    return false;
                }

                Long idResponsavelAnterior = responsaveis.getFirst();
                if (jdbcTemplate.update(sqlAtualizar, turma.getNomeTurma().trim(), turma.getDescricao(),
                        turma.getIdProprietario(), turma.getId()) == 0) {
                    status.setRollbackOnly();
                    return false;
                }

                // preserva o responsavel anterior no array como no fluxo atual
                if (idResponsavelAnterior != null) {
                    jdbcTemplate.update(sqlAdicionarProfessorArray,
                            idResponsavelAnterior, idResponsavelAnterior, turma.getId());
                }
                jdbcTemplate.update(sqlAdicionarProfessorArray,
                        turma.getIdProprietario(), turma.getIdProprietario(), turma.getId());

                boolean professorJaEstaNaTurma = jdbcTemplate.query(sqlVerificarProfessorTurma,
                        rs -> { return rs.next(); }, turma.getIdProprietario(), turma.getId());
                if (!professorJaEstaNaTurma
                        && jdbcTemplate.update(sqlAdicionarProfessorTurma,
                        turma.getIdProprietario(), turma.getId()) == 0) {
                    throw new DataIntegrityViolationException("Não foi possível relacionar o novo responsável à turma.");
                }
                return true;
            });
        } catch (DataAccessException | TransactionException e) {
            throw converterErro(e);
        }
    }

    public List<usuarioAdminDTO> listarAlunosDaTurma(Long idTurma) throws SQLException {

        String sql = """
                SELECT
                    a.id AS id_aluno,
                    u.id AS id_usuario,
                    u.nome,
                    u.email,
                    u.telefone,
                    u.datanasc,
                    u.status,
                    a.rgm
                FROM public."ALUNO_TURMA" at
                INNER JOIN public."ALUNOS" a ON a.id = at.id_aluno
                INNER JOIN public."USUARIOS" u ON u.id = a.id_usuario
                WHERE at.id_turma = ?
                ORDER BY u.nome
                """;

        try {
            return jdbcTemplate.query(sql, (rs, linha) -> montarAluno(rs), idTurma);
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public List<usuarioAdminDTO> listarAlunosDisponiveis(Long idTurma) throws SQLException {

        String sql = """
                SELECT
                    a.id AS id_aluno,
                    u.id AS id_usuario,
                    u.nome,
                    u.email,
                    u.telefone,
                    u.datanasc,
                    u.status,
                    a.rgm
                FROM public."ALUNOS" a
                INNER JOIN public."USUARIOS" u ON u.id = a.id_usuario
                WHERE u.status = true
                  AND NOT EXISTS (
                      SELECT 1
                      FROM public."ALUNO_TURMA" at
                      WHERE at.id_aluno = a.id
                        AND at.id_turma = ?
                  )
                ORDER BY u.nome
                """;

        try {
            return jdbcTemplate.query(sql, (rs, linha) -> montarAluno(rs), idTurma);
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public boolean adicionarAlunoNaTurma(Long idTurma, Long idAluno) throws SQLException {

        if (idTurma == null || idAluno == null) {
            return false;
        }

        String sqlTurma = """
                SELECT 1
                FROM public."TURMAS"
                WHERE id = ?
                """;

        String sqlAluno = """
                SELECT 1
                FROM public."ALUNOS" a
                INNER JOIN public."USUARIOS" u ON u.id = a.id_usuario
                WHERE a.id = ?
                  AND u.status = true
                """;

        String sqlVinculo = """
                SELECT 1
                FROM public."ALUNO_TURMA"
                WHERE id_aluno = ?
                  AND id_turma = ?
                """;

        String sqlAtualizarTurma = """
                UPDATE public."TURMAS"
                SET id_alunos =
                    CASE
                        WHEN ? = ANY(COALESCE(id_alunos, ARRAY[]::bigint[]))
                        THEN COALESCE(id_alunos, ARRAY[]::bigint[])
                        ELSE array_append(COALESCE(id_alunos, ARRAY[]::bigint[]), ?)
                    END
                WHERE id = ?
                """;

        String sqlAdicionarAluno = """
                INSERT INTO public."ALUNO_TURMA"
                    (id_aluno, id_turma, situacao_aluno)
                VALUES (?, ?, ?)
                """;

        try {
            return transacao.execute(status -> {
                if (!jdbcTemplate.query(sqlTurma, rs -> { return rs.next(); }, idTurma)
                        || !jdbcTemplate.query(sqlAluno, rs -> { return rs.next(); }, idAluno)
                        || jdbcTemplate.query(sqlVinculo, rs -> { return rs.next(); }, idAluno, idTurma)) {
                    status.setRollbackOnly();
                    return false;
                }

                if (jdbcTemplate.update(sqlAtualizarTurma, idAluno, idAluno, idTurma) == 0) {
                    throw new DataIntegrityViolationException("Não foi possível atualizar a turma.");
                }
                if (jdbcTemplate.update(sqlAdicionarAluno, idAluno, idTurma, "verde") == 0) {
                    throw new DataIntegrityViolationException("Não foi possível adicionar o aluno à turma.");
                }
                return true;
            });
        } catch (DataAccessException | TransactionException e) {
            throw converterErro(e);
        }
    }

    public boolean removerAlunoDaTurma(Long idTurma, Long idAluno) throws SQLException {

        if (idTurma == null || idAluno == null) {
            return false;
        }

        String sqlVerificarVinculo = """
                SELECT 1
                FROM public."ALUNO_TURMA"
                WHERE id_aluno = ?
                  AND id_turma = ?
                """;

        String sqlRemoverVinculo = """
                DELETE FROM public."ALUNO_TURMA"
                WHERE id_aluno = ?
                  AND id_turma = ?
                """;

        String sqlAtualizarTurma = """
                UPDATE public."TURMAS"
                SET id_alunos = array_remove(
                    COALESCE(id_alunos, ARRAY[]::bigint[]),
                    ?
                )
                WHERE id = ?
                """;

        try {
            return transacao.execute(status -> {
                if (!jdbcTemplate.query(sqlVerificarVinculo, rs -> { return rs.next(); }, idAluno, idTurma)) {
                    status.setRollbackOnly();
                    return false;
                }

                if (jdbcTemplate.update(sqlRemoverVinculo, idAluno, idTurma) == 0) {
                    throw new DataIntegrityViolationException("Não foi possível remover o aluno da turma.");
                }
                if (jdbcTemplate.update(sqlAtualizarTurma, idAluno, idTurma) == 0) {
                    throw new DataIntegrityViolationException("Não foi possível atualizar os alunos da turma.");
                }
                return true;
            });
        } catch (DataAccessException | TransactionException e) {
            throw converterErro(e);
        }
    }

    public List<usuarioAdminDTO> listarProfessoresDaTurma(Long idTurma) throws SQLException {

        String sql = """
                SELECT
                    p.id AS id_professor,
                    u.id AS id_usuario,
                    u.nome,
                    u.email,
                    u.telefone,
                    u.datanasc,
                    u.status,
                    p.matricula
                FROM public."PROFESSOR_TURMA" pt
                INNER JOIN public."PROFESSORES" p ON p.id = pt.id_professor
                INNER JOIN public."USUARIOS" u ON u.id = p.id_usuario
                WHERE pt.id_turma = ?
                ORDER BY u.nome
                """;

        try {
            return jdbcTemplate.query(sql, (rs, linha) -> montarProfessor(rs), idTurma);
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public List<usuarioAdminDTO> listarProfessoresDisponiveis(Long idTurma) throws SQLException {

        String sql = """
                SELECT
                    p.id AS id_professor,
                    u.id AS id_usuario,
                    u.nome,
                    u.email,
                    u.telefone,
                    u.datanasc,
                    u.status,
                    p.matricula
                FROM public."PROFESSORES" p
                INNER JOIN public."USUARIOS" u ON u.id = p.id_usuario
                WHERE u.status = true
                  AND NOT EXISTS (
                      SELECT 1
                      FROM public."PROFESSOR_TURMA" pt
                      WHERE pt.id_professor = p.id
                        AND pt.id_turma = ?
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM public."TURMAS" t
                      WHERE t.id = ?
                        AND t.id_proprietario = p.id
                  )
                ORDER BY u.nome
                """;

        try {
            return jdbcTemplate.query(sql, (rs, linha) -> montarProfessor(rs), idTurma, idTurma);
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public boolean adicionarProfessorNaTurma(Long idTurma, Long idProfessor) throws SQLException {

        if (idTurma == null || idProfessor == null) {
            return false;
        }

        String sqlTurma = """
                SELECT id_proprietario
                FROM public."TURMAS"
                WHERE id = ?
                """;

        String sqlProfessor = """
                SELECT 1
                FROM public."PROFESSORES" p
                INNER JOIN public."USUARIOS" u ON u.id = p.id_usuario
                WHERE p.id = ?
                  AND u.status = true
                """;

        String sqlVinculo = """
                SELECT 1
                FROM public."PROFESSOR_TURMA"
                WHERE id_professor = ?
                  AND id_turma = ?
                """;

        String sqlAtualizarTurma = """
                UPDATE public."TURMAS"
                SET id_professores =
                    CASE
                        WHEN ? = ANY(COALESCE(id_professores, ARRAY[]::bigint[]))
                        THEN COALESCE(id_professores, ARRAY[]::bigint[])
                        ELSE array_append(COALESCE(id_professores, ARRAY[]::bigint[]), ?)
                    END
                WHERE id = ?
                """;

        String sqlAdicionarProfessor = """
                INSERT INTO public."PROFESSOR_TURMA"
                    (id_professor, id_turma)
                VALUES (?, ?)
                """;

        try {
            return transacao.execute(status -> {
                List<Long> proprietarios = jdbcTemplate.query(sqlTurma,
                        (rs, linha) -> rs.getObject("id_proprietario", Long.class), idTurma);
                if (proprietarios.isEmpty()) {
                    status.setRollbackOnly();
                    return false;
                }

                Long idProprietario = proprietarios.getFirst();
                if ((idProprietario != null && idProprietario.equals(idProfessor))
                        || !jdbcTemplate.query(sqlProfessor, rs -> { return rs.next(); }, idProfessor)
                        || jdbcTemplate.query(sqlVinculo, rs -> { return rs.next(); }, idProfessor, idTurma)) {
                    status.setRollbackOnly();
                    return false;
                }

                if (jdbcTemplate.update(sqlAtualizarTurma, idProfessor, idProfessor, idTurma) == 0) {
                    throw new DataIntegrityViolationException("Não foi possível atualizar os professores da turma.");
                }
                if (jdbcTemplate.update(sqlAdicionarProfessor, idProfessor, idTurma) == 0) {
                    throw new DataIntegrityViolationException("Não foi possível adicionar o professor à turma.");
                }
                return true;
            });
        } catch (DataAccessException | TransactionException e) {
            throw converterErro(e);
        }
    }

    public boolean removerProfessorDaTurma(Long idTurma, Long idProfessor) throws SQLException {

        if (idTurma == null || idProfessor == null) {
            return false;
        }

        String sqlTurma = """
                SELECT id_proprietario
                FROM public."TURMAS"
                WHERE id = ?
                """;

        String sqlVerificarVinculo = """
                SELECT 1
                FROM public."PROFESSOR_TURMA"
                WHERE id_professor = ?
                  AND id_turma = ?
                """;

        String sqlRemoverVinculo = """
                DELETE FROM public."PROFESSOR_TURMA"
                WHERE id_professor = ?
                  AND id_turma = ?
                """;

        String sqlAtualizarTurma = """
                UPDATE public."TURMAS"
                SET id_professores = array_remove(
                    COALESCE(id_professores, ARRAY[]::bigint[]),
                    ?
                )
                WHERE id = ?
                """;

        try {
            return transacao.execute(status -> {
                List<Long> proprietarios = jdbcTemplate.query(sqlTurma,
                        (rs, linha) -> rs.getObject("id_proprietario", Long.class), idTurma);
                if (proprietarios.isEmpty()) {
                    status.setRollbackOnly();
                    return false;
                }

                Long idProprietario = proprietarios.getFirst();
                if ((idProprietario != null && idProprietario.equals(idProfessor))
                        || !jdbcTemplate.query(sqlVerificarVinculo, rs -> { return rs.next(); }, idProfessor, idTurma)) {
                    status.setRollbackOnly();
                    return false;
                }

                if (jdbcTemplate.update(sqlRemoverVinculo, idProfessor, idTurma) == 0) {
                    throw new DataIntegrityViolationException("Não foi possível remover o professor da turma.");
                }
                if (jdbcTemplate.update(sqlAtualizarTurma, idProfessor, idTurma) == 0) {
                    throw new DataIntegrityViolationException("Não foi possível atualizar os professores da turma.");
                }
                return true;
            });
        } catch (DataAccessException | TransactionException e) {
            throw converterErro(e);
        }
    }

    private usuarioAdminDTO montarAluno(ResultSet rs) throws SQLException {

        usuarioAdminDTO aluno = new usuarioAdminDTO();

        aluno.setIdUsuario(rs.getLong("id_usuario"));
        aluno.setIdPerfil(rs.getLong("id_aluno"));
        aluno.setNome(rs.getString("nome"));
        aluno.setEmail(rs.getString("email"));
        aluno.setTelefone(rs.getString("telefone"));

        if (rs.getDate("datanasc") != null) {
            aluno.setDataNasc(rs.getDate("datanasc").toLocalDate().toString());
        }

        aluno.setIdentificador(rs.getString("rgm"));
        aluno.setPerfil("Aluno");
        aluno.setStatus(rs.getBoolean("status"));

        return aluno;
    }

    // monta os dados do professor usados nas telas do administrador
    private usuarioAdminDTO montarProfessor(ResultSet rs) throws SQLException {

        usuarioAdminDTO professor = new usuarioAdminDTO();

        professor.setIdUsuario(rs.getLong("id_usuario"));
        professor.setIdPerfil(rs.getLong("id_professor"));
        professor.setNome(rs.getString("nome"));
        professor.setEmail(rs.getString("email"));
        professor.setTelefone(rs.getString("telefone"));

        if (rs.getDate("datanasc") != null) {
            professor.setDataNasc(rs.getDate("datanasc").toLocalDate().toString());
        }

        professor.setIdentificador(rs.getString("matricula"));
        professor.setPerfil("Professor");
        professor.setStatus(rs.getBoolean("status"));

        return professor;
    }

    // mantem o contrato de erro usado pelos controllers atuais
    private SQLException converterErro(RuntimeException erro) {
        Throwable causa = erro;
        while (causa != null) {
            if (causa instanceof SQLException sqlException) {
                return sqlException;
            }
            causa = causa.getCause();
        }
        return new SQLException(erro.getMessage(), erro);
    }
}
