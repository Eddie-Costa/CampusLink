
package com.example.CampusLink.service;

import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.ConteudoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Service
public class ConteudoService {

    @Autowired
    private usuarioDAO usuarioDAO;

    private static final Logger logger = LoggerFactory.getLogger(ConteudoService.class);

    private final ConteudoRepository conteudoRepository;
    private final ArquivoService arquivoService;

    public ConteudoService(ConteudoRepository conteudoRepository, ArquivoService arquivoService) {
        this.conteudoRepository = conteudoRepository;
        this.arquivoService = arquivoService;
    }

    public ConteudoDTO criar(ConteudoDTO conteudo, MultipartFile arquivo) {

        if (conteudo.getPrioridade() == null || conteudo.getPrioridade().isBlank()) {
            conteudo.setPrioridade("media");
        }

        conteudo.setTipo(definirTipo(conteudo, arquivo));

        try {
            conteudoRepository.inserir(conteudo);

        } catch (SQLException e) {
            logger.error("Erro ao cadastrar conteúdo. turmaId={} professorId={}", conteudo.getIdTurma(), conteudo.getIdProfessor(), e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    usuarioDAO.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "criar", null,
                            MessageFormatter.arrayFormat("Erro ao cadastrar conteúdo. turmaId={} professorId={}", new Object[]{conteudo.getIdTurma(), conteudo.getIdProfessor()}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível cadastrar o conteúdo.", e);
        }

        if (arquivo != null && !arquivo.isEmpty()) {
            try {
                arquivoService.salvar(arquivo, conteudo.getId());

            } catch (RuntimeException e) {
                try {
                    conteudoRepository.excluir(conteudo.getId());

                    logger.info("Conteúdo removido após falha no arquivo. conteudoId={} turmaId={}", conteudo.getId(), conteudo.getIdTurma());

                    if (logger.isInfoEnabled()) {
                        try {
                            usuarioDAO.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "criar", null,
                                    MessageFormatter.arrayFormat("Conteúdo removido após falha no arquivo. conteudoId={} turmaId={}", new Object[]{conteudo.getId(), conteudo.getIdTurma()}).getMessage(), null, null);

                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }

                } catch (SQLException erroRemocao) {
                    logger.error("Erro ao remover conteúdo após falha no arquivo. conteudoId={} turmaId={}", conteudo.getId(), conteudo.getIdTurma(), erroRemocao);

                    if (logger.isErrorEnabled()) {
                        try {
                            StringWriter excecaoLogBD = new StringWriter();
                            erroRemocao.printStackTrace(new PrintWriter(excecaoLogBD));

                            usuarioDAO.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "criar", null,
                                    MessageFormatter.arrayFormat("Erro ao remover conteúdo após falha no arquivo. conteudoId={} turmaId={}", new Object[]{conteudo.getId(), conteudo.getIdTurma()}).getMessage(), null, excecaoLogBD.toString());

                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }
                }

                throw e;
            }
        }

        logger.info("Conteúdo cadastrado. conteudoId={} turmaId={} professorId={}", conteudo.getId(), conteudo.getIdTurma(), conteudo.getIdProfessor());

        if (logger.isInfoEnabled()) {
            try {
                usuarioDAO.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "criar", null,
                        MessageFormatter.arrayFormat("Conteúdo cadastrado. conteudoId={} turmaId={} professorId={}", new Object[]{conteudo.getId(), conteudo.getIdTurma(), conteudo.getIdProfessor()}).getMessage(), null, null);

            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        return conteudo;
    }

    public List<ConteudoDTO> listarPorTurma(Long idTurma) {
        try {
            return conteudoRepository.listarPorTurma(idTurma);

        } catch (SQLException e) {
            logger.error("Erro ao listar conteúdos. turmaId={}", idTurma, e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    usuarioDAO.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "listarPorTurma", null,
                            MessageFormatter.arrayFormat("Erro ao listar conteúdos. turmaId={}", new Object[]{idTurma}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível listar os conteúdos da turma.", e);
        }
    }
    public List<ConteudoDTO> buscarPorTurma(Long idTurma, String termo, String tipo) {

        String termoLimpo = (termo == null) ? null : termo.trim();

        if (termoLimpo != null && termoLimpo.length() > 100) {
            termoLimpo = termoLimpo.substring(0, 100);
        }

        String tipoLimpo = null;
        if (tipo != null && List.of("link", "arquivo", "texto").contains(tipo.toLowerCase())) {
            tipoLimpo = tipo.toLowerCase();
        }

        try {
            return conteudoRepository.buscarPorTurma(idTurma, termoLimpo, tipoLimpo);

        } catch (SQLException e) {
            logger.error("Erro ao buscar conteúdos. turmaId={}", idTurma, e);
            throw new RuntimeException("Não foi possível buscar os conteúdos da turma.", e);
        }
    }

    public List<ConteudoDTO> listarPorProfessor(Long idProfessor) {
        try {
            return conteudoRepository.listarPorProfessor(idProfessor);

        } catch (SQLException e) {
            logger.error("Erro ao listar conteúdos do professor. professorId={}", idProfessor, e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    usuarioDAO.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "listarPorProfessor", null,
                            MessageFormatter.arrayFormat("Erro ao listar conteúdos do professor. professorId={}", new Object[]{idProfessor}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível listar os conteúdos do professor.", e);
        }
    }

    public List<ConteudoDTO> listarPorProfessorPaginado(Long idProfessor, int pagina, String busca, String status) throws SQLException {
        return conteudoRepository.listarPorProfessorPaginado(idProfessor, pagina, busca, status);
    }

    public int contarFiltradosPorProfessor(Long idProfessor, String busca, String status) throws SQLException {
        return conteudoRepository.contarFiltradosPorProfessor(idProfessor, busca, status);
    }

    public Map<String, Integer> contarPorProfessor(Long idProfessor) throws SQLException {
        return conteudoRepository.contarPorProfessor(idProfessor);
    }

    // lista dez conteudos por pagina para o administrador
    public List<ConteudoDTO> listarTodosParaAdminPaginado(int pagina, String busca, String status) throws SQLException {
        return conteudoRepository.listarTodosParaAdminPaginado(pagina, busca, status);
    }

    // conta os conteudos encontrados para a paginacao do administrador
    public int contarFiltradosParaAdmin(String busca, String status) throws SQLException {
        return conteudoRepository.contarFiltradosParaAdmin(busca, status);
    }

    public List<ConteudoDTO> listarParaAnaliseAdmin() {
        try {
            return conteudoRepository.listarParaAnaliseAdmin();

        } catch (SQLException e) {
            throw new RuntimeException("Não foi possível listar os conteúdos enviados para análise.", e);
        }
    }

    public boolean removerConteudoProfessor(Long idConteudo, Long idProfessor) {
        try {
            boolean removido = conteudoRepository.removerConteudoProfessor(idConteudo, idProfessor);

            if (removido) {
                logger.info("Conteúdo removido pelo professor. conteudoId={} professorId={}", idConteudo, idProfessor);

                if (logger.isInfoEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "removerConteudoProfessor", null,
                                MessageFormatter.arrayFormat("Conteúdo removido pelo professor. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

            } else {
                logger.warn("Remoção de conteúdo recusada. conteudoId={} professorId={}", idConteudo, idProfessor);

                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", ConteudoService.class.getName(), "removerConteudoProfessor", null,
                                MessageFormatter.arrayFormat("Remoção de conteúdo recusada. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            }

            return removido;

        } catch (SQLException e) {
            logger.error("Erro ao remover conteúdo do professor. conteudoId={} professorId={}", idConteudo, idProfessor, e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    usuarioDAO.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "removerConteudoProfessor", null,
                            MessageFormatter.arrayFormat("Erro ao remover conteúdo do professor. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível remover o conteúdo.", e);
        }
    }

    public boolean solicitarRevisao(Long idConteudo, Long idProfessor) {
        try {
            boolean solicitado = conteudoRepository.solicitarRevisao(idConteudo, idProfessor);

            if (solicitado) {
                logger.info("Revisão de conteúdo solicitada. conteudoId={} professorId={}", idConteudo, idProfessor);

                if (logger.isInfoEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "solicitarRevisao", null,
                                MessageFormatter.arrayFormat("Revisão de conteúdo solicitada. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

            } else {
                logger.warn("Solicitação de revisão recusada. conteudoId={} professorId={}", idConteudo, idProfessor);

                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", ConteudoService.class.getName(), "solicitarRevisao", null,
                                MessageFormatter.arrayFormat("Solicitação de revisão recusada. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            }

            return solicitado;

        } catch (SQLException e) {
            logger.error("Erro ao solicitar revisão. conteudoId={} professorId={}", idConteudo, idProfessor, e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    usuarioDAO.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "solicitarRevisao", null,
                            MessageFormatter.arrayFormat("Erro ao solicitar revisão. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível solicitar a análise.", e);
        }
    }

    public void excluir(Long id) {
        try {
            conteudoRepository.excluir(id);

            logger.info("Conteúdo excluído. conteudoId={}", id);

            if (logger.isInfoEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "excluir", null,
                            MessageFormatter.arrayFormat("Conteúdo excluído. conteudoId={}", new Object[]{id}).getMessage(), null, null);

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

        } catch (SQLException e) {
            logger.error("Erro ao excluir conteúdo. conteudoId={}", id, e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    usuarioDAO.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "excluir", null,
                            MessageFormatter.arrayFormat("Erro ao excluir conteúdo. conteudoId={}", new Object[]{id}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível excluir o conteúdo.", e);
        }
    }

    public ConteudoDTO atualizar(ConteudoDTO conteudo) {
        conteudo.setTipo(definirTipo(conteudo, null));

        try {
            conteudoRepository.atualizar(conteudo);

            logger.info("Conteúdo atualizado. conteudoId={}", conteudo.getId());

            if (logger.isInfoEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "atualizar", null,
                            MessageFormatter.arrayFormat("Conteúdo atualizado. conteudoId={}", new Object[]{conteudo.getId()}).getMessage(), null, null);

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

        } catch (SQLException e) {
            logger.error("Erro ao atualizar conteúdo. conteudoId={}", conteudo.getId(), e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    usuarioDAO.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "atualizar", null,
                            MessageFormatter.arrayFormat("Erro ao atualizar conteúdo. conteudoId={}", new Object[]{conteudo.getId()}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

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
