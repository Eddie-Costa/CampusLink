package com.example.CampusLink.exception;

import java.sql.SQLException;
import java.util.List;

public class SQLErrorHandler {

    public static void VerificarErro(String TipoUsuario,List<String> erros, org.springframework.ui.Model model){
        String mensagem = "";

        for (int i = 0; i<erros.size();i++) {
            if (erros.get(i).equals("Identificador já cadastrado")) {
                if(TipoUsuario.equals("aluno")) {
                    mensagem += "\n RGM já cadastrado.";
                } else if(TipoUsuario.equals("professor")) {
                    mensagem += "\n MATRICULA já cadastrada.";
                }
            }else if (erros.get(i).equals("Email já cadastrado")) {
                mensagem += "\n Email já cadastrado.";
            } else if (erros.get(i).equals("Telefone já cadastrado")) {
                mensagem += "\n Telefone já cadastrado.";
            } else {
                mensagem += "\n Verifique os dados e a conexão com o banco.";
            }
        }

        model.addAttribute("mensagemDeErro", "Não foi possível cadastrar." + mensagem);
    }
}
