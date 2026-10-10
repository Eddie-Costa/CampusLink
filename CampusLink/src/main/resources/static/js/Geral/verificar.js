document.addEventListener('DOMContentLoaded', () => {
    const form = document.querySelector('.form-verificacao');
    const codigoCompleto = document.getElementById('codigo');
    const campos = Array.from(document.querySelectorAll('.codigo-digit'));

    if (!form || !codigoCompleto || campos.length === 0) return;

    const atualizarCodigo = () => {
        codigoCompleto.value = campos.map(campo => campo.value).join('');
    };

    const preencherAPartirDe = (indice, valor) => {
        const digitos = valor.replace(/\D/g, '').slice(0, campos.length - indice);
        digitos.split('').forEach((digito, deslocamento) => {
            campos[indice + deslocamento].value = digito;
        });
        atualizarCodigo();

        const proximoVazio = campos.findIndex((campo, posicao) => posicao >= indice && !campo.value);
        const foco = proximoVazio === -1 ? campos.at(-1) : campos[proximoVazio];
        foco.focus();
        if (proximoVazio === -1) foco.select();
    };

    campos.forEach((campo, indice) => {
        campo.addEventListener('input', () => {
            const valor = campo.value;
            if (valor.length > 1) {
                preencherAPartirDe(indice, valor);
                return;
            }

            campo.value = valor.replace(/\D/g, '').slice(-1);
            atualizarCodigo();
            if (campo.value && campos[indice + 1]) campos[indice + 1].focus();
        });

        campo.addEventListener('keydown', evento => {
            if (evento.key === 'Backspace' && !campo.value && campos[indice - 1]) {
                campos[indice - 1].focus();
                campos[indice - 1].value = '';
                atualizarCodigo();
            } else if (evento.key === 'ArrowLeft' && campos[indice - 1]) {
                evento.preventDefault();
                campos[indice - 1].focus();
            } else if (evento.key === 'ArrowRight' && campos[indice + 1]) {
                evento.preventDefault();
                campos[indice + 1].focus();
            }
        });

        campo.addEventListener('paste', evento => {
            evento.preventDefault();
            preencherAPartirDe(indice, evento.clipboardData.getData('text'));
        });
    });

    form.addEventListener('submit', atualizarCodigo);
});
