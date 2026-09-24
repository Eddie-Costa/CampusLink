package com.example.CampusLink.dao;

import com.example.CampusLink.dto.Admin.usuarioAdminDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class AdminUsuarioDAO {

    @Autowired
    private DataSource dataSource;

    public List<usuarioAdminDTO> listarUsuarios() throws SQLException {

        List<usuarioAdminDTO> usuarios = new ArrayList<>();

        String sql = """
                SELECT
                    u.id AS id_usuario,
                    a.id AS id_aluno,
                    p.id AS id_professor,
                    a.rgm,
                    p.matricula,
                    u.nome,
                    u.email,
                    u.telefone,
                    u.datanasc,
                    u.status
                FROM public."USUARIOS" u
                LEFT JOIN public."ALUNOS" a ON a.id_usuario = u.id
                LEFT JOIN public."PROFESSORES" p ON p.id_usuario = u.id
                WHERE a.id IS NOT NULL OR p.id IS NOT NULL
                ORDER BY u.nome
                """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()
        ) {

            while (rs.next()) {
                usuarioAdminDTO usuario = montarUsuario(rs);
                usuarios.add(usuario);
            }
        }

        return usuarios;
    }

    public usuarioAdminDTO buscarUsuarioPorId(Long idUsuario) throws SQLException {

        String sql = """
                SELECT
                    u.id AS id_usuario,
                    a.id AS id_aluno,
                    p.id AS id_professor,
                    a.rgm,
                    p.matricula,
                    u.nome,
                    u.email,
                    u.telefone,
                    u.datanasc,
                    u.status
                FROM public."USUARIOS" u
                LEFT JOIN public."ALUNOS" a ON a.id_usuario = u.id
                LEFT JOIN public."PROFESSORES" p ON p.id_usuario = u.id
                WHERE u.id = ?
                AND (a.id IS NOT NULL OR p.id IS NOT NULL)
                """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setLong(1, idUsuario);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    return montarUsuario(rs);
                }
            }
        }

        return null;
    }

    public List<String> validarDadosEdicao(Long idUsuario, String perfil, String identificador, String email, String telefone) throws SQLException {

        List<String> erros = new ArrayList<>();

        if (emailJaExiste(idUsuario, email)) {
            erros.add("E-mail já cadastrado para outro usuário.");
        }

        if (telefoneJaExiste(idUsuario, telefone)) {
            erros.add("Telefone já cadastrado para outro usuário.");
        }

        if (identificadorJaExiste(idUsuario, perfil, identificador)) {
            if ("Aluno".equalsIgnoreCase(perfil)) {
                erros.add("RGM já cadastrado para outro aluno.");
            } else {
                erros.add("Matrícula já cadastrada para outro professor.");
            }
        }

        return erros;
    }

    public boolean atualizarUsuario(usuarioAdminDTO usuario) throws SQLException {

        String sqlUsuario = """
                UPDATE public."USUARIOS"
                SET nome = ?,
                    email = ?,
                    telefone = ?,
                    datanasc = ?
                WHERE id = ?
                """;

        String sqlPerfil;

        if ("Aluno".equalsIgnoreCase(usuario.getPerfil())) {
            sqlPerfil = """
                    UPDATE public."ALUNOS"
                    SET rgm = ?
                    WHERE id_usuario = ?
                    """;

        } else if ("Professor".equalsIgnoreCase(usuario.getPerfil())) {
            sqlPerfil = """
                    UPDATE public."PROFESSORES"
                    SET matricula = ?
                    WHERE id_usuario = ?
                    """;

        } else {
            return false;
        }

        try (Connection conn = dataSource.getConnection()) {

            conn.setAutoCommit(false);

            try {
                // atualiza primeiro o rgm ou a matricula
                try (PreparedStatement stmtPerfil = conn.prepareStatement(sqlPerfil)) {
                    stmtPerfil.setString(1, usuario.getIdentificador());
                    stmtPerfil.setLong(2, usuario.getIdUsuario());

                    int perfilAlterado = stmtPerfil.executeUpdate();

                    if (perfilAlterado == 0) {
                        conn.rollback();
                        return false;
                    }
                }

                // atualiza os dados principais do usuario
                try (PreparedStatement stmtUsuario = conn.prepareStatement(sqlUsuario)) {
                    stmtUsuario.setString(1, usuario.getNome());
                    stmtUsuario.setString(2, normalizarEmail(usuario.getEmail()));
                    stmtUsuario.setString(3, usuario.getTelefone());
                    stmtUsuario.setDate(4, Date.valueOf(usuario.getDataNasc()));
                    stmtUsuario.setLong(5, usuario.getIdUsuario());

                    int usuarioAlterado = stmtUsuario.executeUpdate();

                    if (usuarioAlterado == 0) {
                        conn.rollback();
                        return false;
                    }
                }

                conn.commit();
                return true;

            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public boolean atualizarStatusUsuario(Long idUsuario, boolean status) throws SQLException {

        String sql = """
                UPDATE public."USUARIOS" u
                SET status = ?
                WHERE u.id = ?
                AND (
                    EXISTS (
                        SELECT 1
                        FROM public."ALUNOS" a
                        WHERE a.id_usuario = u.id
                    )
                    OR
                    EXISTS (
                        SELECT 1
                        FROM public."PROFESSORES" p
                        WHERE p.id_usuario = u.id
                    )
                )
                """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setBoolean(1, status);
            stmt.setLong(2, idUsuario);

            int linhasAlteradas = stmt.executeUpdate();

            return linhasAlteradas > 0;
        }
    }

    private boolean emailJaExiste(Long idUsuario, String email) throws SQLException {

        String sql = """
                SELECT 1
                FROM public."USUARIOS"
                WHERE LOWER(email) = ?
                AND id <> ?
                """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setString(1, normalizarEmail(email));
            stmt.setLong(2, idUsuario);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean telefoneJaExiste(Long idUsuario, String telefone) throws SQLException {

        String sql = """
                SELECT 1
                FROM public."USUARIOS"
                WHERE telefone = ?
                AND id <> ?
                """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setString(1, telefone);
            stmt.setLong(2, idUsuario);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean identificadorJaExiste(Long idUsuario, String perfil, String identificador) throws SQLException {

        String sql;

        if ("Aluno".equalsIgnoreCase(perfil)) {
            sql = """
                    SELECT 1
                    FROM public."ALUNOS"
                    WHERE rgm = ?
                    AND id_usuario <> ?
                    """;

        } else if ("Professor".equalsIgnoreCase(perfil)) {
            sql = """
                    SELECT 1
                    FROM public."PROFESSORES"
                    WHERE matricula = ?
                    AND id_usuario <> ?
                    """;

        } else {
            return true;
        }

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setString(1, identificador);
            stmt.setLong(2, idUsuario);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private usuarioAdminDTO montarUsuario(ResultSet rs) throws SQLException {

        usuarioAdminDTO usuario = new usuarioAdminDTO();

        usuario.setIdUsuario(rs.getLong("id_usuario"));
        usuario.setNome(rs.getString("nome"));
        usuario.setEmail(rs.getString("email"));
        usuario.setTelefone(rs.getString("telefone"));
        usuario.setStatus(rs.getBoolean("status"));

        Date dataNasc = rs.getDate("datanasc");

        if (dataNasc != null) {
            usuario.setDataNasc(dataNasc.toString());
        }

        Long idAluno = rs.getObject("id_aluno", Long.class);
        Long idProfessor = rs.getObject("id_professor", Long.class);

        if (idAluno != null) {
            usuario.setIdPerfil(idAluno);
            usuario.setIdentificador(rs.getString("rgm"));
            usuario.setPerfil("Aluno");

        } else if (idProfessor != null) {
            usuario.setIdPerfil(idProfessor);
            usuario.setIdentificador(rs.getString("matricula"));
            usuario.setPerfil("Professor");
        }

        return usuario;
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}