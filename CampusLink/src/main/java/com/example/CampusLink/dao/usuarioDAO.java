package com.example.CampusLink.dao;

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
public class usuarioDAO {

    @Autowired
    private DataSource dataSource;

    public void InsertCadastroUsuarioIntoBD(String Tipo, String IDENTIFICADOR, String NOME, String EMAIL, String TELEFONE, String DATANASC, String SENHA) throws SQLException {
        String sql = "";

        if(Tipo.equalsIgnoreCase("Aluno")){
            sql = "INSERT INTO public.\"Alunos\" (\"rgm\", \"nome\", \"email\", \"telefone\", \"dataNasc\", \"senha\") VALUES (?, ?, ?, ?, ?, ?)";
        } else if (Tipo.equalsIgnoreCase("Professor")) {
            sql = "INSERT INTO public.\"Professores\" (\"matricula\", \"nome\", \"email\", \"telefone\", \"dataNasc\", \"senha\") VALUES (?, ?, ?, ?, ?, ?)";
        }


        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, IDENTIFICADOR);
            stmt.setString(2, NOME);
            stmt.setString(3, EMAIL);
            stmt.setString(4, TELEFONE);
            stmt.setDate(5, java.sql.Date.valueOf(java.time.LocalDate.parse(DATANASC)));
            stmt.setString(6, SENHA);

            int linhas = stmt.executeUpdate();
            if (linhas == 0) {
                throw new SQLException("Nenhuma linha foi inserida na tabela de alunos.");
            }
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
        String resultado = "";

        //define o tipo de usuario
        if(tipoUsuario.equalsIgnoreCase("Aluno")){
            sql = "SELECT * FROM public.\"Alunos\"";
        } else if (tipoUsuario.equalsIgnoreCase("Professor")) {
            sql = "SELECT * FROM public.\"Professores\"";
        }

        //define o tipo de dado passado na query
        switch (tipoDado) {
            case "identificador":
                if(tipoUsuario.equalsIgnoreCase("Aluno")){
                    sql +=  " WHERE rgm = ? ";
                } else if(tipoUsuario.equalsIgnoreCase("Professor")){
                    sql +=  " WHERE matricula = ? ";
                }
                break;
            case "email":
                sql +=  " WHERE email = ? ";
                break;
            case "telefone":
                sql +=  " WHERE telefone = ? ";
                break;
        }

        //executa a query
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, dado);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                existe = true;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        //se existe retorno ja existe o dado
        if (existe) {
            return true;
        } else  {
            return false;
        }
    }

//    public String QueryLoginUsuario(String EMAIL) throws SQLException {
//        String resultado = "";
//
//        // conexão
//        Connection conn = dataSource.getConnection();
//
//        // SQL
//        String sql = "SELECT \"SENHA\" FROM pessoas WHERE \"EMAIL\" = ?";
//
//        // preparar
//        PreparedStatement stmt = conn.prepareStatement(sql);
//        stmt.setString(1, EMAIL);
//
//        //Realizar Querys
//        ResultSet rs = stmt.executeQuery();
//
//        if (rs.next()) {
//            resultado = rs.getString("SENHA");
//        }
//
//        // fechar
//
//        rs.close();
//        stmt.close();
//        conn.close();
//
//        return resultado;
//    }
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
//    public LoginDTO buscarPorEmail(String email) throws SQLException {
//
//        String sql = "SELECT * FROM pessoas WHERE \"EMAIL\" = ?";
//        Connection conn = dataSource.getConnection();
//        PreparedStatement stmt = conn.prepareStatement(sql);
//        stmt.setString(1, email);
//        ResultSet rs = stmt.executeQuery();
//
//        if (rs.next()) {
//            LoginDTO usuario = new LoginDTO();
//            usuario.setEmail(rs.getString("EMAIL"));
//            usuario.setSenha(rs.getString("SENHA"));
//
//            return usuario;
//        }
//
//        rs.close();
//        stmt.close();
//        conn.close();
//        return null;
//    }
}
