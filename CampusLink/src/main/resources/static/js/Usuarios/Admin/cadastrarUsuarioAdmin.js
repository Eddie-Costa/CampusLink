// cadastro administrativo de usuario bloqueia envios repetidos e restaura o botao ao voltar
// preparar cadastro de usuario administrativo configura a trava de envio e o retorno do navegador
document.addEventListener("DOMContentLoaded", function () {

    const formulario = document.querySelector(".cadastro-admin-form");

    // configurar cadastro de usuario encerra quando o formulario nao existe
    if (!formulario) {
        return;
    }

    const botaoCadastrar = formulario.querySelector('button[type="submit"]');

    // configurar botao de usuario encerra quando o botao de cadastro nao existe
    if (!botaoCadastrar) {
        return;
    }

    const textoOriginalBotao = botaoCadastrar.textContent;
    const botaoDesabilitadoInicialmente = botaoCadastrar.disabled;

    let enviando = false;

    // cadastrar usuario administrativo valida o formulario e impede envios repetidos
    formulario.addEventListener("submit", function (evento) {


        // impedir usuario duplicado cancela o envio enquanto o cadastro esta em andamento
        if (enviando) {
            evento.preventDefault();
            return;
        }

        // respeitar validacao do usuario encerra quando outra validacao cancelou o envio
        if (evento.defaultPrevented) {
            return;
        }

        // validar formulario antes do envio cancela a solicitacao e mostra os campos invalidos
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


    // restaurar cadastro de usuario recupera o texto e o estado inicial do botao
    window.addEventListener("pageshow", function () {
        enviando = false;
        botaoCadastrar.disabled = botaoDesabilitadoInicialmente;
        botaoCadastrar.textContent = textoOriginalBotao;
        formulario.removeAttribute("aria-busy");
    });
});