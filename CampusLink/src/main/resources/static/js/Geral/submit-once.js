// controle de envio impede repeticoes nos formularios marcados e reforca o bloqueio do login
// desativar botoes do formulario percorre os controles que podem enviar os dados
function desativarBotoesEnvio(formulario) {
    formulario.querySelectorAll('button[type="submit"], input[type="submit"], button:not([type])')
        // marcar botao bloqueado aplica a aparencia desativada e impede novos cliques
        .forEach(function (botao) {
            botao.classList.add('submit-once-disabled');
            botao.disabled = true;
        });
}

// controlar envio unico marca o primeiro envio e cancela repeticoes nos formularios indicados
document.addEventListener('submit', function (event) {
    const formulario = event.target;
    // selecionar formulario com envio unico exige um formulario marcado para esse controle
    if (!(formulario instanceof HTMLFormElement) || !formulario.hasAttribute('data-submit-once')) {
        return;
    }

    // respeitar validacao do formulario ignora um envio que ja foi cancelado
    if (event.defaultPrevented) return;

    // bloquear formulario repetido impede um novo envio de formulario ja enviado
    if (formulario.dataset.enviado === 'true') {
        event.preventDefault();
        return;
    }

    formulario.dataset.enviado = 'true';
    desativarBotoesEnvio(formulario);
}, false);

// preparar trava do login configura os formularios de acesso ao carregar a pagina
document.addEventListener('DOMContentLoaded', function () {
    // acompanhar envio do login registra o bloqueio antes dos demais controles do formulario
    document.querySelectorAll('form.form-login[data-submit-once]').forEach(function (formulario) {
        // bloquear login repetido impede um segundo envio e desativa os botoes
        formulario.addEventListener('submit', function (event) {
            // bloquear login repetido impede uma nova tentativa enquanto o login foi enviado
            if (formulario.dataset.loginEnviado === 'true') {
                event.preventDefault();
                return;
            }

            formulario.dataset.loginEnviado = 'true';
            desativarBotoesEnvio(formulario);
        }, { capture: true });
    });
});
