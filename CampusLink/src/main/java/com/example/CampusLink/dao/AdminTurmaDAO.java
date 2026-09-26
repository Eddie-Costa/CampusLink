package com.example.CampusLink.dao;

import com.example.CampusLink.dto.Admin.cadastrarTurmaAdminDTO;
import com.example.CampusLink.dto.Admin.editarTurmaAdminDTO;
import com.example.CampusLink.dto.Admin.turmaAdminDTO;
import com.example.CampusLink.dto.Admin.usuarioAdminDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class AdminTurmaDAO {

    @Autowired
    private DataSource dataSource;

    public List<turmaAdminDTO> listarTurmas() throws SQLException {

        List<turmaAdminDTO> turmas = new ArrayList<>();

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

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()
        ) {

            while (rs.next()) {

                turmaAdminDTO turma = new turmaAdminDTO();

                turma.setId(rs.getLong("id"));
                turma.setNomeTurma(rs.getString("nome_turma"));
                turma.setDescricao(rs.getString("descricao"));
                turma.setIdProprietario(rs.getObject("id_proprietario", Long.class));
                turma.setNomeProprietario(rs.getString("nome_proprietario"));
                turma.setQuantidadeAlunos(rs.getInt("quantidade_alunos"));
                turma.setQuantidadeProfessores(rs.getInt("quantidade_professores"));

                turmas.add(turma);
            }
        }

        return turmas;
    }

    // busca os professores ativos para o administrador escolher
    public List<usuarioAdminDTO> listarProfessoresAtivos() throws SQLException {

        List<usuarioAdminDTO> professores = new ArrayList<>();

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

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()
        ) {

            while (rs.next()) {

                usuarioAdminDTO professor = new usuarioAdminDTO();

                professor.setIdUsuario(rs.getLong("id_usuario"));
                professor.setIdPerfil(rs.getLong("id_professor"));
                professor.setNome(rs.getString("nome"));
                professor.setEmail(rs.getString("email"));
                professor.setIdentificador(rs.getString("matricula"));
                professor.setPerfil("Professor");
                professor.setStatus(rs.getBoolean("status"));

                professores.add(professor);
            }
        }

        return professores;
    }

    // cadastra a turma e liga o professor responsavel a ela
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

        try (Connection conn = dataSource.getConnection()) {

            conn.setAutoCommit(false);

            try {

                // confirma se o professor escolhido existe e esta ativo
                try (PreparedStatement stmtProfessor = conn.prepareStatement(sqlProfessor)) {

                    stmtProfessor.setLong(1, turma.getIdProprietario());

                    try (ResultSet rs = stmtProfessor.executeQuery()) {

                        if (!rs.next()) {
                            throw new SQLException("Professor responsável não encontrado ou está inativo.");
                        }
                    }
                }

                Long idTurma;

                // cadastra a turma com o responsavel tambem no array de professores
                try (PreparedStatement stmtTurma = conn.prepareStatement(sqlTurma)) {

                    stmtTurma.setString(1, turma.getNomeTurma().trim());
                    stmtTurma.setString(2, turma.getDescricao());
                    stmtTurma.setLong(3, turma.getIdProprietario());
                    stmtTurma.setLong(4, turma.getIdProprietario());

                    try (ResultSet rs = stmtTurma.executeQuery()) {

                        if (!rs.next()) {
                            throw new SQLException("Não foi possível cadastrar a turma.");
                        }

                        idTurma = rs.getLong("id");
                    }
                }

                // cadastra o responsavel na tabela de relacionamento
                try (PreparedStatement stmtProfessorTurma = conn.prepareStatement(sqlProfessorTurma)) {

                    stmtProfessorTurma.setLong(1, turma.getIdProprietario());
                    stmtProfessorTurma.setLong(2, idTurma);

                    if (stmtProfessorTurma.executeUpdate() == 0) {
                        throw new SQLException("Não foi possível relacionar o professor à turma.");
                    }
                }

                conn.commit();
                return idTurma;

            } catch (SQLException e) {

                conn.rollback();
                throw e;
            }
        }
    }

    // busca uma turma para preencher a tela de edicao
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

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setLong(1, idTurma);

            try (ResultSet rs = stmt.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                editarTurmaAdminDTO turma = new editarTurmaAdminDTO();

                turma.setId(rs.getLong("id"));
                turma.setNomeTurma(rs.getString("nome_turma"));
                turma.setDescricao(rs.getString("descricao"));
                turma.setIdProprietario(rs.getObject("id_proprietario", Long.class));

                return turma;
            }
        }
    }

    // atualiza os dados principais da turma
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

        try (Connection conn = dataSource.getConnection()) {

            conn.setAutoCommit(false);

            try {

                // confirma se o novo responsavel existe e esta ativo
                try (PreparedStatement stmtProfessor = conn.prepareStatement(sqlProfessor)) {

                    stmtProfessor.setLong(1, turma.getIdProprietario());

                    try (ResultSet rs = stmtProfessor.executeQuery()) {

                        if (!rs.next()) {
                            throw new SQLException("Professor responsável não encontrado ou está inativo.");
                        }
                    }
                }

                Long idResponsavelAnterior;

                // guarda o responsavel anterior antes da alteracao
                try (PreparedStatement stmtResponsavelAtual = conn.prepareStatement(sqlBuscarResponsavelAtual)) {

                    stmtResponsavelAtual.setLong(1, turma.getId());

                    try (ResultSet rs = stmtResponsavelAtual.executeQuery()) {

                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }

                        idResponsavelAnterior = rs.getObject("id_proprietario", Long.class);
                    }
                }

                // atualiza os dados principais da turma
                try (PreparedStatement stmtAtualizar = conn.prepareStatement(sqlAtualizar)) {

                    stmtAtualizar.setString(1, turma.getNomeTurma().trim());
                    stmtAtualizar.setString(2, turma.getDescricao());
                    stmtAtualizar.setLong(3, turma.getIdProprietario());
                    stmtAtualizar.setLong(4, turma.getId());

                    if (stmtAtualizar.executeUpdate() == 0) {
                        conn.rollback();
                        return false;
                    }
                }

                // mantem o antigo responsavel no array caso ele continue vinculado
                if (idResponsavelAnterior != null) {

                    try (PreparedStatement stmtArrayAnterior = conn.prepareStatement(sqlAdicionarProfessorArray)) {

                        stmtArrayAnterior.setLong(1, idResponsavelAnterior);
                        stmtArrayAnterior.setLong(2, idResponsavelAnterior);
                        stmtArrayAnterior.setLong(3, turma.getId());
                        stmtArrayAnterior.executeUpdate();
                    }
                }

                // garante que o novo responsavel esta no array
                try (PreparedStatement stmtArrayNovo = conn.prepareStatement(sqlAdicionarProfessorArray)) {

                    stmtArrayNovo.setLong(1, turma.getIdProprietario());
                    stmtArrayNovo.setLong(2, turma.getIdProprietario());
                    stmtArrayNovo.setLong(3, turma.getId());
                    stmtArrayNovo.executeUpdate();
                }

                boolean professorJaEstaNaTurma;

                // verifica se o novo responsavel ja esta na tabela de relacionamento
                try (PreparedStatement stmtVerificar = conn.prepareStatement(sqlVerificarProfessorTurma)) {

                    stmtVerificar.setLong(1, turma.getIdProprietario());
                    stmtVerificar.setLong(2, turma.getId());

                    try (ResultSet rs = stmtVerificar.executeQuery()) {
                        professorJaEstaNaTurma = rs.next();
                    }
                }

                // adiciona o novo responsavel caso ainda nao esteja relacionado
                if (!professorJaEstaNaTurma) {

                    try (PreparedStatement stmtAdicionar = conn.prepareStatement(sqlAdicionarProfessorTurma)) {

                        stmtAdicionar.setLong(1, turma.getIdProprietario());
                        stmtAdicionar.setLong(2, turma.getId());

                        if (stmtAdicionar.executeUpdate() == 0) {
                            throw new SQLException("Não foi possível relacionar o novo responsável à turma.");
                        }
                    }
                }

                conn.commit();
                return true;

            } catch (SQLException e) {

                conn.rollback();
                throw e;
            }
        }
    }

    // busca os alunos vinculados a uma turma
    public List<usuarioAdminDTO> listarAlunosDaTurma(Long idTurma) throws SQLException {

        List<usuarioAdminDTO> alunos = new ArrayList<>();

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

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setLong(1, idTurma);

            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    alunos.add(montarAluno(rs));
                }
            }
        }

        return alunos;
    }

    // busca alunos ativos que ainda nao estao na turma
    public List<usuarioAdminDTO> listarAlunosDisponiveis(Long idTurma) throws SQLException {

        List<usuarioAdminDTO> alunos = new ArrayList<>();

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

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setLong(1, idTurma);

            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    alunos.add(montarAluno(rs));
                }
            }
        }

        return alunos;
    }

    // adiciona um aluno na turma
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

        try (Connection conn = dataSource.getConnection()) {

            conn.setAutoCommit(false);

            try {

                try (PreparedStatement stmtTurma = conn.prepareStatement(sqlTurma)) {

                    stmtTurma.setLong(1, idTurma);

                    try (ResultSet rs = stmtTurma.executeQuery()) {

                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                    }
                }

                try (PreparedStatement stmtAluno = conn.prepareStatement(sqlAluno)) {

                    stmtAluno.setLong(1, idAluno);

                    try (ResultSet rs = stmtAluno.executeQuery()) {

                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                    }
                }

                try (PreparedStatement stmtVinculo = conn.prepareStatement(sqlVinculo)) {

                    stmtVinculo.setLong(1, idAluno);
                    stmtVinculo.setLong(2, idTurma);

                    try (ResultSet rs = stmtVinculo.executeQuery()) {

                        if (rs.next()) {
                            conn.rollback();
                            return false;
                        }
                    }
                }

                try (PreparedStatement stmtAtualizar = conn.prepareStatement(sqlAtualizarTurma)) {

                    stmtAtualizar.setLong(1, idAluno);
                    stmtAtualizar.setLong(2, idAluno);
                    stmtAtualizar.setLong(3, idTurma);

                    if (stmtAtualizar.executeUpdate() == 0) {
                        throw new SQLException("Não foi possível atualizar a turma.");
                    }
                }

                try (PreparedStatement stmtAdicionar = conn.prepareStatement(sqlAdicionarAluno)) {

                    stmtAdicionar.setLong(1, idAluno);
                    stmtAdicionar.setLong(2, idTurma);
                    stmtAdicionar.setString(3, "verde");

                    if (stmtAdicionar.executeUpdate() == 0) {
                        throw new SQLException("Não foi possível adicionar o aluno à turma.");
                    }
                }

                conn.commit();
                return true;

            } catch (SQLException e) {

                conn.rollback();
                throw e;
            }
        }
    }

    // remove um aluno da turma
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

        try (Connection conn = dataSource.getConnection()) {

            conn.setAutoCommit(false);

            try {

                try (PreparedStatement stmtVerificar = conn.prepareStatement(sqlVerificarVinculo)) {

                    stmtVerificar.setLong(1, idAluno);
                    stmtVerificar.setLong(2, idTurma);

                    try (ResultSet rs = stmtVerificar.executeQuery()) {

                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                    }
                }

                try (PreparedStatement stmtRemover = conn.prepareStatement(sqlRemoverVinculo)) {

                    stmtRemover.setLong(1, idAluno);
                    stmtRemover.setLong(2, idTurma);

                    if (stmtRemover.executeUpdate() == 0) {
                        throw new SQLException("Não foi possível remover o aluno da turma.");
                    }
                }

                try (PreparedStatement stmtAtualizar = conn.prepareStatement(sqlAtualizarTurma)) {

                    stmtAtualizar.setLong(1, idAluno);
                    stmtAtualizar.setLong(2, idTurma);

                    if (stmtAtualizar.executeUpdate() == 0) {
                        throw new SQLException("Não foi possível atualizar os alunos da turma.");
                    }
                }

                conn.commit();
                return true;

            } catch (SQLException e) {

                conn.rollback();
                throw e;
            }
        }
    }

    // busca os professores vinculados a uma turma
    public List<usuarioAdminDTO> listarProfessoresDaTurma(Long idTurma) throws SQLException {

        List<usuarioAdminDTO> professores = new ArrayList<>();

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

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setLong(1, idTurma);

            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    professores.add(montarProfessor(rs));
                }
            }
        }

        return professores;
    }

    // busca professores ativos que ainda nao estao na turma
    public List<usuarioAdminDTO> listarProfessoresDisponiveis(Long idTurma) throws SQLException {

        List<usuarioAdminDTO> professores = new ArrayList<>();

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

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setLong(1, idTurma);
            stmt.setLong(2, idTurma);

            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    professores.add(montarProfessor(rs));
                }
            }
        }

        return professores;
    }

    // adiciona um professor na turma
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

        try (Connection conn = dataSource.getConnection()) {

            conn.setAutoCommit(false);

            try {

                Long idProprietario;

                try (PreparedStatement stmtTurma = conn.prepareStatement(sqlTurma)) {

                    stmtTurma.setLong(1, idTurma);

                    try (ResultSet rs = stmtTurma.executeQuery()) {

                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }

                        idProprietario = rs.getObject("id_proprietario", Long.class);
                    }
                }

                if (idProprietario != null && idProprietario.equals(idProfessor)) {
                    conn.rollback();
                    return false;
                }

                try (PreparedStatement stmtProfessor = conn.prepareStatement(sqlProfessor)) {

                    stmtProfessor.setLong(1, idProfessor);

                    try (ResultSet rs = stmtProfessor.executeQuery()) {

                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                    }
                }

                try (PreparedStatement stmtVinculo = conn.prepareStatement(sqlVinculo)) {

                    stmtVinculo.setLong(1, idProfessor);
                    stmtVinculo.setLong(2, idTurma);

                    try (ResultSet rs = stmtVinculo.executeQuery()) {

                        if (rs.next()) {
                            conn.rollback();
                            return false;
                        }
                    }
                }

                try (PreparedStatement stmtAtualizar = conn.prepareStatement(sqlAtualizarTurma)) {

                    stmtAtualizar.setLong(1, idProfessor);
                    stmtAtualizar.setLong(2, idProfessor);
                    stmtAtualizar.setLong(3, idTurma);

                    if (stmtAtualizar.executeUpdate() == 0) {
                        throw new SQLException("Não foi possível atualizar os professores da turma.");
                    }
                }

                try (PreparedStatement stmtAdicionar = conn.prepareStatement(sqlAdicionarProfessor)) {

                    stmtAdicionar.setLong(1, idProfessor);
                    stmtAdicionar.setLong(2, idTurma);

                    if (stmtAdicionar.executeUpdate() == 0) {
                        throw new SQLException("Não foi possível adicionar o professor à turma.");
                    }
                }

                conn.commit();
                return true;

            } catch (SQLException e) {

                conn.rollback();
                throw e;
            }
        }
    }

    // remove um professor da turma
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

        try (Connection conn = dataSource.getConnection()) {

            conn.setAutoCommit(false);

            try {

                Long idProprietario;

                // confirma se a turma existe e busca o responsavel
                try (PreparedStatement stmtTurma = conn.prepareStatement(sqlTurma)) {

                    stmtTurma.setLong(1, idTurma);

                    try (ResultSet rs = stmtTurma.executeQuery()) {

                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }

                        idProprietario = rs.getObject("id_proprietario", Long.class);
                    }
                }

                // o professor responsavel nao pode ser removido
                if (idProprietario != null && idProprietario.equals(idProfessor)) {
                    conn.rollback();
                    return false;
                }

                // confirma se o professor pertence a turma
                try (PreparedStatement stmtVerificar = conn.prepareStatement(sqlVerificarVinculo)) {

                    stmtVerificar.setLong(1, idProfessor);
                    stmtVerificar.setLong(2, idTurma);

                    try (ResultSet rs = stmtVerificar.executeQuery()) {

                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                    }
                }

                // remove o relacionamento entre professor e turma
                try (PreparedStatement stmtRemover = conn.prepareStatement(sqlRemoverVinculo)) {

                    stmtRemover.setLong(1, idProfessor);
                    stmtRemover.setLong(2, idTurma);

                    if (stmtRemover.executeUpdate() == 0) {
                        throw new SQLException("Não foi possível remover o professor da turma.");
                    }
                }

                // remove o professor do array usado pelas telas atuais
                try (PreparedStatement stmtAtualizar = conn.prepareStatement(sqlAtualizarTurma)) {

                    stmtAtualizar.setLong(1, idProfessor);
                    stmtAtualizar.setLong(2, idTurma);

                    if (stmtAtualizar.executeUpdate() == 0) {
                        throw new SQLException("Não foi possível atualizar os professores da turma.");
                    }
                }

                conn.commit();
                return true;

            } catch (SQLException e) {

                conn.rollback();
                throw e;
            }
        }
    }

    // monta os dados do aluno usados nas telas do administrador
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
}