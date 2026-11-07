// verificacao de acesso organiza os digitos do codigo e controla os prazos de validade e reenvio
// preparar verificacao de codigo configura digitacao colagem validade e espera para reenvio
document.addEventListener('DOMContentLoaded', () => {
    const form = document.querySelector('.form-verificacao');
    const codigoCompleto = document.getElementById('codigo');
    const campos = Array.from(document.querySelectorAll('.codigo-digit'));

    // iniciar verificacao do codigo exige formulario campo completo e campos dos digitos
    if (!form || !codigoCompleto || campos.length === 0) return;

    // juntar codigo de verificacao combina os digitos no campo usado pelo formulario
    const atualizarCodigo = () => {
        // ler digito do codigo retorna o valor atual de cada campo
        codigoCompleto.value = campos.map(campo => campo.value).join('');
    };

    // preencher varios digitos distribui apenas numeros entre os campos e ajusta o foco
    const preencherAPartirDe = (indice, valor) => {
        const digitos = valor.replace(/\D/g, '').slice(0, campos.length - indice);
        // distribuir digito colado coloca cada numero no campo correspondente
        digitos.split('').forEach((digito, deslocamento) => {
            campos[indice + deslocamento].value = digito;
        });
        atualizarCodigo();

        // localizar digito vazio procura o proximo campo sem valor a partir da posicao atual
        const proximoVazio = campos.findIndex((campo, posicao) => posicao >= indice && !campo.value);
        const foco = proximoVazio === -1 ? campos.at(-1) : campos[proximoVazio];
        foco.focus();
        // selecionar ultimo digito destaca o campo quando todos os digitos foram preenchidos
        if (proximoVazio === -1) foco.select();
    };

    // configurar campo do codigo acompanha digitacao teclas de navegacao e colagem
    campos.forEach((campo, indice) => {
        // digitar codigo limita o campo a numeros e avanca o foco quando preenchido
        campo.addEventListener('input', () => {
            const valor = campo.value;
            // distribuir codigo colado preenche varios campos quando a entrada possui mais de um digito
            if (valor.length > 1) {
                preencherAPartirDe(indice, valor);
                return;
            }

            campo.value = valor.replace(/\D/g, '').slice(-1);
            atualizarCodigo();
            // avancar digito do codigo move o foco ao proximo campo depois do preenchimento
            if (campo.value && campos[indice + 1]) campos[indice + 1].focus();
        });

        // navegar codigo usa retrocesso e setas para apagar ou mudar de campo
        campo.addEventListener('keydown', evento => {
            // apagar digito anterior retorna e limpa o campo anterior ao usar backspace em um campo vazio
            if (evento.key === 'Backspace' && !campo.value && campos[indice - 1]) {
                campos[indice - 1].focus();
                campos[indice - 1].value = '';
                atualizarCodigo();
            }
            // recuar no codigo usa a seta esquerda para acessar o campo anterior
            else if (evento.key === 'ArrowLeft' && campos[indice - 1]) {
                evento.preventDefault();
                campos[indice - 1].focus();
            }
            // avancar no codigo usa a seta direita para acessar o campo seguinte
            else if (evento.key === 'ArrowRight' && campos[indice + 1]) {
                evento.preventDefault();
                campos[indice + 1].focus();
            }
        });

        // colar codigo distribui o texto colado entre os campos numericos
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
    // atualizar prazos do codigo encerra quando a area dos contadores nao existe
    if (!contadores) return;

    const inicio = Date.now();
    const fimValidade = inicio + Number(contadores.dataset.validade) * 1000;
    const fimEspera = inicio + Number(contadores.dataset.reenvio) * 1000;
    let enviando = false;

    // formatar espera do codigo mostra minutos e segundos com dois digitos
    const formatarTempo = segundos => {
        const minutos = String(Math.floor(segundos / 60)).padStart(2, '0');
        return minutos + ':' + String(segundos % 60).padStart(2, '0');
    };

    // atualizar validade do codigo mostra os prazos e libera verificacao ou reenvio conforme o tempo
    const atualizarContadores = () => {
        const agora = Date.now();
        const validade = Math.max(0, Math.ceil((fimValidade - agora) / 1000));
        const espera = Math.max(0, Math.ceil((fimEspera - agora) / 1000));
        tempoValidade.textContent = formatarTempo(validade);
        barraValidade.value = validade;
        // mostrar prazo de reenvio atualiza o texto do botao quando nao ha envio em andamento
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
        // atualizar validade do codigo troca a mensagem apenas quando o estado mudou
        if (estadoCodigo.textContent !== mensagem) estadoCodigo.textContent = mensagem;
    };

    // enviar verificacao valida a disponibilidade da acao e impede envios repetidos
    form.addEventListener('submit', evento => {
        atualizarContadores();
        const solicitandoCodigo = evento.submitter === reenviar;
        // bloquear envio do codigo impede envios repetidos ou acoes ainda desabilitadas
        if (enviando || (solicitandoCodigo ? reenviar.disabled : verificar.disabled)) {
            evento.preventDefault();
            return;
        }
        enviando = true;
        // mostrar reenvio do codigo apresenta o estado de envio no botao correspondente
        if (solicitandoCodigo) reenviar.textContent = 'Enviando...';

        setTimeout(atualizarContadores, 0);
    });

    // recarregar verificacao ao voltar atualiza a pagina restaurada da memoria do navegador
    window.addEventListener('pageshow', evento => {
        // renovar pagina de verificacao recarrega a pagina recuperada do historico
        if (evento.persisted) window.location.reload();
    });
    document.addEventListener('visibilitychange', atualizarContadores);
    atualizarContadores();
    setInterval(atualizarContadores, 1000);
});
