function desativarBotoesEnvio(formulario) {
    formulario.querySelectorAll('button[type="submit"], input[type="submit"], button:not([type])')
        .forEach(function (botao) {
            botao.classList.add('submit-once-disabled');
            botao.disabled = true;
        });
}

document.addEventListener('submit', function (event) {
    const formulario = event.target;
    if (!(formulario instanceof HTMLFormElement) || !formulario.hasAttribute('data-submit-once')) {
        return;
    }

    if (event.defaultPrevented) return;

    if (formulario.dataset.enviado === 'true') {
        event.preventDefault();
        return;
    }

    formulario.dataset.enviado = 'true';
    desativarBotoesEnvio(formulario);
}, false);

document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('form.form-login[data-submit-once]').forEach(function (formulario) {
        formulario.addEventListener('submit', function (event) {
            if (formulario.dataset.loginEnviado === 'true') {
                event.preventDefault();
                return;
            }

            formulario.dataset.loginEnviado = 'true';
            desativarBotoesEnvio(formulario);
        }, { capture: true });
    });
});
