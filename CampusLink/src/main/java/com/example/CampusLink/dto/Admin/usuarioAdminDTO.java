// resumo administrativo de usuario organiza os dados de gerenciamento da conta
package com.example.CampusLink.dto.Admin;

import lombok.Getter;
import lombok.Setter;

// dados da conta na gestao esta classe guarda identificadores perfil situacao e permissao master
@Getter
@Setter
public class usuarioAdminDTO {

    private Long idUsuario;

    private Long idPerfil;

    private String identificador;

    private String nome;

    private String email;

    private String telefone;

    private String dataNasc;

    private String perfil;

    private boolean status;

    private boolean master;
}