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
        String sql = "SELECT t.id FROM public.\"TURMAS\" t WHERE ? = ANY(t.id_professores)";

        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setLong(1, Long.parseLong(idProfessor));
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
        String sql = "SELECT t.id FROM public.\"TURMAS\" t WHERE ? = ANY(t.id_professores) ORDER BY t.id DESC LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, Long.parseLong(idProfessor));

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
        } else if (tipo.equalsIgnoreCase("professor")) {
            sql = "SELECT t.id, t.nome_turma, t.descricao FROM public.\"TURMAS\" t WHERE ? = ANY(t.id_professores)";
        } else {
            return List.of();
        }

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
    }

    public TurmaDTO buscarTurmaPorId(String id) throws SQLException {
        String sql = "SELECT t.id, t.nome_turma, t.descricao FROM public.\"TURMAS\" t WHERE t.id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, Long.parseLong(id));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    TurmaDTO turma = new TurmaDTO();
                    turma.setId(rs.getLong("id"));
                    turma.setNomeTurma(rs.getString("nome_turma"));
                    turma.setDescricao(rs.getString("descricao"));
                    return turma;
                }
            }
        }

        return null;
    }

    public void inserirPessoaTurma(String email, String idTurma) throws SQLException {
        String sqlUsuario = "SELECT u.id, u.perfil FROM public.\"USUARIOS\" u WHERE u.email = ?";
        String sqlAluno = "SELECT a.id FROM public.\"ALUNOS\" a WHERE a.id_usuario = ?";
        String sqlProfessor = "SELECT p.id FROM public.\"PROFESSORES\" p WHERE p.id_usuario = ?";
        String sqlUpdateAluno = "UPDATE public.\"TURMAS\" SET \"id_alunos\" = array_append(COALESCE(\"id_alunos\", ARRAY[]::bigint[]), ?) WHERE id = ?";
        String sqlUpdateProfessor = "UPDATE public.\"TURMAS\" SET \"id_professores\" = array_append(COALESCE(\"id_professores\", ARRAY[]::bigint[]), ?) WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmtUsuario = conn.prepareStatement(sqlUsuario)) {

            stmtUsuario.setString(1, email);

            try (ResultSet rsUsuario = stmtUsuario.executeQuery()) {
                if (!rsUsuario.next()) {
                    logger.warn("[turmaDAO.inserirPessoaTurma] Usuário não encontrado para o email informado.");
                    return;
                }

                long usuarioId = rsUsuario.getLong("id");
                String perfil = rsUsuario.getString("perfil");

                String sqlBuscaEntidade = "";
                String sqlUpdate = "";

                if (perfil.equalsIgnoreCase("alunos")) {
                    sqlBuscaEntidade = sqlAluno;
                    sqlUpdate = sqlUpdateAluno;
                } else if (perfil.equalsIgnoreCase("professores")) {
                    sqlBuscaEntidade = sqlProfessor;
                    sqlUpdate = sqlUpdateProfessor;
                } else {
                    logger.warn("[turmaDAO.inserirPessoaTurma] Perfil não suportado: {}", perfil);
                    return;
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
                    return;
                }

                try (PreparedStatement stmtUpdate = conn.prepareStatement(sqlUpdate)) {
                    stmtUpdate.setLong(1, entidadeId);
                    stmtUpdate.setLong(2, Long.parseLong(idTurma));

                    int linhasAfetadas = stmtUpdate.executeUpdate();

                    if (linhasAfetadas <= 0) {
                        logger.warn("[turmaDAO.inserirPessoaTurma] nenhuma linha foi afetada na turma {}.", idTurma);
                    }
                }
            }
        }
    }
}
