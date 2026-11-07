// lista administrativa de usuarios pesquisa nome email ou identificador
// preparar busca administrativa de usuarios configura a pesquisa e oculta o aviso inicial
document.addEventListener("DOMContentLoaded", function () {

    const campoBusca = document.getElementById("buscarUsuario");
    const linhasUsuarios = document.querySelectorAll(".usuario-linha");
    const mensagemVazio = document.getElementById("nenhumResultado");

    // limpar aviso de usuarios oculta a mensagem de lista vazia ao iniciar
    if (mensagemVazio) {
        mensagemVazio.style.display = "none";
    }

    // iniciar busca administrativa de usuarios encerra quando o campo nao existe
    if (!campoBusca) {
        return;
    }

    // buscar usuario administrativo filtra as linhas e informa quando nao existem resultados
    campoBusca.addEventListener("input", function () {

        const busca = campoBusca.value.toLowerCase().trim();
        let usuariosVisiveis = 0;

        // comparar usuario administrativo procura o texto no nome no email ou no identificador
        linhasUsuarios.forEach(function (linha) {

            const nome = linha.getAttribute("data-nome").toLowerCase();
            const email = linha.getAttribute("data-email").toLowerCase();
            const identificador = linha.getAttribute("data-identificador").toLowerCase();

            const encontrou =
                nome.includes(busca) ||
                email.includes(busca) ||
                identificador.includes(busca);

            // filtrar linhas de usuarios mostra e conta os usuarios encontrados ou oculta os demais
            if (encontrou) {
                linha.style.display = "";
                usuariosVisiveis++;
            } else {
                linha.style.display = "none";
            }
        });

        // avisar usuario nao encontrado exibe a mensagem quando nenhum usuario corresponde
        if (mensagemVazio) {
            mensagemVazio.style.display = usuariosVisiveis === 0 ? "" : "none";
        }
    });
});