// regras de dominios controlam os enderecos aceitos nos materiais
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

// servico de sites permitidos esta classe normaliza pedidos e valida as decisoes de aprovacao ou recusa
@Service
public class DominioService {

    private static final Logger logger = LoggerFactory.getLogger(DominioService.class);



    private static final Pattern DOMINIO_VALIDO = Pattern.compile(
            "^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$"
    );

    @Autowired
    private DominioRepository dominioRepository;



    // solicitar dominio validado este metodo normaliza o endereco e impede pedidos pendentes ou aprovados repetidos
    public void solicitar(DominioDTO dominioDTO, Long idProfessor) {

        String dominio = normalizar(dominioDTO.getDominio());

        String justificativa = dominioDTO.getJustificativa();
        justificativa = (justificativa == null || justificativa.isBlank()) ? null : justificativa.trim();

        // solicitar dominio confere pedidos existentes e tenta cadastrar a solicitacao
        try {
            // impedir dominio repetido rejeita dominio autorizado ou com pedido pendente
            if (dominioRepository.existePendenteOuAprovado(dominio)) {
                throw new IllegalArgumentException(
                        "O site " + dominio + " já está liberado ou já tem um pedido em análise.");
            }

            dominioDTO.setDominio(dominio);
            dominioDTO.setJustificativa(justificativa);
            dominioDTO.setIdProfessor(idProfessor);

            dominioRepository.inserir(dominioDTO);

        } catch (SQLException e) {

            // tratar dominio duplicado informa quando o banco recusa uma solicitacao repetida
            if ("23505".equals(e.getSQLState())) {
                throw new IllegalArgumentException(
                        "O site " + dominio + " já está liberado ou já tem um pedido em análise.");
            }
            logger.error("Erro ao solicitar domínio. professorId={}", idProfessor, e);
            throw new RuntimeException("Não foi possível registrar a solicitação.", e);
        }
    }

    // consultar pedidos do professor este metodo lista suas solicitacoes de dominio e trata falhas
    public List<DominioDTO> listarPorProfessor(Long idProfessor) {
        // listar pedidos do professor consulta suas solicitacoes de dominio e trata falhas
        try {
            return dominioRepository.listarPorProfessor(idProfessor);
        } catch (SQLException e) {
            logger.error("Erro ao listar solicitações do professor. professorId={}", idProfessor, e);
            throw new RuntimeException("Não foi possível listar as solicitações.", e);
        }
    }



    // consultar pedidos pendentes de dominio este metodo recupera a fila de analise e trata falhas
    public List<DominioDTO> listarPendentes() {
        // listar dominios pendentes consulta as solicitacoes que aguardam analise
        try {
            return dominioRepository.listarPendentes();
        } catch (SQLException e) {
            logger.error("Erro ao listar solicitações pendentes.", e);
            throw new RuntimeException("Não foi possível listar as solicitações pendentes.", e);
        }
    }

    // consultar enderecos aprovados este metodo recupera os dominios permitidos e trata falhas
    public List<DominioDTO> listarAprovados() {
        // listar dominios aprovados consulta os dominios autorizados e trata falhas
        try {
            return dominioRepository.listarAprovados();
        } catch (SQLException e) {
            logger.error("Erro ao listar domínios autorizados.", e);
            throw new RuntimeException("Não foi possível listar os sites liberados.", e);
        }
    }

    // analisar pedido de dominio validado este metodo exige motivo de recusa e grava a decisao administrativa
    public void analisar(Long id, boolean aprovar, String comentarioAdmin) {

        String comentario = (comentarioAdmin == null || comentarioAdmin.isBlank()) ? null : comentarioAdmin.trim();

        // validar comentario do dominio limita a justificativa a quinhentos caracteres
        if (comentario != null && comentario.length() > 500) {
            throw new IllegalArgumentException("O comentário deve ter no máximo 500 caracteres.");
        }

        // justificar rejeicao do dominio exige um motivo para rejeitar a solicitacao
        if (!aprovar && comentario == null) {
            throw new IllegalArgumentException("Informe o motivo da rejeição.");
        }

        // analisar dominio tenta salvar a aprovacao ou rejeicao do pedido
        try {
            boolean analisado = dominioRepository.analisar(id, aprovar ? "aprovado" : "rejeitado", comentario);

            // conferir analise do dominio informa quando o pedido nao existe ou ja foi analisado
            if (!analisado) {
                throw new IllegalArgumentException("Essa solicitação não existe ou já foi analisada.");
            }

        } catch (SQLException e) {

            // tratar aprovacao duplicada informa que outra solicitacao ja autorizou o dominio
            if ("23505".equals(e.getSQLState())) {
                throw new IllegalArgumentException("Esse site já foi liberado em outra solicitação.");
            }
            logger.error("Erro ao analisar solicitação de domínio. id={}", id, e);
            throw new RuntimeException("Não foi possível analisar a solicitação.", e);
        }
    }



    // consultar permissao do endereco este metodo verifica o dominio autorizado e trata falhas do banco
    public boolean hostAutorizado(String host) {
        // verificar dominio autorizado consulta a permissao do endereco e trata falhas
        try {
            return dominioRepository.hostAutorizado(host);
        } catch (SQLException e) {
            logger.error("Erro ao verificar domínio autorizado. host={}", host, e);
            throw new RuntimeException("Não foi possível verificar o domínio.", e);
        }
    }




    // normalizar dominio solicitado este metodo extrai o nome do site e rejeita formatos invalidos
    public static String normalizar(String entrada) {

        // validar dominio solicitado exige um endereco preenchido
        if (entrada == null || entrada.isBlank()) {
            throw new IllegalArgumentException("Informe o link ou o endereço do site.");
        }

        String d = entrada.trim().toLowerCase();

        d = d.replaceFirst("^[a-z][a-z0-9+.-]*://", "");
        d = d.replaceAll("[/?#].*$", "");

        // validar endereco do dominio rejeita entradas com usuario ou senha
        if (d.contains("@")) {
            throw new IllegalArgumentException("Informe apenas o link do site, sem usuário ou senha.");
        }

        d = d.replaceFirst(":\\d+$", "");
        d = d.replaceFirst("^www\\.", "");
        d = d.replaceFirst("\\.$", "");

        // conferir formato do dominio rejeita enderecos fora do formato aceito
        if (!DOMINIO_VALIDO.matcher(d).matches()) {
            throw new IllegalArgumentException(
                    "Endereço inválido. Cole o link do site, por exemplo https://www.exemplo.com.br.");
        }

        return d;
    }
}
