package com.example.CampusLink.service;

import com.example.CampusLink.repository.DominioRepository;
import com.example.CampusLink.dto.DominioDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class DominioService {

    private static final Logger logger = LoggerFactory.getLogger(DominioService.class);

    // exemplo.com, aula.exemplo.com.br (precisa de ao menos um ponto e terminar em letras)
    // isso também rejeita IPs (192.168.0.1) e "localhost"
    private static final Pattern DOMINIO_VALIDO = Pattern.compile(
            "^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$"
    );

    @Autowired
    private DominioRepository dominioRepository;

    // ---------- professor ----------

    public void solicitar(DominioDTO dominioDTO, Long idProfessor) {

        String dominio = normalizar(dominioDTO.getDominio());

        String justificativa = dominioDTO.getJustificativa();
        justificativa = (justificativa == null || justificativa.isBlank()) ? null : justificativa.trim();

        try {
            if (dominioRepository.existePendenteOuAprovado(dominio)) {
                throw new IllegalArgumentException(
                        "O domínio " + dominio + " já está autorizado ou já possui uma solicitação em análise.");
            }

            dominioDTO.setDominio(dominio);
            dominioDTO.setJustificativa(justificativa);
            dominioDTO.setIdProfessor(idProfessor);

            dominioRepository.inserir(dominioDTO);

        } catch (SQLException e) {
            // 23505 = violação do índice único (duas solicitações iguais ao mesmo tempo)
            if ("23505".equals(e.getSQLState())) {
                throw new IllegalArgumentException(
                        "O domínio " + dominio + " já está autorizado ou já possui uma solicitação em análise.");
            }
            logger.error("Erro ao solicitar domínio. professorId={}", idProfessor, e);
            throw new RuntimeException("Não foi possível registrar a solicitação.", e);
        }
    }

    public List<DominioDTO> listarPorProfessor(Long idProfessor) {
        try {
            return dominioRepository.listarPorProfessor(idProfessor);
        } catch (SQLException e) {
            logger.error("Erro ao listar solicitações do professor. professorId={}", idProfessor, e);
            throw new RuntimeException("Não foi possível listar as solicitações.", e);
        }
    }

    // ---------- admin ----------

    public List<DominioDTO> listarPendentes() {
        try {
            return dominioRepository.listarPendentes();
        } catch (SQLException e) {
            logger.error("Erro ao listar solicitações pendentes.", e);
            throw new RuntimeException("Não foi possível listar as solicitações pendentes.", e);
        }
    }

    public List<DominioDTO> listarAprovados() {
        try {
            return dominioRepository.listarAprovados();
        } catch (SQLException e) {
            logger.error("Erro ao listar domínios autorizados.", e);
            throw new RuntimeException("Não foi possível listar os domínios autorizados.", e);
        }
    }

    public void analisar(Long id, boolean aprovar, String comentarioAdmin) {

        String comentario = (comentarioAdmin == null || comentarioAdmin.isBlank()) ? null : comentarioAdmin.trim();

        if (comentario != null && comentario.length() > 500) {
            throw new IllegalArgumentException("O comentário deve ter no máximo 500 caracteres.");
        }

        if (!aprovar && comentario == null) {
            throw new IllegalArgumentException("Informe o motivo da rejeição.");
        }

        try {
            boolean analisado = dominioRepository.analisar(id, aprovar ? "aprovado" : "rejeitado", comentario);

            if (!analisado) {
                throw new IllegalArgumentException("Essa solicitação não existe ou já foi analisada.");
            }

        } catch (SQLException e) {
            // aprovar um domínio que já foi aprovado em outra solicitação
            if ("23505".equals(e.getSQLState())) {
                throw new IllegalArgumentException("Esse domínio já está autorizado por outra solicitação.");
            }
            logger.error("Erro ao analisar solicitação de domínio. id={}", id, e);
            throw new RuntimeException("Não foi possível analisar a solicitação.", e);
        }
    }

    // ---------- usado pela validação de URL ----------

    public boolean hostAutorizado(String host) {
        try {
            return dominioRepository.hostAutorizado(host);
        } catch (SQLException e) {
            logger.error("Erro ao verificar domínio autorizado. host={}", host, e);
            throw new RuntimeException("Não foi possível verificar o domínio.", e);
        }
    }

    // ---------- util ----------

    // aceita "https://www.Exemplo.com/pagina?x=1" e devolve "exemplo.com"
    public static String normalizar(String entrada) {

        if (entrada == null || entrada.isBlank()) {
            throw new IllegalArgumentException("Informe o domínio que deseja solicitar.");
        }

        String d = entrada.trim().toLowerCase();

        d = d.replaceFirst("^[a-z][a-z0-9+.-]*://", "");   // remove protocolo
        d = d.replaceAll("[/?#].*$", "");                  // remove caminho, query e fragmento

        if (d.contains("@")) {
            throw new IllegalArgumentException("Informe apenas o domínio, sem usuário ou senha.");
        }

        d = d.replaceFirst(":\\d+$", "");                  // remove porta
        d = d.replaceFirst("^www\\.", "");                 // www. é subdomínio comum
        d = d.replaceFirst("\\.$", "");                    // ponto final

        if (!DOMINIO_VALIDO.matcher(d).matches()) {
            throw new IllegalArgumentException(
                    "Domínio inválido. Use um formato como exemplo.com ou exemplo.com.br.");
        }

        return d;
    }
}
