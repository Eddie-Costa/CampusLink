
package com.example.CampusLink.service;

import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.repository.UsuarioRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ConteudoService {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(ConteudoService.class);

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

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
        conteudo.setDuracaoEstimadaMin(converterDuracaoEmMinutos(conteudo.getDuracaoEstimada()));

        try {
            conteudoRepository.inserir(conteudo);

        } catch (SQLException e) {
            logger.error("Erro ao cadastrar conteúdo. turmaId={} professorId={}", conteudo.getIdTurma(), conteudo.getIdProfessor(), e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "criar", null,
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
                            UsuarioRepository.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "criar", null,
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

                            UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "criar", null,
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
                UsuarioRepository.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "criar", null,
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

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "listarPorTurma", null,
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
        if (tipo != null && List.of("link", "video", "livro", "arquivo", "texto").contains(tipo.toLowerCase())) {
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

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "listarPorProfessor", null,
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
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "removerConteudoProfessor", null,
                                MessageFormatter.arrayFormat("Conteúdo removido pelo professor. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

            } else {
                logger.warn("Remoção de conteúdo recusada. conteudoId={} professorId={}", idConteudo, idProfessor);

                if (logger.isWarnEnabled()) {
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", ConteudoService.class.getName(), "removerConteudoProfessor", null,
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

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "removerConteudoProfessor", null,
                            MessageFormatter.arrayFormat("Erro ao remover conteúdo do professor. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível remover o conteúdo.", e);
        }
    }

    public boolean reabrir(Long idConteudo, Long idProfessor) {
        try {
            boolean reaberto = conteudoRepository.reabrir(idConteudo, idProfessor);

            if (reaberto) {
                logger.info("Conteúdo re-disponibilizado. conteudoId={} professorId={}", idConteudo, idProfessor);

                if (logger.isInfoEnabled()) {
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "reabrir", null,
                                MessageFormatter.arrayFormat("Conteúdo re-disponibilizado. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

            } else {
                logger.warn("Re-disponibilização recusada. conteudoId={} professorId={}", idConteudo, idProfessor);

                if (logger.isWarnEnabled()) {
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", ConteudoService.class.getName(), "reabrir", null,
                                MessageFormatter.arrayFormat("Re-disponibilização recusada. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            }

            return reaberto;

        } catch (SQLException e) {
            logger.error("Erro ao re-disponibilizar conteúdo. conteudoId={} professorId={}", idConteudo, idProfessor, e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "reabrir", null,
                            MessageFormatter.arrayFormat("Erro ao re-disponibilizar conteúdo. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível re-disponibilizar o conteúdo.", e);
        }
    }

    public boolean solicitarRevisao(Long idConteudo, Long idProfessor) {
        try {
            boolean solicitado = conteudoRepository.solicitarRevisao(idConteudo, idProfessor);

            if (solicitado) {
                logger.info("Revisão de conteúdo solicitada. conteudoId={} professorId={}", idConteudo, idProfessor);

                if (logger.isInfoEnabled()) {
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "solicitarRevisao", null,
                                MessageFormatter.arrayFormat("Revisão de conteúdo solicitada. conteudoId={} professorId={}", new Object[]{idConteudo, idProfessor}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

            } else {
                logger.warn("Solicitação de revisão recusada. conteudoId={} professorId={}", idConteudo, idProfessor);

                if (logger.isWarnEnabled()) {
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", ConteudoService.class.getName(), "solicitarRevisao", null,
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

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "solicitarRevisao", null,
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
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "excluir", null,
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

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "excluir", null,
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
        conteudo.setDuracaoEstimadaMin(converterDuracaoEmMinutos(conteudo.getDuracaoEstimada()));

        try {
            conteudoRepository.atualizar(conteudo);

            logger.info("Conteúdo atualizado. conteudoId={}", conteudo.getId());

            if (logger.isInfoEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "atualizar", null,
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

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "atualizar", null,
                            MessageFormatter.arrayFormat("Erro ao atualizar conteúdo. conteudoId={}", new Object[]{conteudo.getId()}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível atualizar o conteúdo.", e);
        }

        return conteudo;
    }

    public void alterarConclusao(Long idAluno, Long idTurma, Long idConteudo, boolean concluido) {
        try {
            if (!conteudoRepository.conteudoPertenceTurma(idConteudo, idTurma)) {
                logger.warn("Conclusão recusada: conteúdo fora da turma. alunoId={} turmaId={} conteudoId={}", idAluno, idTurma, idConteudo);

                if (logger.isWarnEnabled()) {
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", ConteudoService.class.getName(), "alterarConclusao", null,
                                MessageFormatter.arrayFormat("Conclusão recusada: conteúdo fora da turma. alunoId={} turmaId={} conteudoId={}", new Object[]{idAluno, idTurma, idConteudo}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

                throw new IllegalArgumentException("Conteúdo não encontrado nesta turma.");
            }

            LocalDateTime prazoEncerrado = concluido ? listarPrazosEncerrados(List.of(idConteudo)).get(idConteudo) : null;

            if (prazoEncerrado != null) {
                logger.warn("Conclusão recusada: prazo encerrado. alunoId={} conteudoId={} prazo={}", idAluno, idConteudo, prazoEncerrado);

                if (logger.isWarnEnabled()) {
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", ConteudoService.class.getName(), "alterarConclusao", null,
                                MessageFormatter.arrayFormat("Conclusão recusada: prazo encerrado. alunoId={} conteudoId={} prazo={}", new Object[]{idAluno, idConteudo, prazoEncerrado}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

                throw new IllegalArgumentException("O prazo deste conteúdo terminou em "
                        + prazoEncerrado.format(DateTimeFormatter.ofPattern("dd/MM"))
                        + ". Ele não pode mais ser concluído.");
            }

            if (concluido) {
                conteudoRepository.marcarConcluido(idAluno, idConteudo);
            } else {
                conteudoRepository.desmarcarConcluido(idAluno, idConteudo);
            }

            logger.info("Conclusão de conteúdo alterada. alunoId={} conteudoId={} concluido={}", idAluno, idConteudo, concluido);

            if (logger.isInfoEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "alterarConclusao", null,
                            MessageFormatter.arrayFormat("Conclusão de conteúdo alterada. alunoId={} conteudoId={} concluido={}", new Object[]{idAluno, idConteudo, concluido}).getMessage(), null, null);

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

        } catch (SQLException e) {
            logger.error("Erro ao alterar conclusão do conteúdo. alunoId={} conteudoId={}", idAluno, idConteudo, e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "alterarConclusao", null,
                            MessageFormatter.arrayFormat("Erro ao alterar conclusão do conteúdo. alunoId={} conteudoId={}", new Object[]{idAluno, idConteudo}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível atualizar o progresso do conteúdo.", e);
        }
    }

    public Set<Long> listarConcluidosDoAluno(Long idAluno, Long idTurma) {
        try {
            return conteudoRepository.listarConcluidosDoAluno(idAluno, idTurma);

        } catch (SQLException e) {
            logger.error("Erro ao listar conteúdos concluídos. alunoId={} turmaId={}", idAluno, idTurma, e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "listarConcluidosDoAluno", null,
                            MessageFormatter.arrayFormat("Erro ao listar conteúdos concluídos. alunoId={} turmaId={}", new Object[]{idAluno, idTurma}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível listar os conteúdos concluídos.", e);
        }
    }

    public Map<Long, LocalDateTime> listarPrazosEncerrados(List<Long> idsConteudos) {
        try {
            return conteudoRepository.listarPrazosEncerrados(idsConteudos, LocalDate.now(FUSO).atStartOfDay());

        } catch (SQLException e) {
            logger.error("Erro ao listar prazos encerrados. conteudos={}", idsConteudos, e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "listarPrazosEncerrados", null,
                            MessageFormatter.arrayFormat("Erro ao listar prazos encerrados. conteudos={}", new Object[]{idsConteudos}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível verificar os prazos dos conteúdos.", e);
        }
    }

    private Integer converterDuracaoEmMinutos(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }

        String valor = texto.trim().toLowerCase(Locale.ROOT);

        try {
            int minutos;

            if (valor.matches("\\d+")) {
                minutos = Integer.parseInt(valor);
            } else if (valor.matches("\\d+:\\d{2}:\\d{2}")) {
                String[] partes = valor.split(":");
                int segundos = Integer.parseInt(partes[0]) * 3600 + Integer.parseInt(partes[1]) * 60 + Integer.parseInt(partes[2]);
                minutos = (int) Math.ceil(segundos / 60.0);
            } else if (valor.matches("\\d+:\\d{2}")) {
                String[] partes = valor.split(":");
                int segundos = Integer.parseInt(partes[0]) * 60 + Integer.parseInt(partes[1]);
                minutos = (int) Math.ceil(segundos / 60.0);
            } else if (valor.matches("\\d+\\s*(h|hora|horas)")) {
                minutos = Integer.parseInt(valor.replaceAll("\\D", "")) * 60;
            } else if (valor.matches("\\d+\\s*(min|minuto|minutos)")) {
                minutos = Integer.parseInt(valor.replaceAll("\\D", ""));
            } else {
                return null;
            }

            return minutos > 0 ? minutos : null;

        } catch (NumberFormatException e) {
            return null;
        }
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
