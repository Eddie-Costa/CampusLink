
document.addEventListener("DOMContentLoaded", function () {

    const campoBusca = document.getElementById("buscarEvento");
    const filtroTipo = document.getElementById("filtrarTipoEvento");
    const filtroStatus = document.getElementById("filtrarStatusEvento");
    const linhasEventos = document.querySelectorAll(".evento-admin-linha");
    const mensagemVazio = document.getElementById("nenhumEventoEncontrado");

    if (!campoBusca || !filtroTipo || !filtroStatus) {
        return;
    }

    // filtra os eventos pelos campos selecionados
    function filtrarEventos() {

        const busca = campoBusca.value.toLowerCase().trim();
        const tipo = filtroTipo.value.toUpperCase();
        const status = filtroStatus.value.toUpperCase();

        let eventosVisiveis = 0;

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

            if (mostrarEvento) {
                eventosVisiveis++;
            }
        });

        // mostra a mensagem quando nenhum evento atende aos filtros
        if (mensagemVazio) {
            mensagemVazio.hidden = eventosVisiveis > 0 || linhasEventos.length === 0;
        }
    }

    campoBusca.addEventListener("input", filtrarEventos);
    filtroTipo.addEventListener("change", filtrarEventos);
    filtroStatus.addEventListener("change", filtrarEventos);

    filtrarEventos();
});
