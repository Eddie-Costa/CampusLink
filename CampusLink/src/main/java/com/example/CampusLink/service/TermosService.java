package com.example.CampusLink.service;

import com.example.CampusLink.dto.TermoAceiteDTO;
import com.example.CampusLink.dto.TermoVersaoDTO;
import com.example.CampusLink.dto.TermoVersaoResumoDTO;
import com.example.CampusLink.repository.TermosRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class TermosService {

    private static final Logger logger = LoggerFactory.getLogger(TermosService.class);
    private static final int LIMITE_CARACTERES = 100_000;

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private final TermosRepository termosRepository;

    public TermosService(TermosRepository termosRepository) {
        this.termosRepository = termosRepository;
    }

    public TermoVersaoDTO versaoAtual() {
        TermoVersaoDTO termo = termosRepository.buscarVersaoAtual();
        if (termo == null) {
            throw new IllegalStateException("Nenhuma versão dos termos foi publicada.");
        }

        if (!calcularHash(termo.conteudo()).equals(termo.hashSha256())) {
            logger.error("O texto dos termos não confere com o hash registrado. versao={}", termo.versao());
            if (logger.isErrorEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", TermosService.class.getName(), "versaoAtual", null,
                            MessageFormatter.arrayFormat("O texto dos termos não confere com o hash registrado. versao={}", new Object[]{termo.versao()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new IllegalStateException("O texto dos termos não confere com o hash registrado.");
        }

        return termo;
    }

    public boolean aceitouVersaoAtual(Long idUsuario) {
        return termosRepository.possuiAceite(idUsuario, versaoAtual());
    }

    public TermoVersaoDTO registrarAceite(Long idUsuario) {
        TermoVersaoDTO termo = versaoAtual();
        termosRepository.registrarAceite(idUsuario, termo);
        return termo;
    }

    public TermoAceiteDTO aceiteAtivo(Long idUsuario) {
        return termosRepository.buscarAceiteAtivo(idUsuario);
    }

    public void revogarAceite(Long idUsuario) {
        int revogados = termosRepository.revogarAceites(idUsuario);

        logger.info("Aceite dos termos revogado pelo usuário. usuario={} registros={}", idUsuario, revogados);
        if (logger.isInfoEnabled()) {
            try {
                UsuarioRepository.InserirLogsNoBD(null, "INFO", TermosService.class.getName(), "revogarAceite", null,
                        MessageFormatter.arrayFormat("Aceite dos termos revogado pelo usuário. usuario={} registros={}", new Object[]{idUsuario, revogados}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
    }

    public List<TermoVersaoResumoDTO> listarVersoes() {
        return termosRepository.listarVersoes();
    }

    public String publicarNovaVersao(String versao, String conteudo) {
        String versaoLimpa = versao == null ? "" : versao.strip();
        String texto = conteudo == null ? "" : conteudo.replace("\r\n", "\n").replace('\r', '\n').strip() + "\n";

        if (!versaoLimpa.matches("\\d{1,4}(\\.\\d{1,4}){0,3}")) {
            throw new IllegalArgumentException("Informe a versão no formato numérico, por exemplo 1.1 ou 2.0.");
        }
        if (texto.isBlank()) {
            throw new IllegalArgumentException("O texto dos termos não pode ficar vazio.");
        }
        if (texto.length() > LIMITE_CARACTERES) {
            throw new IllegalArgumentException("O texto dos termos deve ter no máximo " + LIMITE_CARACTERES + " caracteres.");
        }

        String hash = calcularHash(texto);
        if (termosRepository.existeVersaoOuConteudo(versaoLimpa, hash)) {
            throw new IllegalArgumentException("Já existe uma versão com esse número ou com exatamente esse texto.");
        }

        termosRepository.publicar(versaoLimpa, texto, hash);
        return hash;
    }

    public static String calcularHash(String texto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }
}
