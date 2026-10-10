document.addEventListener("DOMContentLoaded", function () {

    const campoBusca = document.getElementById("buscarTurma");
    const linhasTurmas = document.querySelectorAll(".turma-linha");
    const mensagemVazio = document.getElementById("nenhumaTurmaEncontrada");

    if (mensagemVazio) {
        mensagemVazio.style.display = "none";
    }

    if (!campoBusca) {
        return;
    }

    campoBusca.addEventListener("input", function () {

        const busca = campoBusca.value.toLowerCase().trim();
        let turmasVisiveis = 0;

        linhasTurmas.forEach(function (linha) {

            const nomeTurma = linha.getAttribute("data-nome").toLowerCase();
            const encontrou = nomeTurma.includes(busca);

            if (encontrou) {
                linha.style.display = "";
                turmasVisiveis++;
            } else {
                linha.style.display = "none";
            }
        });

        if (mensagemVazio) {
            mensagemVazio.style.display = turmasVisiveis === 0 ? "" : "none";
        }
    });
});