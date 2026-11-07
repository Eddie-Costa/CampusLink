// lista administrativa de eventos filtra por nome turma tipo e situacao

// preparar filtro de eventos configura busca por nome tipo e situacao
document.addEventListener("DOMContentLoaded", function () {

    const campoBusca = document.getElementById("buscarEvento");
    const filtroTipo = document.getElementById("filtrarTipoEvento");
    const filtroStatus = document.getElementById("filtrarStatusEvento");
    const linhasEventos = document.querySelectorAll(".evento-admin-linha");
    const mensagemVazio = document.getElementById("nenhumEventoEncontrado");

    // iniciar filtro de eventos exige os campos de busca tipo e situacao
    if (!campoBusca || !filtroTipo || !filtroStatus) {
        return;
    }


    // filtrar eventos administrativos conta os resultados e controla a mensagem de lista vazia
    function filtrarEventos() {

        const busca = campoBusca.value.toLowerCase().trim();
        const tipo = filtroTipo.value.toUpperCase();
        const status = filtroStatus.value.toUpperCase();

        let eventosVisiveis = 0;

        // comparar evento administrativo combina nome ou turma com tipo e situacao selecionados
        linhasEventos.forEach(function (linha) {

            const nomeEvento = (linha.getAttribute("data-nome") || "").toLowerCase();
            const nomeTurma = (linha.getAttribute("data-turma") || "").toLowerCase();
            const tipoEvento = (linha.getAttribute("data-tipo") || "").toUpperCase();
            const statusEvento = (linha.getAttribute("data-status") || "").toUpperCase();

            const encontrouBusca = nomeEvento.includes(busca) || nomeTurma.includes(busca);
            const encontrouTipo = tipo === "" || tipoEvento === tipo;
            const encontrouStatus = status === "" || statusEvento === status;

            const mostrarEvento = encontrouBusca && encontrouTipo && encontrouStatus;

            linha.hidden = !mostrarEvento;

            // contar eventos visiveis soma os eventos que correspondem aos filtros
            if (mostrarEvento) {
                eventosVisiveis++;
            }
        });


        // mostrar busca de eventos vazia exibe o aviso quando o filtro oculta todos os registros existentes
        if (mensagemVazio) {
            mensagemVazio.hidden = eventosVisiveis > 0 || linhasEventos.length === 0;
        }
    }

    campoBusca.addEventListener("input", filtrarEventos);
    filtroTipo.addEventListener("change", filtrarEventos);
    filtroStatus.addEventListener("change", filtrarEventos);

    filtrarEventos();
});
