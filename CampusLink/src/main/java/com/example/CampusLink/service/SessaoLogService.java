package com.example.CampusLink.service;

import com.example.CampusLink.dto.Admin.SessaoLogDTO;
import com.example.CampusLink.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;

// consulta o historico existente e protege o texto antes de envia-lo para a pagina
@Service
public class SessaoLogService {
    private final UsuarioRepository usuarios;
    private static final Pattern DADO_SENSIVEL = Pattern.compile(
            "senha|password|passwd|pwd|token|secret|segredo|c[oó]digo|code|otp|pin\\b|"
                    + "authorization|autentica[cç][aã]o|cookie|bearer|basic\\s|api[_ -]?key|"
                    + "chave|credencial|credential|private.key|"
                    + "eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+|://[^\\s/]+:[^\\s/]+@",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    // mesmo inicio de registro usado pelo string_agg do repositorio
    private static final String INICIO_REGISTRO = "(?m)(?=^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}[^\\r\\n]*? \\[(?:TRACE|DEBUG|INFO|WARN|ERROR)\\])";

    public SessaoLogService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    public Page<SessaoLogDTO> listar(int pagina, String busca, Long usuarioId, LocalDate dataInicial,
            LocalDate dataFinal, Integer duracaoMin, Integer duracaoMax) throws SQLException {
        if (usuarioId != null && usuarioId <= 0) throw new IllegalArgumentException("Informe um ID de usuário maior que zero.");
        if (dataInicial != null && dataFinal != null && dataInicial.isAfter(dataFinal)) {
            throw new IllegalArgumentException("A data inicial deve ser anterior ou igual à data final.");
        }
        if ((duracaoMin != null && duracaoMin < 0) || (duracaoMax != null && duracaoMax < 0)
                || (duracaoMin != null && duracaoMax != null && duracaoMin > duracaoMax)) {
            throw new IllegalArgumentException("Informe durações positivas, com a mínima menor ou igual à máxima.");
        }
        long total = usuarios.contarSessoesEncerradas(busca, usuarioId, dataInicial, dataFinal, duracaoMin, duracaoMax);
        int ultimaPagina = (int) Math.min(Integer.MAX_VALUE, Math.max(0, (total - 1) / 10));
        pagina = Math.min(Math.max(0, pagina), ultimaPagina);
        return new PageImpl<>(usuarios.listarSessoesEncerradas(pagina, busca, usuarioId, dataInicial, dataFinal,
                duracaoMin, duracaoMax), PageRequest.of(pagina, 10), total);
    }

    public SessaoLogDTO buscar(UUID id) throws SQLException {
        SessaoLogDTO sessao = usuarios.buscarSessaoEncerrada(id);
        if (sessao != null) sessao.setLogCompleto(ocultarDadosSensiveis(sessao.getLogCompleto()));
        return sessao;
    }

    // omitir a ocorrencia inteira protege tambem valores em JSON ou excecoes com varias linhas
    // a copia exibida e alterada; o texto original do banco permanece intacto
    String ocultarDadosSensiveis(String texto) {
        if (texto == null || texto.isBlank()) return "Nenhum log disponível nesta sessão.";
        StringBuilder seguro = new StringBuilder();
        for (String registro : texto.split(INICIO_REGISTRO)) {
            if (DADO_SENSIVEL.matcher(registro).find()) {
                seguro.append("[Registro ocultado por conter possível dado de autenticação.]\n");
            } else {
                seguro.append(registro);
            }
        }
        return seguro.toString();
    }
}
