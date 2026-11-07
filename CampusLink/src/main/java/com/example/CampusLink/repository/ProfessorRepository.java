// banco de professores centraliza consultas de identidade e criacao de turmas
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// persistencia de professores esta classe consulta credenciais e grava turmas e participacoes
@Repository
public class ProfessorRepository {

    @Autowired
    private DataSource dataSource;

    // buscar credenciais do professor este metodo consulta email e senha no cadastro de usuarios
    public loginProfessorDTO buscarPorEmailProfessor(String email) throws SQLException {

        String sql = "SELECT * FROM public.\"USUARIOS\" WHERE \"email\" = ?";
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, email);
        ResultSet rs = stmt.executeQuery();

        // montar credenciais do professor usa email e senha quando a consulta encontra uma conta
        if (rs.next()) {
            loginProfessorDTO usuario = new loginProfessorDTO();
            usuario.setEmail(rs.getString("EMAIL"));
            usuario.setSenha(rs.getString("SENHA"));

            return usuario;
        }

        rs.close();
        stmt.close();
        conn.close();
        return null;
    }

    // buscar identificador do professor este metodo consulta o vinculo entre professor e email do usuario
    public String buscarPorIDProfessor(String email) throws SQLException {

        String  id = "";

        String sql = "SELECT p.id FROM public.\"PROFESSORES\" p\n" + "JOIN public.\"USUARIOS\" u ON p.id_usuario = u.id WHERE u.email = ?";
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, email);
        ResultSet rs = stmt.executeQuery();

        // recuperar codigo do professor guarda o identificador quando existe resultado
        if (rs.next()) {
            id = rs.getString("id");
        }

        rs.close();
        stmt.close();
        conn.close();
        return id;
    }

    // cadastrar turma do professor no banco este metodo grava nome descricao e responsavel
    public void InsertTurmasIntoBD(String nomeTurma, String descricao, String idProfessor) throws SQLException {

        String sql = "INSERT INTO public.\"TURMAS\" (\"nome_turma\", \"descricao\", \"id_proprietario\") VALUES (?, ?, ?)";

        // criar turma com conexao abre a gravacao e confirma as mudancas antes de fechar a conexao
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            // preparar gravacao da turma envia nome descricao e professor e fecha o comando ao terminar
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, nomeTurma);
                stmt.setString(2, descricao);
                stmt.setLong(3, Long.parseLong(idProfessor));

                // conferir turma inserida informa falha quando a gravacao nao criou nenhum registro
                if (stmt.executeUpdate() == 0) {
                    throw new SQLException("Nenhuma turma foi inserida.");
                }
            }

            conn.commit();
        } catch (SQLException e) {
            throw e;
        }
    }

    // cadastrar vinculo direto de professor este metodo grava a participacao do professor na turma
    public void InsertProfessor_TurmaIntoBD(String idProfessor, String idTurma) throws SQLException {

        String sql = "INSERT INTO public.\"PROFESSOR_TURMA\" (\"id_professor\", \"id_turma\") VALUES (?, ?)";

        // criar participacao do professor abre a gravacao e confirma o vinculo antes de fechar a conexao
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            // preparar vinculo do professor envia professor e turma e fecha o comando ao terminar
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, Long.parseLong(idProfessor));
                stmt.setLong(2, Long.parseLong(idTurma));

                // cadastrar professor na turma avisa quando nenhum vinculo foi inserido
                if (stmt.executeUpdate() == 0) {
                    throw new SQLException("Nenhum professor_turma foi inserido.");
                }

            }

            conn.commit();
        } catch (SQLException e) {
            throw e;
        }
    }

    // delete vinculo direto de professor este metodo apaga a relacao entre o professor e a turma
    public void removeProfessor_TurmaIntoBD(String idProfessor, String idTurma) throws SQLException {

        String sql = "DELETE FROM public.\"PROFESSOR_TURMA\" WHERE \"id_professor\" = ? AND \"id_turma\" = ?";

        // delete professor da turma tenta concluir a exclusao e confirma a transacao
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            // delete vinculo do professor executa a exclusao pelos codigos da turma e do professor
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, Long.parseLong(idProfessor));
                stmt.setLong(2, Long.parseLong(idTurma));

                stmt.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            throw e;
        }
    }
}
