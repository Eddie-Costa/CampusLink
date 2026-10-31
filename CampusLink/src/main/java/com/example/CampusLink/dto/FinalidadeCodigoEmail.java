package com.example.CampusLink.dto;

public enum FinalidadeCodigoEmail {
    ACESSO("Confirme seu acesso", "Use o código abaixo para entrar na sua conta."),
    CADASTRO("Confirme seu cadastro", "Use o código abaixo para concluir seu cadastro no CampusLink."),
    REDEFINICAO_SENHA("Redefina sua senha", "Use o código abaixo para continuar a redefinição da sua senha."),
    EXCLUSAO_CONTA("Confirme a exclusão da conta", "Use o código abaixo apenas se deseja excluir sua conta do CampusLink.");

    private final String titulo;
    private final String descricao;

    FinalidadeCodigoEmail(String titulo, String descricao) {
        this.titulo = titulo;
        this.descricao = descricao;
    }

    public String getTitulo() { return titulo; }
    public String getDescricao() { return descricao; }
}
