document.addEventListener("DOMContentLoaded", function () {

    const formulario = document.querySelector(".cadastro-admin-form");

    if (!formulario) {
        return;
    }

    const botaoCadastrar = formulario.querySelector('button[type="submit"]');

    if (!botaoCadastrar) {
        return;
    }

    const textoOriginalBotao = botaoCadastrar.textContent;
    const botaoDesabilitadoInicialmente = botaoCadastrar.disabled;

    let enviando = false;

    formulario.addEventListener("submit", function (evento) {

        // impede outro envio enquanto o cadastro esta carregando
        if (enviando) {
            evento.preventDefault();
            return;
        }

        if (evento.defaultPrevented) {
            return;
        }

        if (!formulario.checkValidity()) {
            evento.preventDefault();
            formulario.reportValidity();
            return;
        }

        enviando = true;
        botaoCadastrar.disabled = true;
        botaoCadastrar.textContent = "Cadastrando...";
        formulario.setAttribute("aria-busy", "true");
    });

    // restaura o botao ao voltar pelo navegador
    window.addEventListener("pageshow", function () {
        enviando = false;
        botaoCadastrar.disabled = botaoDesabilitadoInicialmente;
        botaoCadastrar.textContent = textoOriginalBotao;
        formulario.removeAttribute("aria-busy");
    });
});