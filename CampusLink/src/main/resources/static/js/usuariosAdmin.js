document.addEventListener("DOMContentLoaded", function () {

    const campoBusca = document.getElementById("buscarUsuario");
    const linhasUsuarios = document.querySelectorAll(".usuario-linha");
    const mensagemVazio = document.getElementById("nenhumResultado");

    if (mensagemVazio) {
        mensagemVazio.style.display = "none";
    }

    if (!campoBusca) {
        return;
    }

    campoBusca.addEventListener("input", function () {

        const busca = campoBusca.value.toLowerCase().trim();
        let usuariosVisiveis = 0;

        linhasUsuarios.forEach(function (linha) {

            const nome = linha.getAttribute("data-nome").toLowerCase();
            const email = linha.getAttribute("data-email").toLowerCase();
            const identificador = linha.getAttribute("data-identificador").toLowerCase();

            const encontrou =
                nome.includes(busca) ||
                email.includes(busca) ||
                identificador.includes(busca);

            if (encontrou) {
                linha.style.display = "";
                usuariosVisiveis++;
            } else {
                linha.style.display = "none";
            }
        });

        if (mensagemVazio) {
            mensagemVazio.style.display = usuariosVisiveis === 0 ? "" : "none";
        }
    });
});