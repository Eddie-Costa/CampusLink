package com.example.CampusLink.dao;

import com.example.CampusLink.dto.DenunciaDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Repository
public class denunciaDAO {

    @Autowired
    private DataSource dataSource;

    public void inserir(DenunciaDTO denuncia) throws SQLException {

        String sql = """
                INSERT INTO public."DENUNCIAS"
                ( "id_conteudo", "id_aluno", "id_professor", "motivo")
                VALUES (?, ?, ?, ?)
                RETURNING "id", "status", "created_at"
                """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, denuncia.getIdConteudo());
            stmt.setLong(2, denuncia.getIdAluno());
            stmt.setLong(3, denuncia.getIdProfessor());
            stmt.setString(4, denuncia.getMotivo());

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {

                    denuncia.setId(rs.getLong("id"));
                    denuncia.setStatus(rs.getString("status"));
                    Timestamp createdAt = rs.getTimestamp("created_at");

                    if (createdAt != null) {
                        denuncia.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
                    }
                }
            }
        }
    }

    public boolean alunoJaDenunciou(Long idConteudo, Long idAluno) throws SQLException {

        String sql = """
                SELECT COUNT(*) AS quantidade
                FROM public."DENUNCIAS"
                WHERE "id_conteudo" = ?
                AND "id_aluno" = ?
                """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idConteudo);
            stmt.setLong(2, idAluno);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    return rs.getInt("quantidade") > 0;
                }
            }
        }

        return false;
    }

    public int contarDenunciasPendentes(Long idConteudo) throws SQLException {

        String sql = """
                SELECT COUNT(DISTINCT "id_aluno") AS quantidade
                FROM public."DENUNCIAS"
                WHERE "id_conteudo" = ?
                AND "status" = 'pendente'
                """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idConteudo);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    return rs.getInt("quantidade");
                }
            }
        }

        return 0;
    }

    public int contarAlunosDaTurma(
            Long idTurma
    ) throws SQLException {

        String sql = """
                SELECT COUNT(DISTINCT "id_aluno") AS quantidade
                FROM public."ALUNO_TURMA"
                WHERE "id_turma" = ?
                """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idTurma);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    return rs.getInt("quantidade");
                }
            }
        }

        return 0;
    }

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

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idConteudo);

            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    alunos.add(rs.getString("nome"));
                }
            }
        }

        return alunos;
    }

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

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idConteudo);

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

                    if (createdAt != null) {
                        denuncia.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
                    }

                    denuncias.add(denuncia);
                }
            }
        }

        return denuncias;
    }

    public void atualizarStatusPorConteudo(Long idConteudo, String status) throws SQLException {

        String sql = """
                UPDATE public."DENUNCIAS"
                SET "status" = ?
                WHERE "id_conteudo" = ?
                AND "status" = 'pendente'
                """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setLong(2, idConteudo);
            stmt.executeUpdate();
        }
    }
}