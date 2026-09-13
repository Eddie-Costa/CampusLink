package com.example.CampusLink.service;

import com.example.CampusLink.dao.conteudoDAO;
import com.example.CampusLink.dto.ConteudoDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.sql.SQLException;
import java.util.List;

@Service
public class ConteudoService {

    private final conteudoDAO conteudoDAO;
    private final ArquivoService arquivoService;

    public ConteudoService(
            com.example.CampusLink.dao.conteudoDAO conteudoDAO,
            ArquivoService arquivoService) {

        this.conteudoDAO = conteudoDAO;
        this.arquivoService = arquivoService;
    }
    public ConteudoDTO criar(ConteudoDTO conteudo, MultipartFile arquivo) {

        if (conteudo.getPrioridade() == null || conteudo.getPrioridade().isBlank()) {
            conteudo.setPrioridade("media");
        }
        conteudo.setTipo(definirTipo(conteudo, arquivo));
        try {
            conteudoDAO.inserir(conteudo);
        } catch (SQLException e) {
            throw new RuntimeException("Não foi possível cadastrar o conteúdo.", e);
        }
        if (arquivo != null && !arquivo.isEmpty()) {
            try {
                arquivoService.salvar(arquivo, conteudo.getId());
            } catch (RuntimeException e) {
                // Conteúdo já foi criado, mas o arquivo falhou.
                // Não vamos deixar um conteúdo "quebrado" sem o arquivo.
                try {
                    conteudoDAO.excluir(conteudo.getId());
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }

        return conteudo;
    }

    public List<ConteudoDTO> listarPorTurma(Long idTurma) {

        try {
            return conteudoDAO.listarPorTurma(idTurma);
        } catch (SQLException e) {
            throw new RuntimeException("Não foi possível listar os conteúdos da turma.", e);
        }
    }
    public void excluir(Long id) {

        try {
            conteudoDAO.excluir(id);
        } catch (SQLException e) {
            throw new RuntimeException("Não foi possível excluir o conteúdo.", e);
        }
    }
    public ConteudoDTO atualizar(ConteudoDTO conteudo) {

        conteudo.setTipo(definirTipo(conteudo, null));

        try {
            conteudoDAO.atualizar(conteudo);
        } catch (SQLException e) {
            throw new RuntimeException("Não foi possível atualizar o conteúdo.", e);
        }

        return conteudo;
    }

    private String definirTipo(ConteudoDTO conteudo, MultipartFile arquivo) {

        if (arquivo != null && !arquivo.isEmpty()) {
            return "arquivo";
        }

        if (conteudo.getUrl() != null && !conteudo.getUrl().isBlank()) {
            return "link";
        }

        return "texto";
    }
}