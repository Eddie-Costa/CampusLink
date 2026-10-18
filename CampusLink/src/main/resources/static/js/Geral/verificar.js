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

    const contadores = document.getElementById('contadores2fa');
    const tempoValidade = document.getElementById('tempoValidade');
    const barraValidade = document.getElementById('barraValidade');
    const verificar = document.getElementById('verificarCodigo');
    const reenviar = document.getElementById('reenviarCodigo');
    const estadoCodigo = document.getElementById('estadoCodigo');
    if (!contadores) return;

    const inicio = Date.now();
    const fimValidade = inicio + Number(contadores.dataset.validade) * 1000;
    const fimEspera = inicio + Number(contadores.dataset.reenvio) * 1000;
    let enviando = false;

    const formatarTempo = segundos => {
        const minutos = String(Math.floor(segundos / 60)).padStart(2, '0');
        return minutos + ':' + String(segundos % 60).padStart(2, '0');
    };

    const atualizarContadores = () => {
        const agora = Date.now();
        const validade = Math.max(0, Math.ceil((fimValidade - agora) / 1000));
        const espera = Math.max(0, Math.ceil((fimEspera - agora) / 1000));
        tempoValidade.textContent = formatarTempo(validade);
        barraValidade.value = validade;
        if (!enviando) {
            reenviar.textContent = espera > 0
                ? 'Solicitar novo código (' + formatarTempo(espera) + ')'
                : 'Solicitar novo código';
        }
        verificar.disabled = enviando || validade === 0;
        reenviar.disabled = enviando || espera > 0;
        contadores.classList.toggle('codigo-expirado', validade === 0);
        const mensagem = validade === 0
            ? 'Código expirado ou indisponível. Solicite um novo código.'
            : 'O código vale por 5 minutos após a emissão.';
        if (estadoCodigo.textContent !== mensagem) estadoCodigo.textContent = mensagem;
    };

    form.addEventListener('submit', evento => {
        atualizarContadores();
        const solicitandoCodigo = evento.submitter === reenviar;
        if (enviando || (solicitandoCodigo ? reenviar.disabled : verificar.disabled)) {
            evento.preventDefault();
            return;
        }
        enviando = true;
        if (solicitandoCodigo) reenviar.textContent = 'Enviando...';
        // aguarda o navegador iniciar o envio antes de desabilitar o botao
        setTimeout(atualizarContadores, 0);
    });

    window.addEventListener('pageshow', evento => {
        if (evento.persisted) window.location.reload();
    });
    document.addEventListener('visibilitychange', atualizarContadores);
    atualizarContadores();
    setInterval(atualizarContadores, 1000);
});
