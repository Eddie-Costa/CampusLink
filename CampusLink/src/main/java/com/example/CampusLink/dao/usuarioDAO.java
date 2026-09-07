package com.example.CampusLink.dao;

import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class usuarioDAO {

    @Autowired
    private DataSource dataSource;

    public void InsertCadastroUsuarioIntoBD(String Tipo, String IDENTIFICADOR, String NOME, String EMAIL, String TELEFONE, String DATANASC, String SENHA) throws SQLException {
        String emailNormalizado = normalizarEmail(EMAIL);
        String tabelaEspecifica;
        String colunaIdentificador;

        if (Tipo.equalsIgnoreCase("Aluno")) {
            tabelaEspecifica = "ALUNOS";
            colunaIdentificador = "rgm";
        } else if (Tipo.equalsIgnoreCase("Professor")) {
            tabelaEspecifica = "PROFESSORES";
            colunaIdentificador = "matricula";
        } else {
            throw new IllegalArgumentException("Tipo de usuário inválido: " + Tipo);
        }

        String sqlUsuario = "INSERT INTO public.\"USUARIOS\" (\"nome\", \"email\", \"telefone\", \"datanasc\", \"senha\", \"perfil\", \"status\") " + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        String sqlEspecifico = "INSERT INTO public.\"" + tabelaEspecifica + "\" " + "(\"id_usuario\", \"" + colunaIdentificador + "\") VALUES (?, ?)";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(sqlUsuario, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, NOME);
            stmt.setString(2, emailNormalizado);
            stmt.setString(3, TELEFONE);
            stmt.setDate(4, java.sql.Date.valueOf(java.time.LocalDate.parse(DATANASC)));
            stmt.setString(5, SENHA);
            stmt.setString(6, tabelaEspecifica);
            stmt.setBoolean(7, true);

                if (stmt.executeUpdate() == 0) {
                    throw new SQLException("Nenhum usuário foi inserido.");
                }

                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (!generatedKeys.next()) {
                        throw new SQLException("Não foi possível obter o id do usuário inserido.");
                    }

                    long idUsuario = generatedKeys.getLong(1);
                    try (PreparedStatement stmt2 = conn.prepareStatement(sqlEspecifico)) {
                        stmt2.setLong(1, idUsuario);
                        stmt2.setString(2, IDENTIFICADOR);
                        stmt2.executeUpdate();
                    }
                }
            }

            conn.commit();
        } catch (SQLException e) {
            throw e;
        }
    }

    public List<String> validarDadosDuplicados(String tipoUsuario,String identificador, String email, String telefone) {
        List<String> erros = new ArrayList<>();

        if (verificarExistente(tipoUsuario, "identificador", identificador)) {
            erros.add("Identificador já cadastrado");
        }
        if (verificarExistente(tipoUsuario, "email", email)) {
            erros.add("Email já cadastrado");
        }
        if (verificarExistente(tipoUsuario, "telefone", telefone)) {
            erros.add("Telefone já cadastrado");
        }

        return erros;
    }

    private boolean verificarExistente(String tipoUsuario, String tipoDado, String dado) {
        boolean existe = false;
        String sql = "";

        switch (tipoDado) {
            case "identificador":
                String tabela = tipoUsuario.equalsIgnoreCase("Aluno") ? "ALUNOS" : "PROFESSORES";
                String coluna = tipoUsuario.equalsIgnoreCase("Aluno") ? "rgm" : "matricula";

                sql = "SELECT 1 FROM public.\"USUARIOS\" u JOIN public.\"" + tabela + "\" p ON p.\"id_usuario\" = u.\"id\" " + "WHERE p.\"" + coluna + "\" = ?";
                break;
            case "email":
                sql = "SELECT 1 FROM public.\"USUARIOS\" WHERE LOWER(\"email\") = ?";
                break;
            case "telefone":
                sql = "SELECT 1 FROM public.\"USUARIOS\" WHERE \"telefone\" = ?";
                break;
            default:
                throw new IllegalArgumentException("Tipo de dado inválido: " + tipoDado);
        }

        //executa a query
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tipoDado.equals("email") ? normalizarEmail(dado) : dado);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                existe = true;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        //se existe retorno ja existe o dado
        return existe;
    }

    public String QueryLoginUsuario(String tipoUsuario, String EMAIL) throws SQLException {
        String emailNormalizado = normalizarEmail(EMAIL);
        String resultado = "";
        String sql = "";

        // conexão
        Connection conn = dataSource.getConnection();

        //define o tipo de usuario
        if(tipoUsuario.equalsIgnoreCase("Aluno")){
            sql = "SELECT u.\"senha\" FROM public.\"USUARIOS\" u " + "JOIN public.\"ALUNOS\" a ON a.\"id_usuario\" = u.\"id\" " + "WHERE LOWER(u.\"email\") = ?";
        } else if (tipoUsuario.equalsIgnoreCase("Professor")) {
            sql = "SELECT u.\"senha\" FROM public.\"USUARIOS\" u " + "JOIN public.\"PROFESSORES\" p ON p.\"id_usuario\" = u.\"id\" " + "WHERE LOWER(u.\"email\") = ?";
        }

        // preparar
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, emailNormalizado);

        //Realizar Querys
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            resultado = rs.getString("SENHA");
        }

        // fechar

        rs.close();
        stmt.close();
        conn.close();

        return resultado;
    }

//
//    public void DeleteUser(String EMAIL) throws SQLException {
//        // conexão
//        Connection conn = dataSource.getConnection();
//
//        // SQL
//        String sql = "DELETE FROM pessoas WHERE \"EMAIL\" = ?";
//
//        // preparar
//        PreparedStatement stmt = conn.prepareStatement(sql);
//        stmt.setString(1, EMAIL);
//
//        //Realizar Querys
//        int linhasAfetadas = stmt.executeUpdate();
//
//        if(linhasAfetadas > 0){
//            System.out.println("Usuário deletado");
//        } else {
//            System.out.println("Nenhum usuário encontrado com o email: " + EMAIL);
//        }
//
//
//        // fechar
//        stmt.close();
//        conn.close();
//    }
//
//    public ArrayList<String> QueryUserData(String EMAIL) throws SQLException {
//        // conexão
//        Connection conn = dataSource.getConnection();
//
//        // SQL
//        String sql = "SELECT \"NOME\", \"SOBRENOME\", \"EMAIL\", \"DT_REGISTER\" FROM pessoas WHERE \"EMAIL\" = ?";
//
//        // preparar
//        PreparedStatement stmt = conn.prepareStatement(sql);
//        stmt.setString(1, EMAIL);
//
//        //Realizar Querys
//        ResultSet rs = stmt.executeQuery();
//
//        ArrayList<String> Arrayresultado = new ArrayList<>();
//
//        if (rs.next()) {
//            Arrayresultado.add(rs.getString("NOME"));
//            Arrayresultado.add(rs.getString("SOBRENOME"));
//            Arrayresultado.add(rs.getString("EMAIL"));
//            Arrayresultado.add(rs.getString("DT_REGISTER"));
//        }
//
//        // fechar
//        rs.close();
//        stmt.close();
//        conn.close();
//
//        return Arrayresultado;
//    }
//
//    public void UpdateSenhaUsuario(String SENHA, String EMAIL) throws SQLException {
//        Connection conn = dataSource.getConnection();
//
//        String sql = "UPDATE pessoas SET \"SENHA\" = ? WHERE \"EMAIL\" = ?";
//
//        PreparedStatement stmt = conn.prepareStatement(sql);
//        stmt.setString(1, SENHA);
//        stmt.setString(2, EMAIL);
//
//        System.out.println(stmt);
//        int linhas = stmt.executeUpdate();
//        System.out.println("Linhas afetadas: " + linhas);
//
//        stmt.close();
//        conn.close();
//    }
    public loginAlunoDTO buscarPorEmailAluno(String email) throws SQLException {

        String sql = "SELECT u.\"email\", u.\"senha\" FROM public.\"USUARIOS\" u " + "JOIN public.\"ALUNOS\" a ON a.\"id_usuario\" = u.\"id\" " + "WHERE LOWER(u.\"email\") = ?";
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, normalizarEmail(email));
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            loginAlunoDTO usuario = new loginAlunoDTO();
            usuario.setEmail(rs.getString("EMAIL"));
            usuario.setSenha(rs.getString("SENHA"));

            return usuario;
        }

        rs.close();
        stmt.close();
        conn.close();
        return null;
    }

    public loginProfessorDTO buscarPorEmailProfessor(String email) throws SQLException {

        String sql = "SELECT u.\"email\", u.\"senha\" FROM public.\"USUARIOS\" u " + "JOIN public.\"PROFESSORES\" p ON p.\"id_usuario\" = u.\"id\" " + "WHERE LOWER(u.\"email\") = ?";
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, normalizarEmail(email));
        ResultSet rs = stmt.executeQuery();

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

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
