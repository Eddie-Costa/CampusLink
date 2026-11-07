// termos de uso lgpd controla versoes com hash sha-256 aceite revogacao e publicacao de novas versoes
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

// controle dos termos de uso esta classe garante a integridade do texto pelo hash e registra aceites e revogacoes
@Service
public class TermosService {

    private static final Logger logger = LoggerFactory.getLogger(TermosService.class);
    private static final int LIMITE_CARACTERES = 100_000;

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private final TermosRepository termosRepository;

    // preparar termos de uso este construtor recebe a consulta das versoes e dos aceites
    public TermosService(TermosRepository termosRepository) {
        this.termosRepository = termosRepository;
    }

    // buscar termo vigente este metodo retorna a versao atual somente se o texto conferir com o hash sha-256 gravado
    public TermoVersaoDTO versaoAtual() {
        TermoVersaoDTO termo = termosRepository.buscarVersaoAtual();
        // exigir termo publicado recusa o uso quando nenhuma versao foi cadastrada
        if (termo == null) {
            throw new IllegalStateException("Nenhuma versão dos termos foi publicada.");
        }

        // verificar integridade do termo recalcula o hash sha-256 e recusa textos alterados fora do sistema
        if (!calcularHash(termo.conteudo()).equals(termo.hashSha256())) {
            logger.error("O texto dos termos não confere com o hash registrado. versao={}", termo.versao());
            // registro de erros confere se esta habilitado ao detectar termo adulterado
            if (logger.isErrorEnabled()) {
                // gravar historico do termo adulterado tenta salvar o registro no banco e informa falhas
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

    // conferir aceite vigente este metodo informa se o usuario aceitou a versao atual dos termos
    public boolean aceitouVersaoAtual(Long idUsuario) {
        return termosRepository.possuiAceite(idUsuario, versaoAtual());
    }

    // registrar aceite dos termos este metodo grava o aceite da versao atual com seu hash como prova lgpd
    public TermoVersaoDTO registrarAceite(Long idUsuario) {
        TermoVersaoDTO termo = versaoAtual();
        termosRepository.registrarAceite(idUsuario, termo);
        return termo;
    }

    // consultar aceite ativo este metodo retorna a versao e a data do aceite exibidos no perfil
    public TermoAceiteDTO aceiteAtivo(Long idUsuario) {
        return termosRepository.buscarAceiteAtivo(idUsuario);
    }

    // revogar aceite dos termos este metodo marca o aceite como revogado sem apagar o historico e registra no log
    public void revogarAceite(Long idUsuario) {
        int revogados = termosRepository.revogarAceites(idUsuario);

        logger.info("Aceite dos termos revogado pelo usuário. usuario={} registros={}", idUsuario, revogados);
        // registro de informacoes confere se esta habilitado ao revogar aceite
        if (logger.isInfoEnabled()) {
            // gravar historico da revogacao tenta salvar o registro no banco e informa falhas
            try {
                UsuarioRepository.InserirLogsNoBD(null, "INFO", TermosService.class.getName(), "revogarAceite", null,
                        MessageFormatter.arrayFormat("Aceite dos termos revogado pelo usuário. usuario={} registros={}", new Object[]{idUsuario, revogados}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
    }

    // listar versoes dos termos este metodo retorna o historico com hash e quantidade de aceites para o admin master
    public List<TermoVersaoResumoDTO> listarVersoes() {
        return termosRepository.listarVersoes();
    }

    // publicar nova versao dos termos este metodo valida numero e texto padroniza as quebras de linha e grava com hash sha-256
    public String publicarNovaVersao(String versao, String conteudo) {
        String versaoLimpa = versao == null ? "" : versao.strip();
        String texto = conteudo == null ? "" : conteudo.replace("\r\n", "\n").replace('\r', '\n').strip() + "\n";

        // validar numero da versao aceita apenas o formato numerico como 1.1 ou 2.0
        if (!versaoLimpa.matches("\\d{1,4}(\\.\\d{1,4}){0,3}")) {
            throw new IllegalArgumentException("Informe a versão no formato numérico, por exemplo 1.1 ou 2.0.");
        }
        // exigir texto do termo recusa versoes vazias
        if (texto.isBlank()) {
            throw new IllegalArgumentException("O texto dos termos não pode ficar vazio.");
        }
        // limitar tamanho do termo recusa textos acima do limite de caracteres
        if (texto.length() > LIMITE_CARACTERES) {
            throw new IllegalArgumentException("O texto dos termos deve ter no máximo " + LIMITE_CARACTERES + " caracteres.");
        }

        String hash = calcularHash(texto);
        // impedir versao repetida recusa numero de versao ou texto ja publicados
        if (termosRepository.existeVersaoOuConteudo(versaoLimpa, hash)) {
            throw new IllegalArgumentException("Já existe uma versão com esse número ou com exatamente esse texto.");
        }

        termosRepository.publicar(versaoLimpa, texto, hash);
        return hash;
    }

    // calcular hash sha-256 este metodo gera a impressao digital do texto usada para provar que o termo nao foi alterado
    public static String calcularHash(String texto) {
        // gerar hash sha-256 converte o texto em utf-8 e devolve os 64 caracteres hexadecimais
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }
}
