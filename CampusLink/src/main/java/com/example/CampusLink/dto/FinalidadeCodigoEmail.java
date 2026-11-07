// finalidades de verificacao define os motivos de envio dos codigos
package com.example.CampusLink.dto;

// tipo de codigo esta enumeracao associa acesso cadastro recuperacao e exclusao aos textos do email
public enum FinalidadeCodigoEmail {
    ACESSO("Confirme seu acesso", "Use o código abaixo para entrar na sua conta."),
    CADASTRO("Confirme seu cadastro", "Use o código abaixo para concluir seu cadastro no CampusLink."),
    REDEFINICAO_SENHA("Redefina sua senha", "Use o código abaixo para continuar a redefinição da sua senha."),
    EXCLUSAO_CONTA("Confirme a exclusão da conta", "Use o código abaixo apenas se deseja excluir sua conta do CampusLink.");

    private final String titulo;
    private final String descricao;

    // preparar finalidade do codigo este construtor guarda o titulo e a explicacao do email
    FinalidadeCodigoEmail(String titulo, String descricao) {
        this.titulo = titulo;
        this.descricao = descricao;
    }

    // consultar titulo do codigo este metodo retorna o titulo da finalidade escolhida
    public String getTitulo() { return titulo; }
    // consultar descricao do codigo este metodo retorna a explicacao da finalidade escolhida
    public String getDescricao() { return descricao; }
}
