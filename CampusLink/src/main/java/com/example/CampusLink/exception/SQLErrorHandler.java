// avisos de cadastro transforma conflitos do banco em orientacoes para o usuario
package com.example.CampusLink.exception;

import java.sql.SQLException;
import java.util.List;

// mensagens de duplicidade esta classe adapta os avisos de dados repetidos ao perfil informado
public class SQLErrorHandler {

    // mostrar erros de cadastro este metodo transforma dados repetidos em mensagens para o formulario
    public static void VerificarErro(String TipoUsuario,List<String> erros, org.springframework.ui.Model model){
        String mensagem = "";

        for (int i = 0; i<erros.size();i++) {
            // identificar cadastro repetido escolhe o aviso do identificador conforme o perfil
            if (erros.get(i).equals("Identificador já cadastrado")) {
                // avisar rgm repetido usa a mensagem de aluno para o identificador duplicado
                if(TipoUsuario.equals("aluno")) {
                    mensagem += "\n RGM já cadastrado.";
                }
                // avisar matricula repetida usa a mensagem de professor para o identificador duplicado
                else if(TipoUsuario.equals("professor")) {
                    mensagem += "\n MATRICULA já cadastrada.";
                }
            }
            // avisar email repetido acrescenta o conflito de email encontrado
            else if (erros.get(i).equals("Email já cadastrado")) {
                mensagem += "\n Email já cadastrado.";
            }
            // avisar telefone repetido distingue esse conflito de outras falhas de cadastro
            else if (erros.get(i).equals("Telefone já cadastrado")) {
                mensagem += "\n Telefone já cadastrado.";
            } else {
                mensagem += "\n Verifique os dados e a conexão com o banco.";
            }
        }

        model.addAttribute("mensagemDeErro", "Não foi possível cadastrar." + mensagem);
    }
}
