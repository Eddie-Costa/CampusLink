package com.example.CampusLink.dto.Admin;

import lombok.Getter;
import lombok.Setter;

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
}