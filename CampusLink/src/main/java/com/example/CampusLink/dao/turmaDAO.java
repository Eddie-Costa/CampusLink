package com.example.CampusLink.dao;

import com.example.CampusLink.dto.TurmaDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class turmaDAO {

    private static final Logger logger = LoggerFactory.getLogger(turmaDAO.class);

    @Autowired
    private DataSource dataSource;

    public String buscarPorIDTurma(String idProfessor) throws SQLException {

        String id = "";
        String sql = "SELECT t.id FROM public.\"TURMAS\" t WHERE (? = ANY(COALESCE(t.id_professores, ARRAY[]::bigint[])) OR t.id_proprietario = ?) LIMIT 1";

        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setLong(1, Long.parseLong(idProfessor));
        stmt.setLong(2, Long.parseLong(idProfessor));
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            id = rs.getString("id");
        }

        rs.close();
        stmt.close();
        conn.close();
        return id;
    }

    public String buscarUltimaTurmaPorProfessor(String idProfessor) throws SQLException {
        String sql = "SELECT t.id FROM public.\"TURMAS\" t WHERE (? = ANY(COALESCE(t.id_professores, ARRAY[]::bigint[])) OR t.id_proprietario = ?) ORDER BY t.id DESC LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, Long.parseLong(idProfessor));
            stmt.setLong(2, Long.parseLong(idProfessor));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("id");
                }
            }
        }

        return null;
    }

    public List<TurmaDTO> buscarTurmasDoUsuario(String tipo, String id) throws SQLException {

        String sql;

        if (tipo.equalsIgnoreCase("aluno")) {
            sql = "SELECT t.id, t.nome_turma, t.descricao FROM public.\"TURMAS\" t WHERE ? = ANY(t.id_alunos)";
            List<TurmaDTO> turmas = new ArrayList<>();

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setLong(1, Long.parseLong(id));

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        TurmaDTO turma = new TurmaDTO();
                        turma.setId(rs.getLong("id"));
                        turma.setNomeTurma(rs.getString("nome_turma"));
                        turma.setDescricao(rs.getString("descricao"));
                        turmas.add(turma);
                    }
                }
            }

            return turmas;
        } else if (tipo.equalsIgnoreCase("professor")) {
            sql = "SELECT t.id, t.nome_turma, t.descricao FROM public.\"TURMAS\" t WHERE (? = ANY(COALESCE(t.id_professores, ARRAY[]::bigint[])) OR t.id_proprietario = ?)";

            List<TurmaDTO> turmas = new ArrayList<>();

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setLong(1, Long.parseLong(id));
                stmt.setLong(2, Long.parseLong(id));

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        TurmaDTO turma = new TurmaDTO();
                        turma.setId(rs.getLong("id"));
                        turma.setNomeTurma(rs.getString("nome_turma"));
                        turma.setDescricao(rs.getString("descricao"));
                        turmas.add(turma);
                    }
                }
            }

            return turmas;
        } else {
            return List.of();
        }
    }

    public TurmaDTO buscarTurmaPorId(String id) throws SQLException {
        String sql = "SELECT t.id, t.nome_turma, t.descricao, t.id_proprietario FROM public.\"TURMAS\" t WHERE t.id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, Long.parseLong(id));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    TurmaDTO turma = new TurmaDTO();
                    turma.setId(rs.getLong("id"));
                    turma.setNomeTurma(rs.getString("nome_turma"));
                    turma.setDescricao(rs.getString("descricao"));
                    turma.setIdProprietario(rs.getLong("id_proprietario"));
                    return turma;
                }
            }
        }

        return null;
    }

    public List<TurmaDTO> buscarParticipantesDaTurma(String idTurma) throws SQLException {
        String sql = "(SELECT u.email AS email, 'Aluno' AS tipo_usuario FROM public.\"ALUNO_TURMA\" at " +
                "JOIN public.\"ALUNOS\" a ON a.id = at.id_aluno " +
                "JOIN public.\"USUARIOS\" u ON u.id = a.id_usuario " +
                "WHERE at.id_turma = ?) " +
                "UNION ALL " +
                "(SELECT u.email AS email, CASE WHEN t.id_proprietario = pt.id_professor THEN 'Professor Admin' ELSE 'Professor' END AS tipo_usuario FROM public.\"PROFESSOR_TURMA\" pt " +
                "JOIN public.\"PROFESSORES\" p ON p.id = pt.id_professor " +
                "JOIN public.\"USUARIOS\" u ON u.id = p.id_usuario " +
                "JOIN public.\"TURMAS\" t ON t.id = pt.id_turma " +
                "WHERE pt.id_turma = ?) " +
                "ORDER BY tipo_usuario, email";

        List<TurmaDTO> participantes = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, Long.parseLong(idTurma));
            stmt.setLong(2, Long.parseLong(idTurma));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    TurmaDTO participante = new TurmaDTO();
                    participante.setEmailPessoa(rs.getString("email"));
                    participante.setTipoUsuario(rs.getString("tipo_usuario"));
                    participantes.add(participante);
                }
            }
        }

        return participantes;
    }

    public void excluirTurma(String idTurma, String idProfessor) throws SQLException {
        String sqlVerificarProprietario = "SELECT 1 FROM public.\"TURMAS\" WHERE id = ? AND id_proprietario = ?";
        String sqlDeleteAlunoTurma = "DELETE FROM public.\"ALUNO_TURMA\" WHERE id_turma = ?";
        String sqlDeleteProfessorTurma = "DELETE FROM public.\"PROFESSOR_TURMA\" WHERE id_turma = ?";
        String sqlDeleteTurma = "DELETE FROM public.\"TURMAS\" WHERE id = ?";

        try (Connection conn = dataSource.getConnection()) {

            try (PreparedStatement stmtVerificar = conn.prepareStatement(sqlVerificarProprietario)) {
                stmtVerificar.setLong(1, Long.parseLong(idTurma));
                stmtVerificar.setLong(2, Long.parseLong(idProfessor));

                try (ResultSet rs = stmtVerificar.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Você não é o proprietário desta turma.");
                    }
                }
            }

            try (PreparedStatement stmtAlunoTurma = conn.prepareStatement(sqlDeleteAlunoTurma)) {
                stmtAlunoTurma.setLong(1, Long.parseLong(idTurma));
                stmtAlunoTurma.executeUpdate();
            }

            try (PreparedStatement stmtProfessorTurma = conn.prepareStatement(sqlDeleteProfessorTurma)) {
                stmtProfessorTurma.setLong(1, Long.parseLong(idTurma));
                stmtProfessorTurma.executeUpdate();
            }

            try (PreparedStatement stmtTurma = conn.prepareStatement(sqlDeleteTurma)) {
                stmtTurma.setLong(1, Long.parseLong(idTurma));

                if (stmtTurma.executeUpdate() == 0) {
                    throw new SQLException("Nenhuma turma foi removida.");
                }
            }
        }
    }

    public boolean inserirPessoaTurma(String email, String idTurma) throws SQLException {
        String sqlUsuario = "SELECT u.id, u.perfil FROM public.\"USUARIOS\" u WHERE u.email = ?";
        String sqlAluno = "SELECT a.id FROM public.\"ALUNOS\" a WHERE a.id_usuario = ?";
        String sqlProfessor = "SELECT p.id FROM public.\"PROFESSORES\" p WHERE p.id_usuario = ?";
        String sqlUpdateAluno = "UPDATE public.\"TURMAS\" SET \"id_alunos\" = array_append(COALESCE(\"id_alunos\", ARRAY[]::bigint[]), ?) WHERE id = ?";
        String sqlUpdateProfessor = "UPDATE public.\"TURMAS\" SET \"id_professores\" = array_append(COALESCE(\"id_professores\", ARRAY[]::bigint[]), ?) WHERE id = ?";
        String sqlInsertAlunoTurma = "INSERT INTO public.\"ALUNO_TURMA\" (\"id_aluno\", \"id_turma\", \"situacao_aluno\") VALUES (?, ?, ?)";
        String sqlInsertProfessorTurma = "INSERT INTO public.\"PROFESSOR_TURMA\" (\"id_professor\", \"id_turma\") VALUES (?, ?)";
        String sqlBuscarCorrespondencia = "SELECT 1 FROM public.\"TURMAS\" t WHERE t.id = ? AND (? = ANY(COALESCE(t.id_alunos, ARRAY[]::bigint[])) OR ? = ANY(COALESCE(t.id_professores, ARRAY[]::bigint[])))";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmtUsuario = conn.prepareStatement(sqlUsuario)) {

            stmtUsuario.setString(1, email);

            try (ResultSet rsUsuario = stmtUsuario.executeQuery()) {
                if (!rsUsuario.next()) {
                    logger.warn("[turmaDAO.inserirPessoaTurma] Usuário não encontrado para o email informado.");
                    return false;
                }

                long usuarioId = rsUsuario.getLong("id");
                String perfil = rsUsuario.getString("perfil");

                String sqlBuscaEntidade = "";
                String sqlUpdate = "";
                String sqlInsertRelacao = "";

                if (perfil.equalsIgnoreCase("alunos")) {
                    sqlBuscaEntidade = sqlAluno;
                    sqlUpdate = sqlUpdateAluno;
                    sqlInsertRelacao = sqlInsertAlunoTurma;
                } else if (perfil.equalsIgnoreCase("professores")) {
                    sqlBuscaEntidade = sqlProfessor;
                    sqlUpdate = sqlUpdateProfessor;
                    sqlInsertRelacao = sqlInsertProfessorTurma;
                } else {
                    logger.warn("[turmaDAO.inserirPessoaTurma] Perfil não suportado: {}", perfil);
                    return false;
                }

                long entidadeId = -1;

                try (PreparedStatement stmtEntidade = conn.prepareStatement(sqlBuscaEntidade)) {
                    stmtEntidade.setLong(1, usuarioId);

                    try (ResultSet rsEntidade = stmtEntidade.executeQuery()) {
                        if (rsEntidade.next()) {
                            entidadeId = rsEntidade.getLong("id");
                        } else {
                            logger.warn("[turmaDAO.inserirPessoaTurma] Nenhuma entidade encontrada na tabela específica para o idUsuario={}", usuarioId);
                            return false;
                        }
                    }
                }

                try (PreparedStatement stmtCorrespondente = conn.prepareStatement(sqlBuscarCorrespondencia)) {
                    stmtCorrespondente.setLong(1, Long.parseLong(idTurma));
                    stmtCorrespondente.setLong(2, entidadeId);
                    stmtCorrespondente.setLong(3, entidadeId);

                    try (ResultSet rsCorrespondente = stmtCorrespondente.executeQuery()) {
                        if (rsCorrespondente.next()) {
                            logger.warn("[turmaDAO.inserirPessoaTurma] Este usuário já está cadastrado na turma={}", usuarioId);
                            return false;
                        }
                    }
                }

                try (PreparedStatement stmtUpdate = conn.prepareStatement(sqlUpdate)) {
                    stmtUpdate.setLong(1, entidadeId);
                    stmtUpdate.setLong(2, Long.parseLong(idTurma));

                    int linhasAfetadas = stmtUpdate.executeUpdate();

                    if (linhasAfetadas <= 0) {
                        logger.warn("[turmaDAO.inserirPessoaTurma] nenhuma linha foi afetada na turma {}.", idTurma);
                        return false;
                    }
                }

                try (PreparedStatement stmtInsertRelacao = conn.prepareStatement(sqlInsertRelacao)) {
                    stmtInsertRelacao.setLong(1, entidadeId);
                    stmtInsertRelacao.setLong(2, Long.parseLong(idTurma));

                    if (sqlInsertRelacao.equals(sqlInsertAlunoTurma)) {
                        stmtInsertRelacao.setString(3, "verde");
                    }

                    stmtInsertRelacao.executeUpdate();
                }

                return true;
            }
        }
    }

    public boolean revomerPessoaTurma(String email, String idTurma) throws SQLException {
        String sqlUsuario = "SELECT u.id, u.perfil FROM public.\"USUARIOS\" u WHERE u.email = ?";
        String sqlAluno = "SELECT a.id FROM public.\"ALUNOS\" a WHERE a.id_usuario = ?";
        String sqlProfessor = "SELECT p.id FROM public.\"PROFESSORES\" p WHERE p.id_usuario = ?";
        String sqlUpdateAluno = "UPDATE public.\"TURMAS\" SET \"id_alunos\" = array_remove(COALESCE(\"id_alunos\", ARRAY[]::bigint[]), ?) WHERE id = ?";
        String sqlUpdateProfessor = "UPDATE public.\"TURMAS\" SET \"id_professores\" = array_remove(COALESCE(\"id_professores\", ARRAY[]::bigint[]), ?) WHERE id = ?";
        String sqlDeleteAlunoTurma = "DELETE FROM public.\"ALUNO_TURMA\" WHERE \"id_aluno\" = ? AND \"id_turma\" = ?";
        String sqlDeleteProfessorTurma = "DELETE FROM public.\"PROFESSOR_TURMA\" WHERE \"id_professor\" = ? AND \"id_turma\" = ?";
        String sqlBuscarCorrespondencia = "SELECT 1 FROM public.\"TURMAS\" t WHERE t.id = ? AND (? = ANY(COALESCE(t.id_alunos, ARRAY[]::bigint[])) OR ? = ANY(COALESCE(t.id_professores, ARRAY[]::bigint[])))";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmtUsuario = conn.prepareStatement(sqlUsuario)) {

            stmtUsuario.setString(1, email);

            try (ResultSet rsUsuario = stmtUsuario.executeQuery()) {
                if (!rsUsuario.next()) {
                    logger.warn("[turmaDAO.inserirPessoaTurma] Usuário não encontrado para o email informado.");
                    return false;
                }

                long usuarioId = rsUsuario.getLong("id");
                String perfil = rsUsuario.getString("perfil");

                String sqlBuscaEntidade = "";
                String sqlUpdate = "";
                String sqlDeleteRelacao = "";

                if (perfil.equalsIgnoreCase("alunos")) {
                    sqlBuscaEntidade = sqlAluno;
                    sqlUpdate = sqlUpdateAluno;
                    sqlDeleteRelacao = sqlDeleteAlunoTurma;
                } else if (perfil.equalsIgnoreCase("professores")) {
                    sqlBuscaEntidade = sqlProfessor;
                    sqlUpdate = sqlUpdateProfessor;
                    sqlDeleteRelacao = sqlDeleteProfessorTurma;
                } else {
                    logger.warn("[turmaDAO.inserirPessoaTurma] Perfil não suportado: {}", perfil);
                    return false;
                }

                long entidadeId = -1;

                try (PreparedStatement stmtEntidade = conn.prepareStatement(sqlBuscaEntidade)) {
                    stmtEntidade.setLong(1, usuarioId);

                    try (ResultSet rsEntidade = stmtEntidade.executeQuery()) {
                        if (rsEntidade.next()) {
                            entidadeId = rsEntidade.getLong("id");
                        } else {
                            logger.warn("[turmaDAO.inserirPessoaTurma] Nenhuma entidade encontrada na tabela específica para o idUsuario={}", usuarioId);
                        }
                    }
                }

                if (entidadeId == -1) {
                    logger.warn("[turmaDAO.inserirPessoaTurma] Encerrando porque entidadeId não foi encontrado.");
                    return false;
                }

                try (PreparedStatement stmtCorrespondente = conn.prepareStatement(sqlBuscarCorrespondencia)) {
                    stmtCorrespondente.setLong(1, Long.parseLong(idTurma));
                    stmtCorrespondente.setLong(2, entidadeId);
                    stmtCorrespondente.setLong(3, entidadeId);

                    try (ResultSet rsCorrespondente = stmtCorrespondente.executeQuery()) {
                        if (!rsCorrespondente.next()) {
                            logger.warn("[turmaDAO.revomerPessoaTurma] Usuário {} não está cadastrado na turma {}.", entidadeId, idTurma);
                            return false;
                        }
                    }
                }

                if (perfil.equalsIgnoreCase("professores")) {
                    String sqlBuscarProprietario = "SELECT t.id_proprietario FROM public.\"TURMAS\" t WHERE t.id = ?";

                    try (PreparedStatement stmtProprietario = conn.prepareStatement(sqlBuscarProprietario)) {
                        stmtProprietario.setLong(1, Long.parseLong(idTurma));

                        try (ResultSet rsProprietario = stmtProprietario.executeQuery()) {
                            if (rsProprietario.next()) {
                                long idProprietario = rsProprietario.getLong("id_proprietario");

                                if (entidadeId == idProprietario) {
                                    logger.warn("[turmaDAO.revomerPessoaTurma] Bloqueada a remoção do professor administrador da turma {}.", idTurma);
                                    return false;
                                }
                            }
                        }
                    }
                }

                try (PreparedStatement stmtUpdate = conn.prepareStatement(sqlUpdate)) {
                    stmtUpdate.setLong(1, entidadeId);
                    stmtUpdate.setLong(2, Long.parseLong(idTurma));

                    int linhasAfetadas = stmtUpdate.executeUpdate();

                    if (linhasAfetadas <= 0) {
                        logger.warn("[turmaDAO.inserirPessoaTurma] nenhuma linha foi afetada na turma {}.", idTurma);
                        return false;
                    }
                }

                try (PreparedStatement stmtDeleteRelacao = conn.prepareStatement(sqlDeleteRelacao)) {
                    stmtDeleteRelacao.setLong(1, entidadeId);
                    stmtDeleteRelacao.setLong(2, Long.parseLong(idTurma));

                    int linhasRelacaoAfetadas = stmtDeleteRelacao.executeUpdate();

                    if (linhasRelacaoAfetadas <= 0) {
                        logger.warn("[turmaDAO.revomerPessoaTurma] Nenhuma relação foi removida para entidade {} na turma {}.", entidadeId, idTurma);
                        return false;
                    }
                }

                return true;
            }
        }
    }
}
